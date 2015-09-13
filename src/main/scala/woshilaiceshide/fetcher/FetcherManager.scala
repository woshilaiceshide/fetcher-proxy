package woshilaiceshide.fetcher

import akka.actor._
import akka.io._
import spray.can.Http

import scala.concurrent.duration.FiniteDuration

object FetcherManager {

  //stop gracefully, make sure the listener unbound.
  private object STOP

  private case class WatchIt(x: ActorRef, sink: () => Unit)
  class Watcher() extends Actor with ActorLogging {
    private var sink: Option[() => Unit] = None
    override def receive = akka.event.LoggingReceive {
      case WatchIt(x, sink1) => { context.watch(x); sink = Some(sink1) }
      case Terminated(x) => {
        sink.map(_())
        context.stop(self)
      }
    }
  }

  def start(akkaSystem: ActorSystem, name: String, stopSink: => Unit) = {
    val manager = akkaSystem.actorOf(Props(classOf[FetcherManager]), name)
    val watcher = akkaSystem.actorOf(Props(classOf[Watcher]))
    watcher ! WatchIt(manager, () => { stopSink })
    manager
  }

  def gracefulStop(manager: ActorRef, timeout: FiniteDuration) = {
    akka.pattern.gracefulStop(manager, timeout, STOP)
  }

  private object SHUTTING

  protected[fetcher] case class TheListener(listener: ActorRef)
}
class FetcherManager() extends Actor with Stash with ActorLogging {

  import FetcherManager._

  override def preRestart(reason: Throwable, message: Option[Any]) {
    //DO NOT RESTART ME. Just let me go!
    context.stop(self)
  }

  override def preStart() = {
    interface = context.system.settings.config.getString("fetcher-proxy.interface")
    port = context.system.settings.config.getInt("fetcher-proxy.port")

    service = Some(context.actorOf(Props[FetcherService], name = fetcher_service_name))
    service.map { x =>
      context.watch(x)
      IO(Http)(context.system) ! Http.Bind(x, interface = interface, port = port, options = List(Inet.SO.ReuseAddress(true)))
    }
    listener = Some(None)
    context.become(binding, true)

  }

  override def postStop() = {
    listener.flatten.map { _ ! Http.Unbind }
    service.map { context.stop }
  }

  private var interface: String = _
  private var port: Int = _

  private var fetcher_service_name = "fetcher-service"
  private var service: Option[ActorRef] = None
  private var listener: Option[Option[ActorRef]] = None

  override def supervisorStrategy: SupervisorStrategy = SupervisorStrategy.defaultStrategy
  //override def supervisorStrategy: SupervisorStrategy = SupervisorStrategy.stoppingStrategy

  def binding: Receive = akka.event.LoggingReceive {
    case _: Tcp.Bound => {
      listener = Some(Some(sender))
      service.map { _ ! TheListener(sender) }
      context.watch(sender)
      context.become(working, true)
      unstashAll()
    }
    case Tcp.CommandFailed(cmd: Http.Bind /*Tcp.Bind*/ ) => {
      listener = None
      becomeShutting(true)
    }
    case x => stash()
  }

  private def becomeShutting(unstash: Boolean) {
    context.become(shutting, true)
    if (unstash) unstashAll()
    self ! SHUTTING
  }
  def shutting: Receive = akka.event.LoggingReceive {
    case STOP => {}
    case SHUTTING => {
      listener.flatten.map { _ ! Http.Unbind }
      service.map { context.stop }
      if (listener.isEmpty && service.isEmpty) {
        context.stop(self)
      }
    }
    case Terminated(a) if a.path.name == fetcher_service_name => {
      service = None
      if (listener.isEmpty && service.isEmpty) {
        context.stop(self)
      }
    }
    case Terminated(a) => {
      listener = None
      if (listener.isEmpty && service.isEmpty) {
        context.stop(self)
      }
    }
    case Tcp.CommandFailed(Http.Unbind) => {
      listener = None
      if (listener.isEmpty && service.isEmpty) {
        context.stop(self)
      }
    }
    case Tcp.Unbound => {}
  }

  def working: Receive = akka.event.LoggingReceive {
    case Terminated(a) if a.path.name == fetcher_service_name => {
      service = None
      becomeShutting(false)
    }
    case Terminated(a) => {
      listener = None
      becomeShutting(false)
    }
    case STOP => {
      becomeShutting(false)
    }
    case Tcp.Unbound => {}
  }

  def empty: Receive = akka.event.LoggingReceive {
    case x => { /*???*/ }
  }

  override def receive = empty

}
