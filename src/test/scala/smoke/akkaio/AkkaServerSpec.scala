package smoke.akkaio

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class AkkaServerSpec
  extends TestKit(ActorSystem("AkkaIOServerSpec"))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {
  override protected def beforeAll(): Unit = {
    super.beforeAll()

  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    system.shutdown()
  }
}
