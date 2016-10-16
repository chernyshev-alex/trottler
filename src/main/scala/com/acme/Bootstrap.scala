package com.acme

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

object Bootstrap extends App {

  implicit val timeout = Timeout(1.seconds)

  implicit val system = ActorSystem("system")

  val service = system.actorOf(Props[ServiceActor], "service")

  IO(Http) ? Http.Bind(service, interface = "127.0.0.1", port = 8080)
}