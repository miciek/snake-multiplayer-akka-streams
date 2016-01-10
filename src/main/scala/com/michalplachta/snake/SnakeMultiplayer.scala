package com.michalplachta.snake

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.pattern.ask
import akka.stream.scaladsl._
import akka.stream.{FlowShape, OverflowStrategy}
import akka.util.Timeout
import com.michalplachta.snake.FruitMaker.WhereShouldTheFruitBe
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._

class SnakeMultiplayer(implicit system: ActorSystem) {
  val broadcastActor = system.actorOf(Props[Broadcaster])
  val broadcastSink = Sink.actorRef(broadcastActor, "finished")

  val fruitFlow: Flow[List[PlayerPosition], FruitPosition, _] = {
    val fruitMaker = system.actorOf(Props[FruitMaker])
    implicit val timeout = Timeout(5 seconds)

    def fruit(playerPositions: List[PlayerPosition]): Future[FruitPosition] =
      fruitMaker.ask(WhereShouldTheFruitBe(playerPositions)).mapTo[FruitPosition]

    Flow[List[PlayerPosition]].mapAsync(2)(fruit)
  }

  val gameLogicFlow: Flow[GameEvent, GameEvent, _] =
    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val broadcast = b.add(Broadcast[GameEvent](2))
      val fruit = b.add(fruitFlow)
      val sink = b.add(broadcastSink)

      broadcast.out(1).map(_.positions) ~> fruit ~> sink

      FlowShape(broadcast.in, broadcast.out(0))
    })

  val gameEventBroadcastFlow: Flow[GameEvent, GameEvent, _] = {
    val in =
      Flow[GameEvent]
        .to(broadcastSink)

    val out =
      Source.actorRef[GameEvent](1, OverflowStrategy.dropHead)
        .mapMaterializedValue(broadcastActor ! PlayerJoined(_))

    Flow.fromSinkAndSource(in, out)
  }

  val flow: Flow[Message, Message, _] = {
    Flow[Message]
      .via(debug)
      .collect {
        case msg: TextMessage.Strict => msg.text
      }
      .map(_.parseJson.convertTo[GameEvent])
      .via(gameLogicFlow)
      .via(gameEventBroadcastFlow)
      .map(_.toJson.toString)
      .map(TextMessage.Strict)
  }

  private def debug[T]: Flow[T, T, _] =
    Flow[T].map { in =>
      println("element: " + in)
      in
    }
}
