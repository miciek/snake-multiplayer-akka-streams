package com.michalplachta.snake

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializerSettings, Supervision, ActorMaterializer, OverflowStrategy}
import spray.json._
import spray.json.DefaultJsonProtocol._

object Main extends App {
  val decider: Supervision.Decider = ex => ex match {
    case _: Throwable => {
      ex.printStackTrace()
      Supervision.Resume
    }
  }

  implicit val system = ActorSystem("snake")
  val materializerSettings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
  implicit val materializer = ActorMaterializer(materializerSettings)

  case class Position(x: Int, y: Int)
  object Position {
    implicit val positionFormat = jsonFormat2(Position.apply)
  }
  case class GameEvent(playerName: String, positions: List[Position])
  object GameEvent {
    implicit val gameEventFormat = jsonFormat2(GameEvent.apply)
  }
  case class PlayerJoined(actor: ActorRef)

  val actor =
    system.actorOf(Props(new Actor {
      var subscribers = Set.empty[ActorRef]

      def receive: Receive = {
        case g: GameEvent =>
          println("Received " + g)
          subscribers.foreach(_ ! g)
        case PlayerJoined(actor) =>
          println("Joined " + actor)
          context.watch(actor)
          subscribers += actor
        case Terminated(actor) =>
          // TODO: check if this is enough (remember the supervision strategy settings)
          println("Terminated " + actor)
          subscribers = subscribers.filterNot(_ == actor)
        case msg =>
          println("UNKNOWN: " + msg)
      }
    }))

  val sink = Sink.actorRef(actor, "finished")

  val broadcastFlow: Flow[GameEvent, GameEvent, _] = {
    val in =
      Flow[GameEvent]
        .to(sink)

    val out =
      Source.actorRef[GameEvent](1, OverflowStrategy.dropHead)
        .mapMaterializedValue(actor ! PlayerJoined(_))

    Flow.fromSinkAndSource(in, out)
  }

  val flow: Flow[Message, Message, _] = {
    Flow[Message]
      .via(debug)
      .collect {
        case msg: TextMessage.Strict => msg.text
      }
      .map(_.parseJson.convertTo[GameEvent])
      .via(broadcastFlow)
      .map(_.toJson.toString)
      .map(TextMessage.Strict)
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
