package com.michalplachta.snake

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.pattern.ask
import akka.stream._
import akka.stream.scaladsl._
import akka.util.Timeout
import com.michalplachta.snake.FruitMaker.WhereShouldTheFruitBe
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._

class SnakeMultiplayer(implicit system: ActorSystem) {
  val fruitFlow: Flow[List[PlayerPosition], FruitPosition, _] = {
    val fruitMaker = system.actorOf(Props[FruitMaker])
    implicit val timeout = Timeout(5 seconds)

    def fruit(playerPositions: List[PlayerPosition]): Future[FruitPosition] =
      fruitMaker.ask(WhereShouldTheFruitBe(playerPositions)).mapTo[FruitPosition]

    Flow[List[PlayerPosition]].mapAsync(2)(fruit)
  }

  val scoreFlow: Flow[Int, Int, _] = {
    Flow[Int]
      .buffer(1, OverflowStrategy.backpressure) // TODO: scan and broadcast don't seem to be working well together, that's why the buffer is here
      .scan(0)(_ + _)
  }

  val gameLogicFlow: Flow[PlayerState, GameEvent, _] =
    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val fruitLogic = b.add(fruitFlow)
      val scoreLogic = b.add(scoreFlow)
      val fruitToPoint = b.add(Flow[FruitPosition].map {
        case _: NewFruitPosition => 1
        case _: CurrentFruitPosition => 0
      })
      val gameEvent = b.add(Flow[((FruitPosition, Int), PlayerState)].map {
        case ((fruitPosition, score), playerState) => {
          GameEvent(playerState.playerName, playerState.positions, CurrentFruitPosition(fruitPosition.x, fruitPosition.y), score)
        }
      })

      val broadcastPlayerState = b.add(Broadcast[PlayerState](2))
      val broadcastFruit = b.add(Broadcast[FruitPosition](2))
      val zipFruitAndScore = b.add(Zip[FruitPosition, Int])
      val zip = b.add(Zip[(FruitPosition, Int), PlayerState])

      broadcastPlayerState.out(0).map(_.positions) ~> fruitLogic ~> broadcastFruit.in
      broadcastPlayerState.out(1) ~> zip.in1

      broadcastFruit.out(0) ~> zipFruitAndScore.in0
      broadcastFruit.out(1) ~> fruitToPoint ~> scoreLogic ~> zipFruitAndScore.in1

      zipFruitAndScore.out ~> zip.in0
      zip.out ~> gameEvent

      FlowShape(broadcastPlayerState.in, gameEvent.out)
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
