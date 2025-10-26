import sbt._

object Dependencies {

  object Versions {
    val quartz = "2.5.1"
    val doobie = "1.0.0-RC9"
    val circe = "0.14.15"
  }

  val quartz = "org.quartz-scheduler" % "quartz" % Versions.quartz
  val doobieCore = "org.tpolecat" %% "doobie-core" % Versions.doobie
  val circeJawn = "io.circe" %% "circe-jawn" % Versions.circe

}
