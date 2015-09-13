package woshilaiceshide.fetcher

import akka.actor.{ ActorSystem, Props }
import scala.concurrent._

object Server extends App {

  //val config = com.typesafe.config.ConfigFactory.parseFileAnySyntax(new java.io.File("conf/application.conf"))
  val config = com.typesafe.config.ConfigFactory.load()

  val system = ActorSystem("fetcher-server", config)

  val manager = FetcherManager.start(system, "fetcher-manager", {
    system.shutdown()
  })

  val wait_for_x_seconds_when_stop = system.settings.config.getInt("fetcher-proxy.wait_for_x_seconds_when_stop")

  def seconds(i: Int) = {
    import scala.concurrent.duration._
    Duration(i.toLong, SECONDS)
  }

  Runtime.getRuntime.addShutdownHook(new Thread {
    override def run {

      import akka.pattern.gracefulStop

      try {
        val stopped: Future[Boolean] = FetcherManager.gracefulStop(manager, seconds(wait_for_x_seconds_when_stop))
        Await.result(stopped, seconds(wait_for_x_seconds_when_stop + 1))
      } catch {
        case e: akka.pattern.AskTimeoutException => system.shutdown()
      }
      system.awaitTermination()
    }
  })

}
