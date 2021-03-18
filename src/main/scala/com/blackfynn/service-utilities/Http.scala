package com.blackfynn.service.utilities

import akka.actor.ActorSystem
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.{ Http => HttpClient }
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{
  HttpHeader,
  HttpMethods,
  HttpRequest,
  HttpResponse,
  StatusCode,
  StatusCodes
}
import akka.http.scaladsl.server.Directives.{ complete, onComplete }
import akka.http.scaladsl.server.Route
import akka.stream.{ ActorMaterializer, OverflowStrategy, QueueOfferResult }
import akka.stream.scaladsl._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe._
import io.circe.parser._
import io.circe.generic.auto._

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Success, Try }

/**
  * Exception to flag when we can retry a reqeust
  * (ex: Rate limit exceeded, queue overflow)
  */
case class RetryableException(message: String) extends Exception

trait HttpResponder {
  implicit val system: ActorSystem
  implicit val materializer: ActorMaterializer
  implicit val executionContext: ExecutionContext

  def responder: HttpResponder.Responder
}

object HttpResponder {
  type Responder = HttpRequest => Future[HttpResponse]
  type ConnectionPool[T] =
    Flow[(HttpRequest, T), (Try[HttpResponse], T), HostConnectionPool]
}

class SingleHttpResponder(
  implicit
  val system: ActorSystem,
  val executionContext: ExecutionContext,
  val materializer: ActorMaterializer
) extends HttpResponder {

  override def responder = HttpClient().singleRequest(_)
}

/**
  * A queue-based HTTP responder.
  *
  * This responder will set up a queue of requests to go to the
  * provided host. It will throw a `RetryableException` if the queue
  * overflows, which indicates that the request did not get queued and
  * must be tried again.
  *
  * This responder will throttle requests to the host based on the
  * given rateLimit.
  */
class QueueHttpResponder(
  connectionPool: HttpResponder.ConnectionPool[Promise[HttpResponse]],
  queueSize: Int,
  rateLimit: Int
)(implicit
  val system: ActorSystem,
  val executionContext: ExecutionContext,
  val materializer: ActorMaterializer
) extends HttpResponder {

  lazy val queue = Source
    .queue[(HttpRequest, Promise[HttpResponse])](
      queueSize,
      // Queue overflows will be rejected
      OverflowStrategy.dropNew
    )
    .throttle(rateLimit, 1.second)
    .via(connectionPool)
    .toMat(Sink.foreach {
      case (Success(resp), p) => p.success(resp)
      case (Failure(e), p) => p.failure(e)
    })(Keep.left)
    .run()

  override def responder = (request: HttpRequest) => {
    val responsePromise = Promise[HttpResponse]()
    queue.offer(request -> responsePromise).flatMap {

      // successfully queued
      case QueueOfferResult.Enqueued => responsePromise.future

      // queue overflow
      case QueueOfferResult.Dropped =>
        Future.failed(RetryableException("Queue overflowed"))

      // catch-all queue failure
      case QueueOfferResult.Failure(ex) => Future.failed(ex)

      // connection pool failure
      case QueueOfferResult.QueueClosed =>
        Future.failed(new RuntimeException("Pool shut down while running the request"))
    }
  }
}

object QueueHttpResponder {
  def apply(
    host: String,
    queueSize: Int,
    rateLimit: Int,
    https: Boolean = true,
    port: Option[Int] = None
  )(implicit
    system: ActorSystem,
    executionContext: ExecutionContext,
    materializer: ActorMaterializer
  ): QueueHttpResponder = {
    val usePort = port.getOrElse(if (https) 443 else 80)
    lazy val connectionPool: HttpResponder.ConnectionPool[Promise[HttpResponse]] =
      if (https)
        HttpClient()
          .cachedHostConnectionPoolHttps[Promise[HttpResponse]](host = host, port = usePort)
      else
        HttpClient()
          .cachedHostConnectionPool[Promise[HttpResponse]](host = host, port = usePort)

    new QueueHttpResponder(connectionPool, queueSize, rateLimit)
  }
}

class Http(timeout: FiniteDuration) {

  /**
    * A helper function to get the HTTP response body as a UTF-8 encoded String.
    * @param response
    * @param executionContext
    * @param materializer
    * @return
    */
  def getBody(
    response: HttpResponse
  )(implicit
    executionContext: ExecutionContext,
    materializer: ActorMaterializer
  ): Future[String] = {
    response.entity
      .toStrict(timeout)
      .map(_.data)
      .map(_.utf8String)
  }

  /**
    * A helper to interpret the body of a response as a JSON encoding of some prescribed type `T`.
    * @param response
    * @param executionContext
    * @param materializer
    * @tparam T
    * @return
    */
  def unmarshallAs[T: Decoder](
    response: HttpResponse
  )(implicit
    executionContext: ExecutionContext,
    materializer: ActorMaterializer
  ): Future[T] = {
    getBody(response).flatMap { body: String =>
      parse(body) match {
        case Left(failure: Error) => Future.failed(failure)
        case Right(json) =>
          json.as[T] match {
            case Left(parseFailure: Error) =>
              Future.failed(new Throwable(s"Failed to parse: ${body}", parseFailure))
            case Right(obj) => Future.successful(obj)
          }
      }
    }
  }

  /**
    * A helper to interpret the body of a response as a JSON encoding of an error.
    * @param response
    * @param error
    * @param executionContext
    * @param materializer
    * @tparam U
    * @tparam T
    * @return
    */
  def asError[U, T <: Throwable](
    response: HttpResponse,
    error: (StatusCode, Json) => T
  )(implicit
    executionContext: ExecutionContext,
    materializer: ActorMaterializer
  ): Future[U] =
    getBody(response)
      .flatMap(
        body =>
          parse(body) match {
            case Left(_) => {
              val errorResponse: String = body.toString match {
                case "" => "<empty>"
                case nonEmpty => nonEmpty
              }

              Future.failed(error(response.status, Json.fromString(errorResponse)))
            }
            case Right(json) => Future.failed(error(response.status, json))
          }
      )

  def redirectOrResult(
    response: Future[HttpResponse]
  )(implicit
    http: HttpResponder,
    executionContext: ExecutionContext
  ): Future[HttpResponse] =
    response.flatMap { response =>
      response.status match {
        case StatusCodes.Found | StatusCodes.MovedPermanently | StatusCodes.SeeOther =>
          val newUri = response.header[Location].get.uri
          http.responder(HttpRequest(uri = newUri))
        case _ => Future.successful(response)
      }
    }

}
