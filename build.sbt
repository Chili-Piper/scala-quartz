import org.typelevel.scalacoptions._

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

lazy val commonSettings = commonSettingsMisc

lazy val commonSettingsMisc = List(
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
  sonatypeCredentialHost := "s01.oss.sonatype.org",
  versionScheme := Some("early-semver"),
  scalaVersion := "2.13.16",
  crossScalaVersions := List(scalaVersion.value, "3.3.6"),
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        List(
          "org.typelevel" % "kind-projector" % "0.13.3" cross CrossVersion.full,
          "com.olegpy" %% "better-monadic-for" % "0.3.1",
        )
      case _ => List()
    }
  },
  buildInfoKeys := List[BuildInfoKey](organization, moduleName, version),
  buildInfoOptions += BuildInfoOption.ToJson,
  buildInfoPackage := s"${organization.value}.${moduleName.value}".replace("-", "."),
  Compile / packageBin / packageOptions += Package.ManifestAttributes(
    "Automatic-Module-Name" -> s"${organization.value}.${moduleName.value}".replace("-", "."),
  ),
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
