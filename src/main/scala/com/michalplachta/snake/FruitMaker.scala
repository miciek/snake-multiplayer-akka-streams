package com.michalplachta.snake

import akka.actor.Actor
import com.michalplachta.snake.FruitMaker.WhereShouldTheFruitBe

class FruitMaker extends Actor {
  val random = scala.util.Random
  var currentFruitPosition = CurrentFruitPosition.fromNew(newRandomFruitPosition)

  def receive: Receive = {
    // TODO: refactor this ugly piece of code :(
    case WhereShouldTheFruitBe(playerPositions) =>
      val maybeNewFruit: Option[FruitPosition] =
        playerPositions
          .filter(pos => pos.x == currentFruitPosition.x && pos.y == currentFruitPosition.y)
          .map(_ => newRandomFruitPosition)
          .headOption
      sender() ! maybeNewFruit.getOrElse(currentFruitPosition)
      maybeNewFruit.foreach { fruit =>
        currentFruitPosition = CurrentFruitPosition(fruit.x, fruit.y)
      }
  }

  def newRandomFruitPosition = NewFruitPosition(random.nextInt(20), random.nextInt(20))
}

object FruitMaker {
  case class WhereShouldTheFruitBe(playerPositions: List[PlayerPosition])
}
