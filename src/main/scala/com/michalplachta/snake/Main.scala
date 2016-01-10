package com.michalplachta.snake

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}

import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

object Main extends App {
  implicit val system = ActorSystem()
  implicit val materializer = customMaterializer()

  val snakeMultiplayer = new SnakeMultiplayer

  val route: Route =
    path("snakeSocket") {
      get {
        handleWebsocketMessages(snakeMultiplayer.flow)
      }
    }

  val config = system.settings.config
  val interface = config.getString("app.interface")
  val port = config.getInt("app.port")

  val serverBinding = Http().bindAndHandle(interface = interface, port = port, handler = route)

  serverBinding.onComplete {
    case Success(binding) =>
      val localAddress = binding.localAddress
      println(s"Server is listening on ${localAddress.getHostName}:${localAddress.getPort}")
    case Failure(e) =>
      println(s"Binding failed with ${e.getMessage}")
      system.terminate()
  }

  def customMaterializer(): ActorMaterializer = {
    val decider: Supervision.Decider = ex => ex match {
      case _: Throwable => {
        ex.printStackTrace()
        Supervision.Resume
      }
    }

    val materializerSettings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
    ActorMaterializer(materializerSettings)
  }
}
