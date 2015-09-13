package woshilaiceshide.fetcher

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.actorRef2Scala
import akka.actor.Terminated
import akka.io.Tcp.Close
import spray.can.Http
import spray.http.ChunkedMessageEnd
import spray.http.ChunkedResponseStart
import spray.http.HttpEntity.apply
import spray.http.HttpRequest
import spray.http.HttpResponse
import spray.http.MessageChunk
import spray.http.StatusCode.int2StatusCode
import spray.http.HttpHeaders._

class Fetcher extends Actor with akka.actor.ActorLogging {

  private var request: HttpRequest = _
  private var waiting: ActorRef = _
  private var chunking: Boolean = false

  def receive = akka.event.LoggingReceive {

    case req: HttpRequest => {
      request = req
      akka.io.IO(Http)(context.system) ! req
      waiting = sender
      context.watch(waiting)
    }

    case Terminated(a) => {
      //just stop myself
      context.stop(self)
    }

    //case _: Http.Connected => sender ! request
    //case Http.HostConnectorInfo(hostConnector, _) => hostConnector ! request

    case chunkStart: ChunkedResponseStart => {
      val filteredHeaders = chunkStart.response.headers.filter { x =>
        x match {
          case _: Date                => false
          case _: `Transfer-Encoding` => false
          case _: Server              => false
          case _                      => true
        }
      }
      waiting ! chunkStart.copy(response = chunkStart.response.copy(headers = filteredHeaders))
      chunking = true
    }
    case chunk: MessageChunk => {
      waiting ! chunk
    }
    case chunkEnd: ChunkedMessageEnd => {
      waiting ! chunkEnd
      context.stop(self)

    }
    case resp: HttpResponse => {
      val filteredHeaders = resp.headers.filter { x =>
        x match {
          case _: Date                => false
          case _: `Transfer-Encoding` => false
          case _: Server              => false
          case _                      => true
        }
      }
      waiting ! resp.copy(headers = filteredHeaders)
      context.stop(self)
    }

    case x @ akka.actor.Status.Failure(cause) => {
      if (chunking) {
        waiting ! Close
      } else {
        waiting ! HttpResponse(
          status = 500,
          entity = cause.getMessage)
      }
      log.error(cause, s"fetch failed")
      context.stop(self)
    }

  }

}
