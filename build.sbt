name := "es-transport-client"

scalaVersion := "2.10.1"

libraryDependencies += "org.elasticsearch" % "elasticsearch" % "0.90.1"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.2.0-RC1"

libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % "2.2.0-RC1"

libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.5"

libraryDependencies += "ch.qos.logback" % "logback-core" % "1.0.13"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.0.13"

libraryDependencies += "io.netty" % "netty" % "3.6.6.Final"
