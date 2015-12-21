package io.shinto.amaterasu.mesos.schedulers

import java.util

import io.shinto.amaterasu
import io.shinto.amaterasu.utilities.FsUtil
import io.shinto.amaterasu.{ Config, Kami, Logging }
import org.apache.mesos.Protos.CommandInfo.URI
import org.apache.mesos.Protos._
import org.apache.mesos.{ Protos, Scheduler, SchedulerDriver }

import scala.collection.JavaConverters._

class ClusterScheduler extends Scheduler with Logging {

  private var kami: Kami = null
  private var config: Config = null

  private var driver: SchedulerDriver = null

  def error(driver: SchedulerDriver, message: String) {}

  def executorLost(driver: SchedulerDriver, executorId: ExecutorID, slaveId: SlaveID, status: Int) {}

  def slaveLost(driver: SchedulerDriver, slaveId: SlaveID) {}

  def disconnected(driver: SchedulerDriver) {}

  def frameworkMessage(driver: SchedulerDriver, executorId: ExecutorID, slaveId: SlaveID, data: Array[Byte]) {}

  def statusUpdate(driver: SchedulerDriver, status: TaskStatus) {

    println(s"received status update $status")

  }

  def offerRescinded(driver: SchedulerDriver, offerId: OfferID) = {}

  def validateOffer(offer: Offer): Boolean = {

    val resources = offer.getResourcesList.asScala

    resources.count(r => r.getName == "cpus" && r.getScalar.getValue >= config.Jobs.cpus) > 0 &&
      resources.count(r => r.getName == "mem" && r.getScalar.getValue >= config.Jobs.mem) > 0 &&
      resources.count(r => r.getName == "disk" && r.getScalar.getValue >= config.Jobs.repoSize) > 0
  }

  def createScalarResource(name: String, value: Double): Resource = {
    Resource.newBuilder
      .setName(name)
      .setType(Value.Type.SCALAR)
      .setScalar(Value.Scalar.newBuilder().setValue(value)).build()
  }

  def buildCommandInfo(jobSrc: String): CommandInfo = {

    val fsUtil = FsUtil(config)

    println(s"Starting amaterasu job: java -cp ${config.JobSchedulerJarName} io.shinto.amaterasu.mesos.executors.JobExecutor $jobSrc")

    CommandInfo.newBuilder
      .addUris(URI.newBuilder.setValue(fsUtil.getJarUrl()).setExecutable(true))
      //.addUris(URI.newBuilder.setValue(jobSrc)
      .setValue(s"java -cp ${config.JobSchedulerJar} io.shinto.amaterasu.mesos.executors.JobExecutor $jobSrc")
      .build()
  }

  def resourceOffers(driver: SchedulerDriver, offers: util.List[Offer]): Unit = {

    for (offer <- offers.asScala) {
      log.debug(s"offer received by Amaterasu Cluster Scheduler: $offer")

      if (validateOffer(offer)) {

        log.info(s"Accepting offer, id=${offer.getId}")
        // getting the next job to be executed
        val job = kami.getNextJob()

        val taskId = Protos.TaskID.newBuilder().setValue(job.id).build()
        log.debug(s"Preparing to launch job $taskId on slave ${offer.getSlaveId}")

        val task = TaskInfo.newBuilder
          .setName(s"Amaterasu-job-${taskId.getValue}")
          .setTaskId(taskId)
          .addResources(createScalarResource("cpus", config.Jobs.cpus))
          .addResources(createScalarResource("mem", config.Jobs.mem))
          .addResources(createScalarResource("disk", config.Jobs.repoSize))
          .setSlaveId(offer.getSlaveId)
          .setTaskId(taskId).setCommand(buildCommandInfo(job.src)).build()

        log.debug(s"Starting task Amaterasu-job-${taskId.getValue}")
        log.debug(s"With resources cpus=${config.Jobs.cpus}, mem=${config.Jobs.mem}, disk=${config.Jobs.repoSize}")

        driver.launchTasks(List(offer.getId).asJavaCollection, List(task).asJavaCollection)

      }
      else {

        log.info("Declining offer")
        driver.declineOffer(offer.getId)

      }
    }

  }

  def registered(driver: SchedulerDriver, frameworkId: FrameworkID, masterInfo: MasterInfo): Unit = {

    log.info("[registered] framework:" + frameworkId.getValue + " master:" + masterInfo)

    kami.frameworkId = frameworkId.getValue
    this.driver = driver

  }

  def reregistered(driver: SchedulerDriver, masterInfo: Protos.MasterInfo) {}
}

object ClusterScheduler {

  def apply(kami: Kami, config: amaterasu.Config): ClusterScheduler = {

    val scheduler = new ClusterScheduler()
    scheduler.kami = kami
    scheduler.config = config
    scheduler

  }

}