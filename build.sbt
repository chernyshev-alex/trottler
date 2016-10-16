
scalaVersion := "2.11.8"

val spray_ver = "1.3.3"

val akka_ver = "2.3.9"

libraryDependencies ++= Seq(
    "io.spray"            %%  "spray-can"     % spray_ver,
    "io.spray"            %%  "spray-json" 	  % "1.3.2",
    "io.spray"            %%  "spray-routing" % spray_ver,
    "io.spray"            %%  "spray-testkit" % spray_ver  	% "test",
    "com.typesafe.akka"   %%  "akka-actor"    % akka_ver,
    "com.typesafe.akka"   %%  "akka-testkit"  % akka_ver   	% "test",
  	"ch.qos.logback"  	  %  "logback-classic"   	% "1.1.7",   
    "org.specs2"          %%  "specs2-core"   % "2.3.11" 	% "test",
	"org.scalactic" 	  %% "scalactic" 	  % "2.2.6",
	"org.scalatest" 	  %% "scalatest" 	  % "2.2.6" 	% "test"
)
 
Revolver.settings
 
fork in run := false


