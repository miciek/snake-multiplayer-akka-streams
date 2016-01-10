package com.michalplachta.snake

import akka.actor.ActorRef
import spray.json.DefaultJsonProtocol._

case class Position(x: Int, y: Int)

object Position {
  implicit val positionFormat = jsonFormat2(Position.apply)
}

case class GameEvent(playerName: String, positions: List[Position])

object GameEvent {
  implicit val gameEventFormat = jsonFormat2(GameEvent.apply)
}

case class PlayerJoined(actor: ActorRef)
