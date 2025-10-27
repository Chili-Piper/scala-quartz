import org.typelevel.scalacoptions.*
import sbt.Keys.versionScheme

val root = (project in file("."))
  .settings(commonSettings)
  .settings(
    name := "scala-quartz",
    libraryDependencies ++= List(
      Dependencies.quartz,
      Dependencies.doobieCore,
      Dependencies.circeJawn,
    ),
  )
  .enablePlugins(BuildInfoPlugin)

lazy val commonSettings = List(
  organization := "com.chilipiper",
  homepage := Some(url("https://github.com/Chili-Piper/scala-quartz/")),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  developers := List(
    Developer(
      id = "chilipiper",
      name = "Chili Piper",
      email = "",
      url = url("https://chilipiper.com"),
    ),
  ),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/Chili-Piper/scala-quartz"),
      "scm:git:https://github.com/Chili-Piper/scala-quartz.git",
      Some("scm:git:git@github.com:Chili-Piper/scala-quartz.git"),
    ),
  ),
  versionScheme := Some("early-semver"),
  scalaVersion := "2.13.16",
  crossScalaVersions := List(scalaVersion.value, "3.3.7"),
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        List(
          "org.typelevel" % "kind-projector" % "0.13.4" cross CrossVersion.full,
          "com.olegpy" %% "better-monadic-for" % "0.3.1",
        )
      case _ => List()
    }
  },
  buildInfoKeys := List[BuildInfoKey](organization, moduleName, version),
  buildInfoOptions += BuildInfoOption.ToJson,
  buildInfoPackage := s"${organization.value}.${moduleName.value}".replace("-", "."),
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        List(
          "-explaintypes", // Explain type errors in more detail.
          "-Vimplicits", // Enables the tek/splain features to make the compiler print implicit resolution chains when no implicit value can be found
          "-Vtype-diffs", // Enables the tek/splain features to turn type error messages (found: X, required: Y) into colored diffs between the two types
        )
      case _ =>
        List(
          "-explain",
        )
    }
  },
  tpolecatDevModeOptions ~= { opts =>
    opts.filterNot(Set(ScalacOptions.warnError))
  },
)

addCommandAlias(
  "ci",
  "; scalafmtCheckAll; scalafmtSbtCheck; +compile; +test",
)
