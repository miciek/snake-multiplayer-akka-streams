package com.michalplachta.snake

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import spray.json._

class SnakeMultiplayer(implicit system: ActorSystem) {
  val actor = system.actorOf(Props[Broadcaster])

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

  private def debug[T]: Flow[T, T, _] =
    Flow[T].map { in =>
      println("element: " + in)
      in
    }
}
