Trottler

	ServiceActor.scala  - spray http web service with the trottle directive
	
	Trottler.scala  - trottler api implementation

Tests

	test/TrottlerSpec.scala -trottler spec
	
	test/HttpApiSpec.scala - http web service spec

How to build

	sbt compile run

Tests

	sbt test


Goal

To provide Service Level Agreement (SLA) for our REST endpoint the

throttling service should be implemented in order to limit the requests per

second (RPS) for each user.

Assume you already have a service, which returns maximum allowed

RPS and username of the user by the token from the request

‘Authorization’ header

case class Sla(user:String, rps:Int)

trait SlaService {

def getSlaByToken(token:String):Future[Sla]

}

Service to implement

Service to implement

ThrottlingService might look like

trait ThrottlingService {

val graceRps:Int // configurable

val slaService: SlaService // use mocks/stubs for testing

// Should return true if the request is within allowed RPS.

def isRequestAllowed(token:Option[String]): Boolean

}

Rules for RPS counting

1. If no token provided, assume the client as unauthorized.

2. All unauthorized user's requests are limited by GraceRps

3. If request has a token, but slaService has not returned any info yet,

treat it as unauthorized user

4. RPS should be counted per user, as the same user might use

different tokens for authorization

5. SLA should be counted by intervals of 1/10 second (i.e. if RPS

limit is reached, after 1/10 second ThrottlingService should allow

10% more requests)

6. SLA information is changed quite rarely and SlaService is quite

costly to call (~250ms per request), so consider caching SLA

requests. Also, you should not query the service, if the same token

request is already in progress.

7. Consider that REST service average response time is bellow 5ms,

ThrottlingService shouldn’t impact REST service SLA

