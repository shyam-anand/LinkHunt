name := """LinkHunt"""

version := "1.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  ws,
  "mysql" % "mysql-connector-java" % "5.1.6",
  "org.apache.httpcomponents" % "httpclient" % "4.4.1",
  "org.twitter4j" % "twitter4j-core" % "[4.0,)",
  "org.twitter4j" % "twitter4j-stream" % "[4.0,)",
  "org.webjars" %% "webjars-play" % "2.3.0-2",
  "org.webjars.bower" % "bootstrap-css-only" % "3.3.4"
)
