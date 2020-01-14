package com.svend.demo.ingestion

import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.ThrottleMode
import akka.stream.alpakka.sse.scaladsl.EventSource
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.StringSerializer

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.language.postfixOps

object Ingest extends App {

  val topic = "test-topic"

  implicit val system = ActorSystem()

  val send: HttpRequest => Future[HttpResponse] = Http().singleRequest(_)

  def parse(row: String): (String, String) = (row.drop(15), row)

  // input rowId is written as part of the kafka headers => let's find out the latest one committed
  val latestCommittedRowId = KafkaRowIdReader.latestCommittedRowId("localhost:9092", topic)
  println(s"latestCommittedRowId: $latestCommittedRowId")

  // everything default :)
  val producerSettings =
    ProducerSettings(system, new StringSerializer, new StringSerializer)
      .withBootstrapServers("localhost:9092")

  val httpSource = EventSource(
    uri = Uri(s"http://localhost:8080/events?fromRowId=$latestCommittedRowId"),
    send,
    initialLastEventId = None,
    retryDelay = 1.second)

  httpSource
    .throttle(elements = 10, per = 1.second, maximumBurst = 1, ThrottleMode.Shaping)
    .map(event => parse(event.data))
    .map { case (rowId, row) =>
      new ProducerRecord[String, String](
        topic,
        null,
        rowId, row,
        List(new RecordHeader("db.row.id", rowId.getBytes(StandardCharsets.UTF_8)).asInstanceOf[Header]).asJava
      )
    }
    .runWith(Producer.plainSink(producerSettings))

}
