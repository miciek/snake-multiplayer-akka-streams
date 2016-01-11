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
  val fruitFlow: Flow[List[PlayerPosition], CurrentFruitPosition, _] = {
    val fruitMaker = system.actorOf(Props[FruitMaker])
    implicit val timeout = Timeout(5 seconds)

    def fruit(playerPositions: List[PlayerPosition]): Future[FruitPosition] =
      fruitMaker.ask(WhereShouldTheFruitBe(playerPositions)).mapTo[FruitPosition]

    Flow[List[PlayerPosition]].mapAsync(2)(fruit).collect {
        case pos: CurrentFruitPosition =>
          pos
        case pos: NewFruitPosition =>
          CurrentFruitPosition.fromNew(pos)
      }
  }

  val gameLogicFlow: Flow[PlayerState, GameEvent, _] =
    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val broadcast = b.add(Broadcast[PlayerState](2))
      val fruit = b.add(fruitFlow)
      val zip = b.add(Zip[CurrentFruitPosition, PlayerState])
      val flow = b.add(Flow[(CurrentFruitPosition, PlayerState)].map {
        case (fruitPosition, playerState) => {
          GameEvent(playerState.playerName, playerState.positions, fruitPosition)
        }
      })

      broadcast.out(0).map(_.positions) ~> fruit ~> zip.in0
      broadcast.out(1) ~> zip.in1

      zip.out ~> flow

      FlowShape(broadcast.in, flow.out)
    })

  val gameEventBroadcastFlow: Flow[GameEvent, GameEvent, _] = {
    val broadcastActor = system.actorOf(Props[Broadcaster])
    val broadcastSink = Sink.actorRef(broadcastActor, "finished")

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
      .map(_.parseJson.convertTo[PlayerState])
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
