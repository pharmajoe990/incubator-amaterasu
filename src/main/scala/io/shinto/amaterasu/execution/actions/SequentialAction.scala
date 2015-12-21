package io.shinto.amaterasu.execution.actions

import java.util.concurrent.BlockingQueue

import com.fasterxml.jackson.annotation.JsonProperty
import io.shinto.amaterasu.enums.ActionStatus
import io.shinto.amaterasu.{ Config, Logging }
import io.shinto.amaterasu.dataObjects.ActionData

import org.apache.curator.framework.CuratorFramework
import org.apache.zookeeper.CreateMode

class SequentialAction(
    @JsonProperty("name") actionName: String,
    @JsonProperty("type") actionType: String,
    @JsonProperty("file") actionFile: String
) extends Action with Logging {

  val data: ActionData = ActionData(name = actionName, src = actionFile, actionType = actionType, null)

  var jobId: String = null
  private var jobsQueue: BlockingQueue[ActionData] = null

  private var client: CuratorFramework = null
  private var attempt: Int = 0

  def init(id: String, zkClient: CuratorFramework, queue: BlockingQueue[ActionData]): Unit = {

    jobsQueue = queue
    jobId = id

    // creating a znode for the action
    client = zkClient
    actionPath = client.create().withMode(CreateMode.PERSISTENT_SEQUENTIAL).forPath(s"/${jobId}/task-", ActionStatus.pending.toString.getBytes())
    actionId = actionPath.substring(actionPath.indexOf('-'))

  }

  def execute() = {

    try {

      announceQueued()
      jobsQueue.add(data)

    }
    catch {

      case e: Exception => handleFailure(attempt + 1, e)

    }

  }

  /**
    * The announceStart register the beginning of the of the task with ZooKeper
    */
  def announceStart(): Unit = {

    log.debug(s"Starting action ${data.name} of type ${data.actionType}")
    client.setData().forPath(actionPath, ActionStatus.started.toString.getBytes)

  }

  def announceQueued(): Unit = {

    log.debug(s"Action ${data.name} of type ${data.actionType} is queued for execution")
    client.setData().forPath(actionPath, ActionStatus.queued.toString.getBytes)

  }

  def announceComplete(): Unit = {

    log.debug(s"Action ${data.name} of type ${data.actionType} completed")
    client.setData().forPath(actionPath, ActionStatus.complete.toString.getBytes)
    //next.execute()

  }

  override def handleFailure(attemptNo: Int, e: Exception): Unit = {

    log.error(e.toString)
    log.debug(s"Part ${data.name} of type ${data.actionType} failed on attempt $attemptNo")
    attempt = attemptNo
    if (attempt <= 3) {

      //TODO: add retry policy
      execute()

    }
    else {

      announceFailure()

      //      if (error != null)
      //        error.execute()

    }

  }

}

//object SequentialAction {
//
//  def apply(data: ActionData, jobId: String, config: Config, queue: BlockingQueue[ActionData], client: CuratorFramework, next: Action, error: Action): SequentialAction = {
//
//    val action = new SequentialAction(actionName = data.name, actionFile = data.src, actionType = data.actionType)
//
//    action.jobId = jobId
//    action.config = config
//    action.jobsQueue = queue
//    action.client = client
//    action.next = next
//    action.error = error
//
//    action
//  }
//
//}