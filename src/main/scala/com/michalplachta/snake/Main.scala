package com.michalplachta.snake

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}

object Main extends App {
  implicit val system = ActorSystem("snake")
  implicit val materializer = ActorMaterializer()

  case class GameEvent(s: String)
  case class PlayerJoined(actor: ActorRef)
  case class PlayerLeft(name: String)

  val actor =
    system.actorOf(Props(new Actor {
      var subscribers = Set.empty[ActorRef]

      def receive: Receive = {
        case GameEvent(s) =>
          println("Received " + s)
          subscribers.foreach(_ ! s)
        case PlayerJoined(actor) =>
          println("Joined " + actor)
          context.watch(actor)
          subscribers += actor
        case Terminated(actor) =>
          println("Terminated " + actor)
          subscribers = subscribers.filterNot(_ == actor)
        case msg =>
          println("UNKNOWN: " + msg)
      }
    }))

  val sink = Sink.actorRef(actor, "finished")

  val broadcastFlow: Flow[String, String, _] = {
    val in = Flow[String]
      .map(GameEvent)
      .to(sink)

    val out =
      Source.actorRef[String](1, OverflowStrategy.fail)
        .mapMaterializedValue(actor ! PlayerJoined(_))

    Flow.fromSinkAndSource(in, out)
  }

  val flow: Flow[Message, Message, _] = {
    Flow[Message]
      .collect {
        case msg: TextMessage.Strict => msg.text
      }
      .via(broadcastFlow)
      .map {
        case msg: String =>
          TextMessage.Strict(msg)
      }
      .via(debug)
  }

  val route: Route =
    path("snakeSocket") {
      get {
        handleWebsocketMessages(flow)
      }
    }

  val serverBinding = Http().bindAndHandle(interface = "0.0.0.0", port = 8080, handler = route)

  private def debug[T]: Flow[T, T, _] =
    Flow[T].map { in =>
      println("element: " + in)
      in
    }
}
