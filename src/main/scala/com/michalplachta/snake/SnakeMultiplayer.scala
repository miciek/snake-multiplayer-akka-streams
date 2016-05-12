package com.michalplachta.snake

import akka.actor.{ActorSystem, Props}
import akka.event.Logging
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
  implicit val timeout = Timeout(5 seconds)
  val fruitMaker = system.actorOf(Props[FruitMaker], "fruitMaker")

  /**
    * Returns new fruit if players positions collide with the current one.
    * Returns unchanged current one otherwise.
    */
  def fruitPosition(playerPositions: List[PlayerPosition]): Future[FruitPosition] =
    fruitMaker
      .ask(WhereShouldTheFruitBe(playerPositions))
      .mapTo[FruitPosition]

  val fruitFlow: Flow[PlayerState, FruitPosition, _] =
                 Flow[PlayerState]
                   .map(_.positions)
                   .mapAsync(2)(fruitPosition)

  val scoreFlow: Flow[FruitPosition, Int, _] = {
    Flow[FruitPosition]
      .map {
        case _: NewFruitPosition => 1
        case _: CurrentFruitPosition => 0
      }
      .buffer(1, OverflowStrategy.backpressure) // scan will always be ahead by one initial element
      .scan(0)(_ + _)
  }

  /**
    *               +----------------+              +---------------+     +-------------------+
    *               |                |              |               |     |                   |
    *               |                +------------->+  Fruit Flow   +---->+  Broadcast Fruit  |
    * +------------>+   Broadcast    |              |               |     |     Position      |
    *               |   PlayerState  |              +---------------+     +---+----------+----+
    *               |                |                                        |          |
    *               |                +-------------+                          |          |
    *               +----------------+             |                          v          |
    *                                              |                      +---+--------+ |
    *                                              |                      |            | |
    *                                              |                      | Score Flow | |
    *                                              |                      |            | |
    *                                              |                      +---+--------+ |
    *                      +------------------+    |                          |          |
    *                      |                  +<---+                          |          |
    *                      |                  |                               |          |
    *                      |    Game Event    |<------------------------------+          |
    * <--------------------+        Zip       |                                          |
    *                      |                  +<-----------------------------------------+
    *                      |                  |
    *                      +------------------+
    */
  val gameLogicFlow: Flow[PlayerState, GameEvent, _] =
    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val fruitLogic = b.add(fruitFlow)
      val scoreLogic = b.add(scoreFlow)
      val broadcastPlayerState = b.add(Broadcast[PlayerState](2))
      val broadcastFruit = b.add(Broadcast[FruitPosition](2))
      val gameEventZip = b.add(ZipWith[PlayerState, Int, FruitPosition, GameEvent](GameEvent.apply))

      broadcastPlayerState.out(0) ~> fruitLogic ~> broadcastFruit.in
      broadcastPlayerState.out(1) ~> gameEventZip.in0
      broadcastFruit.out(0) ~> scoreLogic ~> gameEventZip.in1
      broadcastFruit.out(1) ~> gameEventZip.in2

      FlowShape(broadcastPlayerState.in, gameEventZip.out)
    })

  val gameEventBroadcastFlow: Flow[GameEvent, GameEvent, _] = {
    val broadcastActor = system.actorOf(Props[Broadcaster], "broadcaster")
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
      .log("incoming")(Logging(system, "MessageLogger"))
      .collect {
        case msg: TextMessage.Strict => msg.text
      }
      .map(_.parseJson.convertTo[PlayerState])
      .via(gameLogicFlow)
      .via(gameEventBroadcastFlow)
      .map(_.toJson.toString)
      .map(TextMessage.Strict)
  }
}
