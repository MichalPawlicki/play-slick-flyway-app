name := """play-isolated-slick"""

version := "1.1-SNAPSHOT"

scalaVersion := "2.12.2"

lazy val flyway = (project in file("modules/flyway"))
  .enablePlugins(FlywayPlugin)

lazy val api = (project in file("modules/api"))
  .settings(Common.projectSettings)

lazy val slick = (project in file("modules/slick"))
  .settings(Common.projectSettings)
  .aggregate(api)
  .dependsOn(api)

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .aggregate(slick)
  .dependsOn(slick)

TwirlKeys.templateImports += "com.example.user.User"

// Automatic database migration available in testing
fork in Test := true

libraryDependencies ++= Seq(
  guice,
  "org.postgresql" % "postgresql" % "42.1.4",
  "org.webjars" %% "webjars-play" % "2.6.2",
  "org.webjars" % "jquery" % "3.2.1",
  "org.webjars" % "bootstrap" % "3.3.7",
  "com.adrianhurt" %% "play-bootstrap" % "1.2-P26-B3",
  "org.flywaydb" % "flyway-core" % "4.1.2" % Test,
  "com.typesafe.play" %% "play-ahc-ws" % "2.6.0-M4" % Test,
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.0.0-M3" % Test
)

// Silhouette
resolvers += "Atlassian Releases" at "https://maven.atlassian.com/public/"

libraryDependencies ++= Seq(
  "com.mohiva" %% "play-silhouette" % "5.0.0",
  "com.mohiva" %% "play-silhouette-password-bcrypt" % "5.0.0",
  "com.mohiva" %% "play-silhouette-crypto-jca" % "5.0.0",
  "com.mohiva" %% "play-silhouette-persistence" % "5.0.0",
  "com.mohiva" %% "play-silhouette-testkit" % "5.0.0" % "test"
)
