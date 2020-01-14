package com.svend.demo.web

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Source
import akka.stream.typed.scaladsl.ActorFlow
import akka.util.Timeout
import com.svend.demo.web.MockDb.{DbProtocol, DbQuery, Rows}

import scala.collection.immutable._
import scala.concurrent.duration._
import scala.language.postfixOps

class DbRoutes(dbReader: ActorRef[DbProtocol], actorContext: ActorContext[Nothing])(implicit val system: ActorSystem[_]) {

  import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._

  val pageSize = 20

  val dbRoutes: Route =

  // curl -X GET 'http://localhost:8080/events?fromRowId=5000' --limit-rate 10K
    path("events") {
      get {
        parameter("fromRowId".as[Long]) { fromRowId =>
          complete {

            implicit val timeout = Timeout.durationToTimeout(1 minute)

            Source

              // infinite stream of paginated "fromRowId"
              .fromIterator(() => Iterator.from(0, pageSize).map(_ + fromRowId))

              // async requests to DB
              .via(
                ActorFlow
                  .ask(10)(dbReader)((fromRowId, replyTo: ActorRef[Rows]) => DbQuery(fromRowId, pageSize, replyTo))
              )

              // flatten rows + fix out-of orderness due to concurrent DB access (assuming a maximum delay...)
              .mapConcat(dbResponse => dbResponse.rows)
              .statefulMapConcat(sort(pageSize * 20, (row: String) => row.drop(15).toInt))

              // output as SSE events
              .map(ServerSentEvent(_))

              .keepAlive(1.second, () => ServerSentEvent.heartbeat)
          }
        }
      }
    }


  /**
   * partial sort of a stream: wait for <bufferSize> to be buffered, the start flushing them out in order
   **/
  def sort[T, S](bufferSize: Int, order: T => S)(implicit ordering: Ordering[S]): () => T => Iterable[T] = () => {

    var buffer = List.empty[T]

    t: T => {
      buffer = (buffer :+ t).sortBy(order)
      if (buffer.size < bufferSize) Iterable.empty[T]
      else {
        val r = buffer.head
        buffer = buffer.tail
        List(r)
      }
    }
  }

}
