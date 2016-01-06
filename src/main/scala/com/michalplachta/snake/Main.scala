package com.michalplachta.snake

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow

object Main extends App {
  implicit val system = ActorSystem("snake")
  implicit val materializer = ActorMaterializer()

  val flow: Flow[Message, Message, _] = {
    Flow[Message].collect {
      case msg: TextMessage.Strict => msg
    }
  }

  val route: Route =
    path("snakeSocket") {
      get {
        handleWebsocketMessages(flow)
      }
    }

  val serverBinding = Http().bindAndHandle(interface = "0.0.0.0", port = 8080, handler = route)
}
