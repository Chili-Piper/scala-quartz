# scala-quartz

A functional Scala library for job scheduling that wraps Quartz Scheduler with Cats Effect.

## Installation

Add to your `build.sbt`:

```scala
libraryDependencies ++= List(
  "com.chilipiper" %% "scala-quartz" % "<version>",
  "org.quartz-scheduler" % "quartz" % "<qartz-version>",
)
```
| `scala-quartz` | `quartz` |
| :---: | :---: |
| [![scala-quartz badge](https://maven-badges.sml.io/sonatype-central/com.chilipiper/scala-quartz_2.13/badge.svg)](https://central.sonatype.com/artifact/com.chilipiper/scala-quartz_2.13) | [![quartz badge](https://maven-badges.sml.io/sonatype-central/org.quartz-scheduler/quartz/badge.svg)](https://central.sonatype.com/artifact/org.quartz-scheduler/quartz) |
