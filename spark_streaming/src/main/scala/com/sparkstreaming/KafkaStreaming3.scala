package com.sparkstreaming

import java.io.IOException

import com.beans.RecordBean
import com.utils.JsonUtils
import org.apache.hadoop.mapreduce.OutputFormat
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.log4j.{Level, Logger}
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.KafkaUtils
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferBrokers
import org.apache.spark.streaming.{Seconds, StreamingContext, Time}

/**
  * @ClassName LauncherStreaming2
  * @Description TODO 启动sparkstreaming ，并做ETL（装载，转换，存储）,parseLog：解析日志方法
  * @Author zhouyang
  * @Date 2019/2/24 12:22
  * @Version 1.0
  **/
object KafkaStreaming3 {
  //
  private lazy val logger = Logger.getLogger(getClass)
  //数据在hdfs上的路径
  val HDFS_DIR = "/test/test-data/kafka-streaming"

  //
  def main(args: Array[String]) {

    Logger.getLogger("org.apache.spark").setLevel(Level.WARN)
    //对象序列化配置
    System.setProperty("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    //sparkconf
    val sparkConf = new SparkConf().setAppName("KafkaStreaming3")
    //每60秒一个批次
    val ssc = new StreamingContext(sparkConf, Seconds(60))
    //Kafka集群使用的zookeeper
    val servers = "hxf:2181,cfg:2181,jqs:2181,jxf:2181,sxtb:2181"
    //
    val params = Map[String, Object](
      "bootstrap.servers" -> servers,
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "auto.offset.reset" -> "latest",
      "group.id" -> "launcher-streaming",
      "enable.auto.commit" -> (false: java.lang.Boolean)
    )

    // 要读的topic name
    val topics = List("launcher_click")

    // 创建kafka流对象
    val stream = KafkaUtils.createDirectStream[String, String](
      ssc, PreferBrokers, Subscribe(topics, params)
    )
    // just alias for simplicity
    type Record = ConsumerRecord[String, String]
    //rdd 计算
    stream.foreachRDD((rdd : RDD[Record], time : Time) => {
      //将消息中数据生成新的键值对
      val pairs = rdd
        //解析日志
        .map(row => parseLog(row))
      // 存入hdfs
      pairs.saveAsNewAPIHadoopFile(HDFS_DIR, classOf[String], classOf[String], classOf[OutputFormat[String, String]])
    })

    //启动sparkstreaming context，提交任务
    ssc.start()
    // 等待实时流
    ssc.awaitTermination()
  }

  /**
    * Json decode UDF function
    *
    * @param text the encoded JSON string
    * @return Returns record bean
    */
  def jsonDecode(text: String): RecordBean = {
    try {
      JsonUtils.deserialize(text, classOf[RecordBean])
    } catch {
      case e: IOException =>
        logger.error(e.getMessage, e)
        null
    }
  }

  /*
   * @Author zhouyang
   * @Description TODO 分析日志
   * @Date 13:54 2019/2/24
   * @Param [log]
   * @return
   **/
  def parseLog(row: ConsumerRecord[String, String]): (String, (Int, Float, Long)) = {

    val (k1, v1) = (row.timestamp(), jsonDecode(row.value()));
    val (k2, v2) = (v1.getType.name(), (1, v1.getValue, k1))
    (k2, v2)
  }
}
