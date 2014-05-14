package smoke.akkaio

import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestActorRef, DefaultTimeout, ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import scala.concurrent.{Promise, Await}
import akka.util.Timeout
import scala.util.Try

class AkkaServerSpec
  extends TestKit(ActorSystem("AkkaServerSpec"))
  with ImplicitSender
  with DefaultTimeout
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {


  override protected def afterAll(): Unit = {
    system.shutdown()
  }

  val regularConfig = ConfigFactory.load()

  "AkkaServer with configured ports" should {
    "start listening" in {
      // overwrite ports
      val portConfig = ConfigFactory.parseString(
        """
          |akka.actor.debug.lifecycle=on
          |http.ports=[7772]
        """.stripMargin).withFallback(regularConfig)

      val server = TestActorRef[AkkaServer](Props(classOf[AkkaServer],
        Some(portConfig))).underlyingActor
      server.receive(AkkaServer.Start)

      // Wait a while for ConnListener gets Bound
      println(s"DefaultTimeout: ${timeout.duration}")
      Try({
        Await.ready(Promise().future, timeout.duration)
      })
    }
  }


}
