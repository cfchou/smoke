package smoke.examples

import org.scalatest.{ FunSpec, BeforeAndAfterAll }

import scala.concurrent.Await
import scala.concurrent.duration._

import smoke._
import smoke.test._

class ErrorHandlerExampleAppTest extends FunSpec with BeforeAndAfterAll {

  val app = new ErrorHandlerExampleSmoke

  override def afterAll { app.shutdown() }

  describe("GET /before-exception") {
    it("should raise a handled exception") {
      val request = TestRequest("/before-exception")
      val response = Await.result(app.application(request), 2 seconds)
      assert(response.body.toString === "Before filter exception")
    }
  }
  describe("GET /after-exception") {
    it("should raise a handled exception") {
      val request = TestRequest("/after-exception")
      val response = Await.result(app.application(request), 2 seconds)
      assert(response.body.toString === "After filter exception")
    }
  }

  describe("GET /future-result-error") {
    it("should respond with 200") {
      val request = TestRequest("/future-result-error")
      val response = Await.result(app.application(request), 2 seconds)
      assert(response.body.toString === "Future result exception")
    }
  }

  describe("GET /request-handler-error") {
    it("should respond with 200") {
      val request = TestRequest("/request-handler-error")
      val response = Await.result(app.application(request), 2 seconds)
      assert(response.body.toString === "Request handler exception")
    }
  }

  describe("POST /unknown-path") {
    it("should respond with 404") {
      val request = TestRequest("/unknown-path", "POST")
      val response = Await.result(app.application(request), 2 seconds)
      assert(response.status === NotFound)
    }
  }
}
