package com.michalplachta.snake

import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}

class Broadcaster extends Actor with ActorLogging {
  var players = Set.empty[ActorRef]

  private def broadcast(msg: Any): Unit = {
    log.debug("Broadcasting {} to {} players", msg, players.size)
    players.foreach(_ ! msg)
  }

  def receive: Receive = {
    case event: GameEvent =>
      broadcast(event)
    case fruitPos: FruitPosition =>
      broadcast(fruitPos)
    case PlayerJoined(player) =>
      log.debug("New player joined: {}", player)
      context.watch(player)
      players += player
    case Terminated(player) =>
      // TODO: check if this is enough (remember the supervision strategy settings)
      log.debug("Player actor terminated {}", player)
      players = players.filterNot(_ == player)
    case msg =>
      log.error("UNKNOWN message received: {}", msg)
  }
}
