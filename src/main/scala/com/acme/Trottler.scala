package com.acme

import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import akka.actor.{Actor, ActorSystem, Props, ActorLogging, ReceiveTimeout}
import akka.util.Timeout
import spray.http.BasicHttpCredentials

// ===  trottling api ===========================

case class Sla(user : String, rps : Int)
trait SlaService {
  def getSlaByToken(token:String) : Future[Sla]
}

trait ThrottlingService {
  var graceRps   : Int     // configurable
  val slaService : SlaService     // use mocks/stubs for testing

  // Should return true if the request is within allowed RPS.
  def isRequestAllowed(token : Option[String]): Boolean
}

object MockedSlaService {
  val DELAY = 250    // external service  delay
  val RPS = 100      // default RPS with returns slow sla service for users
} 

class MockedSlaService extends SlaService {
  def getSlaByToken(token : String) = Future[Sla] {
      Thread.sleep(MockedSlaService.DELAY)
      Sla(BasicHttpCredentials(token).username, MockedSlaService.RPS)
  }
}

// ===== ThrottlingService impl =======================

trait CountDown {
  var duration : Duration     = 1 seconds
  var gElapsedTicks           = 1  
  def onTick = gElapsedTicks  = 1 + (gElapsedTicks % 100000)
}
  
object SlaEntry {
  def apply(sla : Sla, elapsedTicks : Int) = new SlaEntry(sla.rps, elapsedTicks)
}

case class SlaEntry(rps : Int, elapsedTicks : Int, fract : Int =1, counter : Int =1) {
  // auxiliary functions to recalculate state
  def effectiveRate(gElapsedTicks : Int) : Int = {
    println(rps, gElapsedTicks, elapsedTicks, fract, counter)
    (1 + counter) / (1 + Math.abs(gElapsedTicks - elapsedTicks))
  }  
  
  def updateRate(gElapsedTicks : Int, isEnabled : Boolean) : SlaEntry = {
    if (isEnabled) {
      SlaEntry(rps, gElapsedTicks, 1, counter + 1)
    } else {
      SlaEntry(rps, gElapsedTicks, fract + 10, counter)
    }
  }
}


class Throttler(sla : SlaService) extends ThrottlingService with CountDown {

  type UserType  = String

  val userCounters = new ConcurrentHashMap[UserType, SlaEntry] 

  var requestsNonAuthorized   = 0  // counter for unathorized users   

  var graceRps   : Int = 100           // configurable
  val slaService : SlaService = sla    // use mocks/stubs for testing
  
  def setGraceRps(rps : Int) = graceRps = rps
  
  // anonymous user, no token 
  def checkNonAuthorized(token : Option[String]) : Boolean = {
     requestsNonAuthorized += 1
     return requestsNonAuthorized / gElapsedTicks < graceRps 
  }
    
  // Should return true if the request is within allowed RPS else false
  override def isRequestAllowed(token : Option[String]) : Boolean = 
    if (token.isDefined) checkAuthorized(token) else checkNonAuthorized(token)
    
  def userFromToken(token : Option[String]) =     
    (token map (token => BasicHttpCredentials(token).username))
    
  /**
   * Check access for the authorized users
   * 
   * @return enable/disable access for user's rps settings
   */
  def checkAuthorized(token : Option[String]) : Boolean = {
    val user = userFromToken(token).get
    if (userCounters.containsKey(user)) {
         checkAuthorizedFromCache(user) 
    } else {
      // no SLA yet, getting this one and set up it into map
      // enabled for authorized, but aren't having SLA yet
      checkAuthorizedFromSla(user, token)
    }
  }
 
  /**
   * Requests SLA from external service and insert this one into the cache
   * @return true immediately
   */
  def checkAuthorizedFromSla(user : String, token : Option[String]) : Boolean = {
    slaService.getSlaByToken(token.get).onSuccess {
      // slow service has returned SLA, put it into the cache
      case sla => userCounters.put(user, SlaEntry(sla, gElapsedTicks))
    }
    true
  }
  
  /**
   * Requests SLA from cache and calc access grant
   * @return enabled or disabled
   */
  def checkAuthorizedFromCache(user : String) : Boolean = {
    // restore current sla state
    var usla = userCounters.get(user)
    val er = usla.effectiveRate(gElapsedTicks)
    if (er < usla.rps) {
      val enabled = er % usla.fract == 0  // enable access for all(1), 10% (10), 20% (20) 
      userCounters.put(user, usla.updateRate(gElapsedTicks, enabled))
      enabled
    } else {
      userCounters.put(user, usla.updateRate(gElapsedTicks, false))
      false  // disable access
    }
  }
}

// trottling actor ==========================

sealed trait TrottleBaseMsg
case class IsRequestAllowedMsg(token : Option[String]) extends TrottleBaseMsg

object TrottlerActor {
  def props(ss : SlaService) = Props(new TrottlerActor(new Throttler(ss)))
  def props(ts : Throttler)  = Props(new TrottlerActor(ts))
  def props                  = Props(new TrottlerActor(new Throttler(new MockedSlaService)))
  val name = "trottler"
}
 
class TrottlerActor(ts : Throttler) extends Actor with ActorLogging {
  
  context.setReceiveTimeout(ts.duration) 
 
  def receive = {
    case IsRequestAllowedMsg(token)  =>  sender ! ts.isRequestAllowed(token)
    case ReceiveTimeout =>  ts.onTick
    case _  => None
  }
  
}


