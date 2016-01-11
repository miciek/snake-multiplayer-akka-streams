package com.michalplachta.snake

import akka.actor.{Actor, ActorRef, Terminated}

class Broadcaster extends Actor {
  var subscribers = Set.empty[ActorRef]

  def receive: Receive = {
    case msg: GameEvent =>
      println("Received " + msg)
      subscribers.foreach(_ ! msg)
    case msg: FruitPosition =>
      println("Received " + msg)
      subscribers.foreach(_ ! msg)
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
}
