package com.chilipiper.quartz

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all._
import doobie._
import doobie.implicits._
import munit.CatsEffectSuite
import org.postgresql.ds.PGSimpleDataSource
import org.quartz.{CronScheduleBuilder, JobKey, SimpleScheduleBuilder, Trigger, TriggerBuilder}
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

import java.time.Instant
import java.util.Date
import scala.concurrent.ExecutionContext

/** Regression tests for the trigger-corruption incident, where a trigger row marked `TRIGGER_TYPE = 'CRON'` whose
  * extended-properties row actually lived in `QRTZ_SIMPLE_TRIGGERS` (with none in `QRTZ_CRON_TRIGGERS`) crash-looped
  * the workload.
  *
  * These tests assert that stock Quartz (>= 2.5.3, which carries upstream #1496) both prevents this corruption and
  * tolerates a pre-existing corrupted row through the plain wrapper API — i.e. WITHOUT the former scala-quartz
  * workarounds (the `deleteJob` self-heal and the `scheduleJobsCustom` delete-before-insert), which this PR removes. If
  * any of these go red, the corresponding workaround is still needed and must be restored.
  *
  * They run against a real Postgres because the behaviour lives entirely in the SQL/JDBC layer.
  */
class SchedulerQuartzTriggerCorruptionTest extends CatsEffectSuite {

  // F-bounded self-typed Java class -> subclass to keep Scala 2/3 type inference happy.
  private final class PgContainer extends PostgreSQLContainer[PgContainer](DockerImageName.parse("postgres:16-alpine"))

  private val container = new PgContainer

  private var xa: Transactor.Aux[IO, PGSimpleDataSource] = _

  override def beforeAll(): Unit = {
    container.start()
    val ds = new PGSimpleDataSource
    ds.setUrl(container.getJdbcUrl)
    ds.setUser(container.getUsername)
    ds.setPassword(container.getPassword)
    xa = Transactor.fromDataSource[IO](ds, ExecutionContext.global)
  }

  override def afterAll(): Unit = container.stop()

  private def scheduler: Resource[IO, SchedulerCustom[String, IO]] =
    SchedulerQuartz.make[String, PGSimpleDataSource, IO](xa, dbInitScriptName = Some("tables_postgres.sql"))(_ =>
      IO.unit,
    )

  private val group = "TestGroup"
  private val farFuture = Instant.now.plusSeconds(86400).toEpochMilli

  private def countByJob(table: String, jobName: String): IO[Int] =
    (fr"select count(*) from" ++ Fragment.const(table) ++
      fr"where job_name = $jobName and job_group = $group").query[Int].unique.transact(xa)

  private def countChildByTrigger(table: String, triggerName: String): IO[Int] =
    (fr"select count(*) from" ++ Fragment.const(table) ++
      fr"where trigger_name = $triggerName and trigger_group = $group").query[Int].unique.transact(xa)

  /** Flips a trigger's discriminator to CRON without creating the matching QRTZ_CRON_TRIGGERS row, leaving the extended
    * props stranded in QRTZ_SIMPLE_TRIGGERS — exactly the production corruption.
    */
  private def corruptSimpleTriggerToCron(triggerName: String): IO[Unit] =
    sql"""update qrtz_triggers set trigger_type = 'CRON'
          where trigger_name = $triggerName and trigger_group = $group""".update.run.transact(xa).void

  /** Injects a second, corrupted trigger for an existing job, with a trigger key different from the job key. */
  private def injectCorruptTrigger(triggerName: String, jobName: String): IO[Unit] =
    (sql"""insert into qrtz_triggers
             (sched_name, trigger_name, trigger_group, job_name, job_group,
              trigger_state, trigger_type, start_time, next_fire_time, priority, misfire_instr)
           values ('SchedulerQuartz', $triggerName, $group, $jobName, $group,
              'WAITING', 'CRON', $farFuture, $farFuture, 5, 1)""".update.run *>
      sql"""insert into qrtz_simple_triggers
             (sched_name, trigger_name, trigger_group, repeat_count, repeat_interval, times_triggered)
           values ('SchedulerQuartz', $triggerName, $group, 0, 0, 0)""".update.run).transact(xa).void

  private def assertJobFullyPurged(jobName: String, triggerNames: List[String]): IO[Unit] =
    for {
      triggers <- countByJob("qrtz_triggers", jobName)
      jobs <- countByJob("qrtz_job_details", jobName)
      simple <- triggerNames.foldMapM(countChildByTrigger("qrtz_simple_triggers", _))
      cron <- triggerNames.foldMapM(countChildByTrigger("qrtz_cron_triggers", _))
    } yield {
      assertEquals(triggers, 0, "qrtz_triggers rows must be gone")
      assertEquals(jobs, 0, "qrtz_job_details rows must be gone")
      assertEquals(simple, 0, "qrtz_simple_triggers rows must be gone")
      assertEquals(cron, 0, "qrtz_cron_triggers rows must be gone")
    }

  test("deleteJob removes a trigger whose type disagrees with its extended-properties table (no self-heal needed)") {
    val jobName = "corrupt-simple"
    val jobKey = JobKey.jobKey(jobName, group)
    scheduler.use { sched =>
      for {
        _ <- sched.scheduleJobCustom(jobKey, "payload", Instant.now.plusSeconds(3600))
        _ <- corruptSimpleTriggerToCron(jobName)
        deleted <- sched.deleteJob(jobKey)
        _ <- IO(assert(deleted, "stock deleteJob should remove the job despite the corrupted trigger"))
        _ <- assertJobFullyPurged(jobName, List(jobName))
      } yield ()
    }
  }

  test("scheduleJobsCustom does not corrupt trigger rows when the schedule type changes (simple -> cron)") {
    val jobName = "typechange"
    val jobKey = JobKey.jobKey(jobName, group)
    val once: TriggerBuilder[Trigger] => TriggerBuilder[_ <: Trigger] =
      _.startAt(Date.from(Instant.now.plusSeconds(3600))).withSchedule(SimpleScheduleBuilder.simpleSchedule)
    val cron: TriggerBuilder[Trigger] => TriggerBuilder[_ <: Trigger] =
      _.withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(12, 0))
    scheduler.use { sched =>
      for {
        _ <- sched.scheduleJobsCustom(Map(jobKey -> (("payload", once))))
        _ <- sched.scheduleJobsCustom(Map(jobKey -> (("payload", cron))))
        simpleRows <- countChildByTrigger("qrtz_simple_triggers", jobName)
        cronRows <- countChildByTrigger("qrtz_cron_triggers", jobName)
        _ <- IO(assertEquals(simpleRows, 0, "stale simple_triggers row must not survive the type change"))
        _ <- IO(assertEquals(cronRows, 1, "cron_triggers row must exist after the type change"))
        deleted <- sched.deleteJob(jobKey)
        _ <- IO(assert(deleted, "a well-formed job should delete cleanly"))
        _ <- assertJobFullyPurged(jobName, List(jobName))
      } yield ()
    }
  }

  test("deleteJob removes a job whose corrupted trigger's key differs from the job key (no raw-SQL fallback needed)") {
    val jobName = "multi-trigger"
    val badTrigger = "multi-trigger-orphan"
    val jobKey = JobKey.jobKey(jobName, group)
    scheduler.use { sched =>
      for {
        _ <- sched.scheduleJobCustom(jobKey, "payload", Instant.now.plusSeconds(3600))
        _ <- injectCorruptTrigger(badTrigger, jobName)
        deleted <- sched.deleteJob(jobKey)
        _ <- IO(assert(deleted, "stock deleteJob should remove the job and all of its triggers"))
        _ <- assertJobFullyPurged(jobName, List(jobName, badTrigger))
      } yield ()
    }
  }
}
