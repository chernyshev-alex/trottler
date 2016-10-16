package com.acme

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.scalatest._
import scala.concurrent.duration._
import akka.util.Timeout

class TrottlerSpec extends FlatSpec with Matchers with BeforeAndAfter  {
  
  var tm : Throttler = null
  
  before { 
    tm = new Throttler(new MockedSlaService())
  }

  it should "enable when < graceRps for not authorizated user" in {
     tm.setGraceRps(2)
     tm.isRequestAllowed(None) shouldBe(true)
  }

  it should "disable when > graceRps for not authorizated user" in {
     tm.setGraceRps(2)
     tm.isRequestAllowed(None) shouldBe(true) 
     tm.isRequestAllowed(None) shouldBe(false)
  }
  
  it should """user with token, but sla hasn't returned yet, treats as unauthorized 
      and user should be cached after sla service returns rps""" in {
     val token = Some("dXNlcjpwd2Q=")
     val user = tm.userFromToken(token).get
     tm.setGraceRps(5)
     tm.userCounters.containsKey(user) shouldBe(false)
     tm.isRequestAllowed(token) shouldBe(true)
     Thread.sleep(2 * MockedSlaService.DELAY)
     tm.userCounters.containsKey(user) shouldBe(true)
     
  }
  
}