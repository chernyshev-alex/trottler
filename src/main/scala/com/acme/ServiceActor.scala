package com.acme

import akka.actor.{Actor, ActorSystem, Props}
import akka.util.Timeout
import akka.pattern.ask
import spray.routing._
import spray.routing.authentication.{ UserPass, BasicAuth }
import scala.concurrent.Future
import scala.concurrent.duration._
import spray.http.{ HttpHeader, StatusCodes }
import spray.http.HttpHeaders._
import scala.concurrent.Await;
import Directives._
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Main HTTP server
 */

class ServiceActor extends Actor with HttpServiceApi {
  def actorRefFactory = context
  def receive = runRoute(mroute)
}

//  HTTP service interface
trait HttpServiceApi extends HttpService  { 

  val AUTHORIZATION_TAG = "Authorization"
  
  val trottlerActor = actorRefFactory.actorOf(TrottlerActor.props, TrottlerActor.name)

  val mroute = path("trottle") {
    get {
      optionalHeaderValueByName(AUTHORIZATION_TAG) { authTag => 
          authTag match {
            case Some(_) =>
              authenticate(BasicAuth(authenticator _, realm = "")) { userPass =>
                respondWithHeader(RawHeader(AUTHORIZATION_TAG, authTag.get)) { 
                  trottle(authTag) { isEnabled => 
                    complete(isEnabled.toString())
                  }
                }          
            }
            case None =>
                trottle(None) { isEnabled => 
                  complete(isEnabled.toString())
                }
        }
      }
    }
  }

  def authenticator(userPass : Option[UserPass]) = Future {
      if (userPass.exists(up => up.user == "user" && up.pass == "pwd")) userPass
      else None
    }

  /**
   * Custom trottle directive
   * 
   * @param authTag -  authentication tag {algo user:pass encoded in base64} e.g "Basic 12jhdh4h292="
   * @return true/false - enabled/disabled access 
   */
  def trottle(authTag : Option[String]) : Directive1[Boolean] = {
    provide(rateForUser(authTag))
  }

  implicit val timeout = Timeout(1 milliseconds)

  /**
   * Send request to the trottler actor and return answer
   * @return true/false - enabled/disabled access 
   */
  def rateForUser(authTag : Option[String] = None) : Boolean = {
    val token = authTag map (_.split(" ")(1))
    // TODO : bottleneck ! 
    val response = trottlerActor.ask(IsRequestAllowedMsg(token)).mapTo[Boolean]
    Await.result(response, timeout.duration)
  }

} 