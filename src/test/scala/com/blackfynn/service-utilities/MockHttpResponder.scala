package com.pennsieve.service.utilities

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model._
import akka.http.scaladsl.{ Http => HttpClient }
import akka.util.ByteString

import io.circe._
import io.circe.syntax._

import scala.concurrent.Future

trait MockHttpResponder extends HttpResponder {

  /*
   * Create a Future[HttpResponse] from a given StatusCode and some JSON
   */
  protected def jsonResponse(statusCode: StatusCode, payload: Json): Future[HttpResponse] = {
    Future.successful {
      HttpResponse(
        entity = HttpEntity
          .Strict(ContentTypes.`application/json`, ByteString(payload.noSpaces)),
        status = statusCode
      )
    }
  }

  /*
   * A partial function that can be used to respond to different request
   * methods against different uri values
   */
  def mock: PartialFunction[(HttpMethod, String), (StatusCode, Json)]

  override def responder = (req: HttpRequest) => {
    if (mock.isDefinedAt((req.method, req.uri.toString))) {
      val (statusCode, payload) = mock((req.method, req.uri.toString))
      jsonResponse(statusCode, payload)
    } else {
      HttpClient().singleRequest(req)
    }
  }
}
