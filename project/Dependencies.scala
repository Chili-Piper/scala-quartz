import sbt._

object Dependencies {

  object Versions {
    // 2.5.3 carries upstream https://github.com/quartz-scheduler/quartz/pull/1496, which fixes
    // JDBC trigger corruption on a trigger-type change at the source. See scala-quartz #50.
    val quartz = "2.5.3"
    val doobie = "1.0.0-RC11"
    val circe = "0.14.15"
    val munit = "1.0.2"
    val munitCatsEffect = "2.0.0"
    val postgres = "42.7.4"
    val testcontainers = "1.20.4"
  }

  val quartz = "org.quartz-scheduler" % "quartz" % Versions.quartz
  val doobieCore = "org.tpolecat" %% "doobie-core" % Versions.doobie
  val circeJawn = "io.circe" %% "circe-jawn" % Versions.circe

  // Test-only
  val munit = "org.scalameta" %% "munit" % Versions.munit
  val munitCatsEffect = "org.typelevel" %% "munit-cats-effect" % Versions.munitCatsEffect
  val postgres = "org.postgresql" % "postgresql" % Versions.postgres
  val testcontainersPostgres = "org.testcontainers" % "postgresql" % Versions.testcontainers

}
