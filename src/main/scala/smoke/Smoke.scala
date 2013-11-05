package smoke

import scala.compat.Platform.currentTime
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ Future, ExecutionContext }
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.util.Timeout

import smoke.netty.NettyServer

trait Smoke extends DelayedInit {

  def configure() = { ConfigFactory.load() }

  final implicit val config = configure()

  final implicit val system: ActorSystem = ActorSystem("Smoke", config)
  final implicit val dispatcher: ExecutionContext = system.dispatcher

  private val timeoutDuration: Long = config.getMilliseconds("smoke.timeout")
  final implicit val timeout = Timeout(timeoutDuration milliseconds)

  def setServer() = { new NettyServer }

  private val server = setServer()

  private var beforeFilter = { request: Request ⇒ request }
  private var responder = { request: Request ⇒
    Future.successful(Response(ServiceUnavailable))
  }
  private var afterFilter = { response: Response ⇒ response }
  private var errorHandler: PartialFunction[Throwable, Response] = {
    case t: Throwable ⇒ Response(InternalServerError, body = t.getMessage + "\n" +
      t.getStackTrace.mkString("\n"))
  }

  private var shutdownHooks = List(() ⇒ {
    server.stop()
    system.shutdown()
  })

  private def withErrorHandling[T](errorProne: T ⇒ Future[Response]) = {
    def maybeFails(x: T): Future[Response] = {
      try {
        errorProne(x)
      } catch {
        case e: Exception ⇒
          Future.failed(e)
      }
    }

    maybeFails _ andThen { _ recover errorHandler }
  }

  def application = withErrorHandling {
    beforeFilter andThen responder andThen { _ map afterFilter }
  }

  def before(filter: (Request) ⇒ Request) { beforeFilter = filter }

  def after(filter: (Response) ⇒ Response) { afterFilter = filter }

  def onRequest(handler: (Request) ⇒ Future[Response]) { responder = handler }

  def onError(handler: PartialFunction[Throwable, Response]) {
    errorHandler = handler orElse errorHandler
  }

  def beforeShutdown(hook: ⇒ Unit) { shutdownHooks = hook _ :: shutdownHooks }

  def afterShutdown(hook: ⇒ Unit) { shutdownHooks = shutdownHooks ::: List(hook _) }

  def reply(action: ⇒ Response) = Future(action)

  def fail(e: Exception) = Future.failed(e)

  val executionStart: Long = currentTime
  var running = false

  protected def args: Array[String] = _args
  private var _args: Array[String] = _

  private var initCode: () ⇒ Unit = _
  override def delayedInit(body: ⇒ Unit) { initCode = (() ⇒ body) }

  def init(args: Array[String] = Seq.empty.toArray) {
    if (!running) {
      _args = args
      initCode()
      running = true
    }
  }

  def shutdown() {
    if (running) {
      running = false
      shutdownHooks foreach { hook ⇒ hook() }
    }
  }

  def main(args: Array[String]) = {
    try {
      init(args)
      server.setApplication(application)
      server.start()
    } catch {
      case e: Throwable ⇒
        shutdownHooks foreach { hook ⇒ hook() }
        throw e
    }

    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      def run = shutdown()
    }))
  }
}
