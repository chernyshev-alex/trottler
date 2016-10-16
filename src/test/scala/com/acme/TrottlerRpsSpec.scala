package com.acme

import akka.actor.ActorSystem

import akka.actor.{Props, Actor, ActorRef}
import akka.testkit.{ TestActors, TestKit, ImplicitSender }
import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfter
import akka.actor.PoisonPill
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.Await;
import akka.testkit.TestProbe

class TrottlerRpsSpec extends TestKit(ActorSystem("system")) 
    with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfter {

  implicit val timeout = Timeout(1 milliseconds)

  val ts = new Throttler(new MockedSlaService)
  var trottlerActor : ActorRef = _
  
  override def afterAll = TestKit.shutdownActorSystem(system)
  val msg  = IsRequestAllowedMsg(Some("dXNlcjpwd2Q="))
  
  before {
    trottlerActor = system.actorOf(TrottlerActor.props(ts))
  }
  after {
    trottlerActor ! PoisonPill
  }

  "trottler" must {
    "enable access for unauthorized user when < graseRps" in {
      trottlerActor ! IsRequestAllowedMsg(None)
      expectMsg(true)
    }

    "disable access for unauthorized user when > graseRps" in {
      for (i <- (1 until ts.graceRps)) {
        trottlerActor ! IsRequestAllowedMsg(None)
        expectMsg(true)
      }
      trottlerActor ! IsRequestAllowedMsg(None)
      expectMsg(false)
    }
    "enable access for authorized user when < graseRps" in {
      within (1000 milliseconds) {
        for (i <-(1 to MockedSlaService.RPS)) {
          val f = trottlerActor.ask(msg).mapTo[Boolean]
          val enabled = Await.result(f, 1 milliseconds) 
          enabled should be (true)
          Thread.sleep(5)          
        }
      }
    }  
    "slow down rate for authorized user when > graseRps" in {
      var wasDisabled = false
      
      within (1000 milliseconds) {
        for (i <-(0 to 2 * MockedSlaService.RPS)) {
          val f = trottlerActor.ask(msg).mapTo[Boolean]
          val enabled = Await.result(f, 20 milliseconds) 
          if (!enabled) {
            wasDisabled = true
          }
          Thread.sleep(1)          
        }
        wasDisabled should be (true)

      }
    }  
    
  }
 }

