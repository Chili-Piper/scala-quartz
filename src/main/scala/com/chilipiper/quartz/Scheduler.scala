package com.chilipiper.quartz

import org.quartz.impl.matchers.GroupMatcher
import org.quartz.{CronExpression, JobBuilder, JobDetail, JobKey, Trigger, TriggerBuilder}

import java.time.Instant

trait Scheduler[A, F[_]] {
  def scheduleJob(trigger: Trigger): F[Instant]
  def scheduleJob(jobDetail: JobDetail, trigger: Trigger): F[Instant]
  def scheduleJobs(jobsAndTriggers: Map[JobDetail, Set[Trigger]], replace: Boolean): F[Unit]
  def checkExists(jobKey: JobKey): F[Boolean]
  def deleteJob(jobKey: JobKey): F[Boolean]
  def addJob(jobDetail: JobDetail, replace: Boolean): F[Unit]
  def triggerJob(jobKey: JobKey): F[Unit]
  def getJobKeys(matcher: GroupMatcher[JobKey]): F[Set[JobKey]]
  def getJobDetail(jobKey: JobKey): F[JobDetail]
  def getTriggers(jobKey: JobKey): F[List[Trigger]]
}

trait SchedulerCustom[A, F[_]] extends Scheduler[A, F] {

  /** Use the method instead of manually calling `JobBuilder.newJob` to ensure `this` can work with the job correctly.
    */
  def newJobDetail(jobKey: JobKey, jobData: A, customize: JobBuilder => JobBuilder = identity): JobDetail
  def scheduleJobCustom(jobKey: JobKey, jobData: A, cronExpression: CronExpression): F[Unit]
  def scheduleJobCustom(jobKey: JobKey, jobData: A, instant: Instant): F[Unit]
  def scheduleJobCustom(
      jobKey: JobKey,
      jobData: A,
      configure: TriggerBuilder[Trigger] => TriggerBuilder[? <: Trigger],
      customizeJob: JobBuilder => JobBuilder = identity,
  ): F[Instant]

  def scheduleJobsCustom(
      jobsAndTriggers: Map[JobKey, (A, TriggerBuilder[Trigger] => TriggerBuilder[? <: Trigger])],
      customizeJob: (JobKey, A) => JobBuilder => JobBuilder = { (_, _) => identity },
  ): F[Unit]
}
