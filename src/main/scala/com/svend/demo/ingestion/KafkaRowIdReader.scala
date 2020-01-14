package com.svend.demo.ingestion

import java.time.Duration
import java.util
import java.util.{Collections, Properties}

import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRebalanceListener, KafkaConsumer}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer

import scala.jdk.CollectionConverters._

object KafkaRowIdReader {

  /**
   * Looks up the "db.row.id" header with the largest value among the data already written to Kafka
   */
  def latestCommittedRowId(brokers: String, topic: String) = {

    val kafkaProperties = new Properties()
    kafkaProperties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers)
    kafkaProperties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "row-id-reader")
    kafkaProperties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")

    val consumer = new KafkaConsumer[String, String](kafkaProperties, new StringDeserializer, new StringDeserializer)

    // seeks to the offset before last
    consumer.subscribe(Collections.singletonList(topic), new ConsumerRebalanceListener() {
      override def onPartitionsRevoked(partitions: util.Collection[TopicPartition]): Unit = {}

      override def onPartitionsAssigned(partitions: util.Collection[TopicPartition]): Unit = {
        consumer
          .endOffsets(partitions)
          .forEach { case (parition, endOffset) => consumer.seek(parition, Math.max(endOffset - 1, 0)) }
      }
    })

    consumer
      .poll(Duration.ofSeconds(15))
      .asScala
      .map(record => new String(record.headers().headers("db.row.id").asScala.head.value()).toInt)
      .maxOption
      .getOrElse(0)
  }

}
