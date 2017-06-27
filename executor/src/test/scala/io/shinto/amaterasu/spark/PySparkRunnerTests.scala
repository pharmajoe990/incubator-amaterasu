package io.shinto.amaterasu.spark

import java.io.File

import io.shinto.amaterasu.executor.execution.actions.runners.spark.PySpark.PySparkRunner
import io.shinto.amaterasu.common.runtime.Environment
import io.shinto.amaterasu.utilities.TestNotifier
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.SparkSession
import org.apache.spark.SparkConf
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.collection.JavaConverters._

class PySparkRunnerTests extends FlatSpec with Matchers with BeforeAndAfterAll {

  Logger.getLogger("org").setLevel(Level.OFF)
  Logger.getLogger("akka").setLevel(Level.OFF)
  Logger.getLogger("spark").setLevel(Level.OFF)
  Logger.getLogger("jetty").setLevel(Level.OFF)
  Logger.getRootLogger.setLevel(Level.OFF)

  var spark: SparkSession = _
  var runner: PySparkRunner = _

  def delete(file: File) {
    if (file.isDirectory)
      Option(file.listFiles).map(_.toList).getOrElse(Nil).foreach(delete(_))
    file.delete
  }

  override protected def beforeAll(): Unit = {
    val env = Environment()
    val notifier = new TestNotifier()

    // this is an ugly hack, getClass.getResource("/").getPath should have worked but
    // stopped working when we moved to gradle :(
    val resources = new File(getClass.getResource("/spark_intp.py").getPath).getParent

    val conf = new SparkConf(true)
      .setMaster("local[1]")
      .setAppName("job_5")
      .set("spark.local.ip", "127.0.0.1")
      .set("spark.ui.port", "4081")
      .setExecutorEnv("PYTHONPATH", resources)

    spark = SparkSession.builder.
      master("local")
      .appName("job_5")
      .config(conf)
      .getOrCreate()

    runner = PySparkRunner(env, "job_5", notifier, spark, resources)

    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    spark.stop()
    val pysparkDir = new File(getClass.getResource("/pyspark").getPath)
    val py4jDir = new File(getClass.getResource("/py4j").getPath)
    delete(pysparkDir)
    delete(py4jDir)
    spark.stop()

    super.afterAll()
  }


  "PySparkRunner.executeSource" should "execute simple python code" in {
    runner.executeSource(getClass.getResource("/simple-python.py").getPath, "test_action1", Map.empty[String, String].asJava)
  }

  it should "print and trows an errors" in {
    a[java.lang.Exception] should be thrownBy {
      runner.executeSource(getClass.getResource("/simple-python-err.py").getPath, "test_action2", Map.empty[String, String].asJava)
    }
  }

  it should "also execute spark code written in python" in {
    runner.executeSource(getClass.getResource("/simple-pyspark.py").getPath, "test_action3", Map.empty[String, String].asJava)
  }

  it should "also execute spark code written in python with AmaContext being used" in {
    runner.executeSource(getClass.getResource("/pyspark-with-amacontext.py").getPath, "test_action4", Map.empty[String, String].asJava)
  }

}