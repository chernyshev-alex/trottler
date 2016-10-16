package com.acme

import spray.http._
import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.routing.authentication._

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Future
import scala.concurrent.duration._

class HttpServiceApiSpec extends Specification with Specs2RouteTest with HttpServiceApi {

  def actorRefFactory = system
  
  val legalUser = BasicHttpCredentials("user", "pwd")
  val illegalUser = BasicHttpCredentials("unknown", "")
  
  "A Service" should {
    "for not unauthorized users grace rps" in {
     Get("/trottle") ~> mroute ~> check {
        status mustEqual StatusCodes.OK
        responseAs[String] === "10" 
     }
   }
  
   "for authorized users SLA" in {
    Get("/trottle") ~> addCredentials(legalUser) ~> mroute ~> check {
        status mustEqual StatusCodes.OK
        responseAs[String] === "1000"
      }
   } 
  }
}