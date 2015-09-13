package woshilaiceshide.fetcher

import scala.concurrent.duration.DurationInt

import akka.actor._
import akka.io.Tcp
import akka.pattern.ask
import akka.util.Timeout.durationToTimeout
import spray.can.Http
import spray.can.server.Stats
import spray.http.ContentType.apply
import spray.http.HttpEntity
import spray.http.HttpEntity.apply
import spray.http.HttpMethods._
import spray.http.HttpRequest
import spray.http.HttpResponse
import spray.http.MediaTypes.`text/html`
import spray.http.StatusCode.int2StatusCode
import spray.http.Uri
import spray.util.pimpDuration

import FetcherManager._

class FetcherService extends Actor with ActorLogging {

  override def preStart() = {}

  override def postStop() = {}

  private def renderStats(s: Stats) = HttpResponse(
    entity = HttpEntity(`text/html`,
      <html>
        <body>
          <h1>HttpServer Stats</h1>
          <table>
            <tr><td>uptime:</td><td>{ s.uptime.formatHMS }</td></tr>
            <tr><td>totalRequests:</td><td>{ s.totalRequests }</td></tr>
            <tr><td>openRequests:</td><td>{ s.openRequests }</td></tr>
            <tr><td>maxOpenRequests:</td><td>{ s.maxOpenRequests }</td></tr>
            <tr><td>totalConnections:</td><td>{ s.totalConnections }</td></tr>
            <tr><td>openConnections:</td><td>{ s.openConnections }</td></tr>
            <tr><td>maxOpenConnections:</td><td>{ s.maxOpenConnections }</td></tr>
            <tr><td>requestTimeouts:</td><td>{ s.requestTimeouts }</td></tr>
          </table>
        </body>
      </html>.toString()))

  private var id: Long = 1024
  def newId() = { id = id + 1; id }

  override def supervisorStrategy: SupervisorStrategy = SupervisorStrategy.stoppingStrategy

  private var listener: Option[ActorRef] = None
  override def receive = akka.event.LoggingReceive {

    case TheListener(listener1) => listener = Option(listener1)

    case _: Http.Connected => {
      // when a new connection comes in we register ourselves as the connection handler
      sender ! Http.Register(self)
    }

    //Closed, PeerClosed
    case _: Tcp.ConnectionClosed => {}

    case HttpRequest(GET, Uri.Path("/@ping"), _, _, _) => {
      sender ! HttpResponse(entity = "PONG!")
    }

    case HttpRequest(GET, Uri.Path("/@server-stats"), _, _, _) => {
      import context.dispatcher
      val client = sender
      //context.actorSelection("/user/IO-HTTP/listener-0")
      listener match {
        case None => sender ! HttpResponse(status = 503, entity = "server is initializing")
        case Some(x) => {
          x.ask(Http.GetStats)(1.second) onSuccess {
            case x: Stats => client ! renderStats(x)
          }
        }
      }
    }

    case HttpRequest(GET, Uri.Path("/@404"), _, _, _) => {
      sender ! HttpResponse(status = 404, entity = "unknown resource")
    }

    case req @ HttpRequest(CONNECT, _, _, _, _) => {
      sender ! HttpResponse(status = 501, entity = """"CONNECT" is not supported""")
    }

    case req @ HttpRequest(method, uri, _, _, _) if uri.isAbsolute && uri.authority.nonEmpty => {
      val name = java.net.URLEncoder.encode(uri.render(new spray.http.StringRendering).get, "utf-8")
      val maxLength = 128
      val name1 = if (maxLength < name.length) {
        val len = if ('%' == name.charAt(maxLength - 1)) {
          maxLength + 2
        } else if ('%' == name.charAt(maxLength - 2)) {
          maxLength + 1
        } else {
          maxLength
        }
        name.substring(0, len) + "..."
      } else {
        name
      }
      val a = context.actorOf(Props(classOf[Fetcher]), name = s"fetcher-${method.value}-${newId}-${name1}")
      a.forward(req)
    }

    case _: HttpRequest => {
      sender ! HttpResponse(status = 400, entity = """No "scheme" in request, or no "host""""")
    }

  }
}
