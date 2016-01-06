name := "akka-http-multiplayer-snake"

organization := "miciek"

version := "1.0"

scalaVersion := "2.11.7"

libraryDependencies ++= {
  val akkaVersion = "2.4.1"
  val akkaStreamHttpVersion = "2.0.1"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream-experimental" % akkaStreamHttpVersion,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaStreamHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaStreamHttpVersion,
    "com.typesafe.akka" %% "akka-http-xml-experimental" % akkaStreamHttpVersion,
    "com.typesafe.akka" %% "akka-http-testkit-experimental" % akkaStreamHttpVersion % Test,
    "com.typesafe.akka" %% "akka-stream-testkit-experimental" % akkaStreamHttpVersion % Test,
    "org.scalatest" %% "scalatest" % "2.2.5" % Test,
    "junit" % "junit" % "4.10" % Test
  )
}

fork in run := true

