lazy val akkaHttpVersion = "10.1.11"
lazy val akkaVersion    = "2.6.1"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "com.svend.demo",
      scalaVersion    := "2.13.1"
    )),
    name := "streaming-http-demo",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http"                % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json"     % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-actor-typed"         % akkaVersion,
      "com.typesafe.akka" %% "akka-stream"              % akkaVersion,
      "com.typesafe.akka" %% "akka-stream-typed"              % akkaVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-sse" % "1.1.2",
      "com.typesafe.akka" %% "akka-stream-kafka" % "1.1.0",
      "ch.qos.logback"    % "logback-classic"           % "1.2.3",

      "com.typesafe.akka" %% "akka-http-testkit"        % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion     % Test,
      "org.scalatest"     %% "scalatest"                % "3.0.8"         % Test
    )
  )
