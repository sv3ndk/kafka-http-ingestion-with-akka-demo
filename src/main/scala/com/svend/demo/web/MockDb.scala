package com.svend.demo.web

import akka.actor.typed.scaladsl._
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration._

/**
 * Basic Mock DB: receive async query request for <pageSize> rows and sends back a generated page 500ms later.
 *
 * This is the main back-end of the v1 solution.
 * It is also the backend behind the buffer of the v2 solutieon
 **/
object MockDb {

  trait DbProtocol

  case class DbQuery(fromRowId: Long, pageSize: Long, replyTo: ActorRef[Rows]) extends DbProtocol

  case class ResponseFromDb(rows: List[String], replyTo: ActorRef[Rows]) extends DbProtocol

  case class Rows(rows: List[String])


  def apply(): Behavior[DbProtocol] = Behaviors.receive {
    case (ctx, DbQuery(fromRowId, pageSize, replyTo)) =>

      // simulate a DB delay of 500ms
      ctx.scheduleOnce(
        500.milliseconds,
        ctx.self,
        ResponseFromDb(List.range(fromRowId, fromRowId + pageSize).map(num => s"this is db row $num"), replyTo))

      Behaviors.same

    case (ctx, ResponseFromDb(rows, replyTo)) =>

      println("one more page")
      replyTo ! Rows(rows)
      Behaviors.same
  }

}
