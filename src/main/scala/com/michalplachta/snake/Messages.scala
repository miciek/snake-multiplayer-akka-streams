package com.michalplachta.snake

import akka.actor.ActorRef
import spray.json.DefaultJsonProtocol._

sealed trait Position {
  val x: Int
  val y: Int
}

trait FruitPosition extends Position

case class CurrentFruitPosition(x: Int, y: Int) extends FruitPosition

object CurrentFruitPosition {
  implicit val jsonFormat = jsonFormat2(CurrentFruitPosition.apply)

  def fromNew(newFruitPosition: NewFruitPosition) = {
    CurrentFruitPosition(newFruitPosition.x, newFruitPosition.y)
  }
}

case class NewFruitPosition(x: Int, y: Int) extends FruitPosition

case class PlayerPosition(x: Int, y: Int) extends Position

object PlayerPosition {
  implicit val jsonFormat = jsonFormat2(PlayerPosition.apply)
}

case class GameEvent(playerName: String, positions: List[PlayerPosition])

object GameEvent {
  implicit val jsonFormat = jsonFormat2(GameEvent.apply)
}

case class PlayerJoined(actor: ActorRef)
