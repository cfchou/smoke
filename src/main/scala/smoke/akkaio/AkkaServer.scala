package smoke.akkaio

import smoke._
import smoke.netty.ConfigHelpers
import java.net.InetSocketAddress
import scala.concurrent.{ExecutionContext, Future}
import akka.actor._
import akka.io.{Tcp, IO}
import akka.io.Tcp._
import com.typesafe.config.{ConfigFactory, Config}
import scala.Some


class ServerController(implicit val config: Config, ec: ExecutionContext)
  extends Server {


  override def setApplication(application: (Request) => Future[Response]): Unit = ???

  override def start(): Unit =  AkkaServer.start
  override def stop(): Unit = AkkaServer.stop
}

object AkkaServer {

  sealed trait ServerMessage
  case object Start extends ServerMessage
  case object Stop extends ServerMessage
  case class SetApplication(application: (Request) => Future[Response])
    extends ServerMessage

  private var system: Option[ActorSystem] = None

  def start(implicit config: Config, ec: ExecutionContext): Unit = {
    system = system.fold({
      /*
      custom Config will be sandwiched between the default(reference.conf) and
      the override(application.conf).
       */
      Some(ActorSystem("SmokingServer", ConfigFactory.load(config)))
    })(Some(_))
    val server = system.get.actorOf(Props[AkkaServer], "AkkaServer")
    server ! Start
  }

  def stop(): Unit = {
    system.foreach({ sys =>
      sys.shutdown()
    })
  }

  //def props(config: Config): Props = Props(classOf[AkkaServer], config)
}

/*
AkkServer has one or more listeners as children. They're under default
supervisor strategy which is most of time to restart on one-for-one basis.
 */
class AkkaServer(implicit val config: Config)
  extends Actor
  with ActorLogging
  with ConfigHelpers {

  /*
  We don't want AkkaServer to automatically restart as its life cycle is
  explicitly maintained by calling companion's start/stop. We need to set up
  "akka.actor.guardian-supervisor-strategy" since AkkaServer is the child of
  the user guardian.
  postRestart() is overriden to stop restart in case the setting isn't right.
   */
  @scala.throws[Exception](classOf[Exception])
  override def postRestart(reason: Throwable): Unit = {
    // by default, postRestart() calls preStart()
    context stop self
  }

  override def receive: Actor.Receive = {
    case AkkaServer.Start =>
      config.getScalaIntList("http.ports").foreach({ port =>
        context.system.actorOf(ConnListener.props(port))
      })
  }
}

object ConnListener {
  def props(port: Int): Props = Props(classOf[ConnListener], port)
}

/* Keeping an Actor's fields private makes it explicit that clients should
 * use messages instead of getter/setter.
 */
class ConnListener(private val port: Int)
  extends Actor
  with ActorLogging {

  implicit val system = context.system
  //implicit val ec: ExecutionContext = context.system.dispatcher

  // don't recover broken connections(one-for-one, Stop)
  override def supervisorStrategy: SupervisorStrategy =
    SupervisorStrategy.stoppingStrategy


  @scala.throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
     /* TODO:
      * SSL support
     */
    log.info("(Re)Starting ConnListener")
    IO(Tcp) ! Bind(self, new InetSocketAddress(port))
  }

  override def receive: Receive = {
    case Bound(local) =>
      log.info("Bound to port " + local.getPort)

    case CommandFailed(Bind(_, local, _, _, _)) =>
      log.error(s"Can't not bind $local")
      context stop self // will be restarted by supervisor AkkaServer

    case Tcp.Connected(remote, _) =>
      log.info(s"Connected from $remote")

      val handler =
        context.actorOf(HttpConnectionHandler.props(sender(), remote))

      //sender ! Register(handler, keepOpenOnPeerClosed = true, useResumeWriting = true)
      sender ! Register(handler)
  }
}


object HttpConnectionHandler {
  def props(connection: ActorRef, remote: InetSocketAddress): Props =
    Props(classOf[HttpConnectionHandler], connection, remote)
}

class HttpConnectionHandler(private val connection: ActorRef,
                            private val remote: InetSocketAddress)
  extends Actor
  with ActorLogging {

  // terminates if connection breaks
  context watch connection

  override def receive: Actor.Receive = {
    case _: Tcp.ConnectionClosed =>
    case Tcp.Received(data) =>
  }
}



