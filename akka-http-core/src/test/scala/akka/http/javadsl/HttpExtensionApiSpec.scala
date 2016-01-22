/**
 * Copyright (C) 2016 Typesafe Inc. <http://www.typesafe.com>
 */
package akka.http.javadsl

import java.net.InetSocketAddress
import java.util.Optional
import java.util.concurrent.{ CompletionStage, TimeUnit, CompletableFuture }

import akka.NotUsed
import akka.http.impl.util.JavaMapping.HttpsConnectionContext
import akka.japi.Pair
import akka.actor.ActorSystem
import akka.event.{ NoLogging, LoggingAdapterTest }
import akka.http.{ ClientConnectionSettings, ConnectionPoolSettings, ServerSettings }
import akka.http.javadsl.model._
import akka.http.scaladsl.TestUtils
import akka.japi.Function
import akka.stream.ActorMaterializer
import akka.stream.javadsl.{ Source, Flow, Sink, Keep }
import akka.stream.testkit.TestSubscriber
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.{ WordSpec, Matchers, BeforeAndAfterAll }
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

class HttpExtensionApiSpec extends WordSpec with Matchers with BeforeAndAfterAll with ScalaFutures {
  val testConf: Config = ConfigFactory.parseString("""
    akka.loggers = ["akka.testkit.TestEventListener"]
    akka.loglevel = ERROR
    akka.stdout-loglevel = ERROR
    windows-connection-abort-workaround-enabled = auto
    akka.log-dead-letters = OFF
    akka.http.server.request-timeout = infinite""")
  implicit val system = ActorSystem(getClass.getSimpleName, testConf)
  implicit val materializer = ActorMaterializer()
  implicit val patience = PatienceConfig(3.seconds)
  val http = Http.get(system)
  val connectionContext = ConnectionContext.noEncryption()
  val serverSettings = ServerSettings.create(system)
  val poolSettings = ConnectionPoolSettings.create(system)
  val loggingAdapter = NoLogging

  val successResponse = HttpResponse.create().withStatus(200)

  val httpSuccessFunction = new Function[HttpRequest, HttpResponse] {
    @throws(classOf[Exception])
    override def apply(param: HttpRequest): HttpResponse = successResponse
  }

  val asyncHttpSuccessFunction = new Function[HttpRequest, CompletionStage[HttpResponse]] {
    @throws(classOf[Exception])
    override def apply(param: HttpRequest): CompletionStage[HttpResponse] =
      CompletableFuture.completedFuture(successResponse)
  }

  "The Java HTTP extension" should {

    // all four bind method overloads
    "properly bind a server 1" in {
      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()
      val probe = TestSubscriber.manualProbe[IncomingConnection]()
      val binding = http.bind(host, port, materializer)
        .toMat(Sink.fromSubscriber(probe), Keep.left)
        .run(materializer)
      val sub = probe.expectSubscription()
      val address = binding.toCompletableFuture.get(1, TimeUnit.SECONDS).localAddress
      sub.cancel()
    }

    "properly bind a server 2" in {
      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()
      val probe = TestSubscriber.manualProbe[IncomingConnection]()
      val binding = http.bind(host, port, connectionContext, materializer)
        .toMat(Sink.fromSubscriber(probe), Keep.left)
        .run(materializer)
      val sub = probe.expectSubscription()
      val address = binding.toCompletableFuture.get(1, TimeUnit.SECONDS).localAddress
      sub.cancel()
    }

    "properly bind a server 3" in {
      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()
      val probe = TestSubscriber.manualProbe[IncomingConnection]()
      val binding = http.bind(host, port, connectionContext, serverSettings, materializer)
        .toMat(Sink.fromSubscriber(probe), Keep.left)
        .run(materializer)
      val sub = probe.expectSubscription()
      val address = binding.toCompletableFuture.get(1, TimeUnit.SECONDS).localAddress
      sub.cancel()
    }

    "properly bind a server 4" in {
      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()
      val probe = TestSubscriber.manualProbe[IncomingConnection]()
      val binding = http.bind(host, port, connectionContext, serverSettings, loggingAdapter, materializer)
        .toMat(Sink.fromSubscriber(probe), Keep.left)
        .run(materializer)
      val sub = probe.expectSubscription()
      binding.toCompletableFuture.get(1, TimeUnit.SECONDS).unbind()
      sub.cancel()
    }

    // this cover both bind and single request
    "properly bind and handle a server with a flow 1" in {
      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()
      val flow: Flow[HttpRequest, HttpResponse, NotUsed] = akka.stream.scaladsl.Flow[HttpRequest]
        .map(req ⇒ HttpResponse.create())
        .asJava
      val binding = http.bindAndHandle(flow, host, port, materializer)

      val (_, completion) = http.outgoingConnection(ConnectHttp.toHost(host, port))
        .runWith(Source.single(HttpRequest.create("/abc")), Sink.head(), materializer).toScala

      completion.toCompletableFuture.get(1, TimeUnit.SECONDS)
      binding.toCompletableFuture.get(1, TimeUnit.SECONDS).unbind()
    }

    "properly bind and handle a server with a flow 2" in {
      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()
      val flow: Flow[HttpRequest, HttpResponse, NotUsed] = akka.stream.scaladsl.Flow[HttpRequest]
        .map(req ⇒ HttpResponse.create())
        .asJava
      val binding = http.bindAndHandle(flow, host, port, connectionContext, materializer)

      val (_, completion) = http.outgoingConnection(ConnectHttp.toHost(host, port))
        .runWith(Source.single(HttpRequest.create("/abc")), Sink.head(), materializer).toScala

      completion.toCompletableFuture.get(1, TimeUnit.SECONDS)
      binding.toCompletableFuture.get(1, TimeUnit.SECONDS).unbind()
    }

    "properly bind and handle a server with a flow 3" in {
      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()
      val flow: Flow[HttpRequest, HttpResponse, NotUsed] = akka.stream.scaladsl.Flow[HttpRequest]
        .map(req ⇒ HttpResponse.create())
        .asJava
      val binding = http.bindAndHandle(flow, host, port, serverSettings, connectionContext, loggingAdapter, materializer)

      val (_, completion) = http.outgoingConnection(ConnectHttp.toHost(host, port))
        .runWith(Source.single(HttpRequest.create("/abc")), Sink.head(), materializer).toScala

      completion.toCompletableFuture.get(1, TimeUnit.SECONDS)
      binding.toCompletableFuture.get(1, TimeUnit.SECONDS).unbind()
    }

    "properly bind and handle a server with a synchronous function 1" in {
      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()
      val binding = http.bindAndHandleSync(httpSuccessFunction, host, port, materializer)

      val response = http.singleRequest(HttpRequest.create(s"http://$host:$port/").withMethod(HttpMethods.GET), materializer)

      response.toCompletableFuture.get(1, TimeUnit.SECONDS)
      binding.toCompletableFuture.get(1, TimeUnit.SECONDS).unbind()
    }

    "properly bind and handle a server with a synchronous function 2" in {
      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()
      val binding = http.bindAndHandleSync(httpSuccessFunction, host, port, connectionContext, materializer)

      val response = http.singleRequest(HttpRequest.create(s"http://$host:$port/").withMethod(HttpMethods.GET), materializer)

      response.toCompletableFuture.get(1, TimeUnit.SECONDS)
      binding.toCompletableFuture.get(1, TimeUnit.SECONDS).unbind()
    }

    "properly bind and handle a server with a synchronous function 3" in {
      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()
      val binding = http.bindAndHandleSync(httpSuccessFunction, host, port, serverSettings, connectionContext, loggingAdapter, materializer)

      val response = http.singleRequest(HttpRequest.create(s"http://$host:$port/").withMethod(HttpMethods.GET), materializer)

      response.toCompletableFuture.get(1, TimeUnit.SECONDS)
      binding.toCompletableFuture.get(1, TimeUnit.SECONDS).unbind()
    }

    "properly bind and handle a server with an asynchronous function 1" in {
      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()
      val binding = http.bindAndHandleAsync(asyncHttpSuccessFunction, host, port, materializer)

      val response = http.singleRequest(HttpRequest.create(s"http://$host:$port/").withMethod(HttpMethods.GET), materializer)

      response.toCompletableFuture.get(1, TimeUnit.SECONDS)
      binding.toCompletableFuture.get(1, TimeUnit.SECONDS).unbind()
    }

    "properly bind and handle a server with an asynchronous function 2" in {
      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()
      val binding = http.bindAndHandleAsync(asyncHttpSuccessFunction, host, port, connectionContext, materializer)

      val response = http.singleRequest(HttpRequest.create(s"http://$host:$port/").withMethod(HttpMethods.GET), materializer)

      response.toCompletableFuture.get(1, TimeUnit.SECONDS)
      binding.toCompletableFuture.get(1, TimeUnit.SECONDS).unbind()
    }

    "properly bind and handle a server with an asynchronous function 3" in {
      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()
      val binding = http.bindAndHandleAsync(asyncHttpSuccessFunction, host, port, serverSettings, connectionContext, 1, loggingAdapter, materializer)

      val response = http.singleRequest(HttpRequest.create(s"http://$host:$port/").withMethod(HttpMethods.GET), materializer)

      response.toCompletableFuture.get(1, TimeUnit.SECONDS)
      binding.toCompletableFuture.get(1, TimeUnit.SECONDS).unbind()
    }

    "have serverLayer methods" in {
      // compile only for now
      pending

      http.serverLayer(materializer)

      val serverSettings = ServerSettings.create(system)
      http.serverLayer(serverSettings, materializer)

      val remoteAddress = Optional.empty[InetSocketAddress]()
      http.serverLayer(serverSettings, remoteAddress, materializer)

      val loggingAdapter = NoLogging
      http.serverLayer(serverSettings, remoteAddress, loggingAdapter, materializer)
    }

    "create a cached connection pool 1" in {
      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()
      val poolFlow: Flow[Pair[HttpRequest, NotUsed], Pair[Try[HttpResponse], NotUsed], HostConnectionPool] =
        http.cachedHostConnectionPool[NotUsed](ConnectHttp.toHost(host, port), materializer)

      val pair: Pair[HostConnectionPool, CompletionStage[Pair[Try[HttpResponse], NotUsed]]] =
        Source.single(new Pair(HttpRequest.GET(s"http://$host:$port/"), NotUsed.getInstance()))
          .viaMat(poolFlow, Keep.right[NotUsed, HostConnectionPool])
          .toMat(
            Sink.head(),
            Keep.both[HostConnectionPool, CompletionStage[Pair[Try[HttpResponse], NotUsed]]])
          .run(materializer)

      val response = pair.second.toCompletableFuture.get(1, TimeUnit.SECONDS)
    }

    "create a cached connection pool 2" in {
      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()

      val poolFlow: Flow[Pair[HttpRequest, NotUsed], Pair[Try[HttpResponse], NotUsed], HostConnectionPool] =
        http.cachedHostConnectionPool[NotUsed](ConnectHttp.toHost(host, port), poolSettings, loggingAdapter, materializer)

      val pair: Pair[HostConnectionPool, CompletionStage[Pair[Try[HttpResponse], NotUsed]]] =
        Source.single(new Pair(HttpRequest.GET("/"), NotUsed.getInstance()))
          .viaMat(poolFlow, Keep.right[NotUsed, HostConnectionPool])
          .toMat(
            Sink.head(),
            Keep.both[HostConnectionPool, CompletionStage[Pair[Try[HttpResponse], NotUsed]]])
          .run(materializer)

      val response = pair.second.toCompletableFuture.get(1, TimeUnit.SECONDS)
    }

    "create a cached connection pool 3" in {
      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()
      val poolFlow: Flow[Pair[HttpRequest, NotUsed], Pair[Try[HttpResponse], NotUsed], HostConnectionPool] =
        http.cachedHostConnectionPool[NotUsed](s"$host:$port", materializer)

      val pair: Pair[HostConnectionPool, CompletionStage[Pair[Try[HttpResponse], NotUsed]]] =
        Source.single(new Pair(HttpRequest.GET("/"), NotUsed.getInstance()))
          .viaMat(poolFlow, Keep.right[NotUsed, HostConnectionPool])
          .toMat(
            Sink.head(),
            Keep.both[HostConnectionPool, CompletionStage[Pair[Try[HttpResponse], NotUsed]]])
          .run(materializer)

      val response = pair.second.toCompletableFuture.get(1, TimeUnit.SECONDS)
    }

    "create a host connection pool 1" in {
      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()
      val poolFlow = http.newHostConnectionPool[NotUsed](ConnectHttp.toHost(host, port), materializer)

      val pair: Pair[HostConnectionPool, CompletionStage[Pair[Try[HttpResponse], NotUsed]]] =
        Source.single(new Pair(HttpRequest.GET("/"), NotUsed.getInstance()))
          .viaMat(poolFlow, Keep.right[NotUsed, HostConnectionPool])
          .toMat(
            Sink.head(),
            Keep.both[HostConnectionPool, CompletionStage[Pair[Try[HttpResponse], NotUsed]]])
          .run(materializer)

      val response = pair.second.toCompletableFuture.get(1, TimeUnit.SECONDS)
    }

    "create a host connection pool 2" in {
      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()
      val poolFlow = http.newHostConnectionPool[NotUsed](ConnectHttp.toHost(host, port), poolSettings, loggingAdapter, materializer)

      val pair: Pair[HostConnectionPool, CompletionStage[Pair[Try[HttpResponse], NotUsed]]] =
        Source.single(new Pair(HttpRequest.GET("/"), NotUsed.getInstance()))
          .viaMat(poolFlow, Keep.right[NotUsed, HostConnectionPool])
          .toMat(
            Sink.head(),
            Keep.both[HostConnectionPool, CompletionStage[Pair[Try[HttpResponse], NotUsed]]])
          .run(materializer)

      val response = pair.second.toCompletableFuture.get(1, TimeUnit.SECONDS)
    }

    "allow access to the default client https context" in {
      http.defaultClientHttpsContext.isSecure should not equal (null)
    }

    "allow access to the default server https context" in {
      http.defaultServerHttpContext.isSecure should not equal (null)
    }

    "have client layer methods" in {
      pending
      http.clientLayer(headers.Host.create("example.com"))
      val connectionSettings = ClientConnectionSettings.create(system)
      http.clientLayer(headers.Host.create("example.com"), connectionSettings)
      http.clientLayer(headers.Host.create("example.com"), connectionSettings, loggingAdapter)
    }

    "create an outgoing connection 1" in {
      // this one cannot be tested because it wants to run on port 80
      pending
      val flow = http.outgoingConnection("example.com")
    }

    "create an outgoing connection 2" in {
      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()
      val server = http.bindAndHandleSync(httpSuccessFunction, host, port, materializer)
      val binding = server.toCompletableFuture.get(1, TimeUnit.SECONDS)

      val flow = http.outgoingConnection(ConnectHttp.toHost(host, port))

      val response = Source.single(HttpRequest.GET("/").addHeader(headers.Host.create(host, port)))
        .via(flow)
        .toMat(Sink.head(), Keep.right[NotUsed, CompletionStage[HttpResponse]])
        .run(materializer)

      response.toCompletableFuture.get(1, TimeUnit.SECONDS).status() should be(StatusCodes.OK)
      binding.unbind()
    }

    "create an outgoing connection 3" in {
      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()
      val server = http.bindAndHandleSync(httpSuccessFunction, host, port, materializer)
      val binding = server.toCompletableFuture.get(1, TimeUnit.SECONDS)
      val flow = http.outgoingConnection(ConnectHttp.toHost(host, port))

      val response = Source.single(HttpRequest.GET(s"http://$host:$port/").addHeader(headers.Host.create(host, port)))
        .via(flow)
        .toMat(Sink.head(), Keep.right[NotUsed, CompletionStage[HttpResponse]])
        .run(materializer)

      response.toCompletableFuture.get(1, TimeUnit.SECONDS).status() should be(StatusCodes.OK)
      binding.unbind()
    }

    "allow a single request 1" in {
      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()
      val server = http.bindAndHandleSync(httpSuccessFunction, host, port, materializer)

      val response = http.singleRequest(HttpRequest.GET(s"http://$host:$port/"), materializer)

      response.toCompletableFuture.get(1, TimeUnit.SECONDS).status() should be(StatusCodes.OK)
      server.toCompletableFuture.get(1, TimeUnit.SECONDS).unbind()
    }

    "allow a single request 2" in {
      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()
      val server = http.bindAndHandleSync(httpSuccessFunction, host, port, materializer)

      val response = http.singleRequest(HttpRequest.GET(s"http://$host:$port/"), http.defaultClientHttpsContext, materializer)

      response.toCompletableFuture.get(1, TimeUnit.SECONDS).status() should be(StatusCodes.OK)
      server.toCompletableFuture.get(1, TimeUnit.SECONDS).unbind()
    }

    "allow a single request 3" in {
      val (_, host, port) = TestUtils.temporaryServerHostnameAndPort()
      val server = http.bindAndHandleSync(httpSuccessFunction, host, port, materializer)

      val response = http.singleRequest(HttpRequest.GET(s"http://$host:$port/"), http.defaultClientHttpsContext, poolSettings, loggingAdapter, materializer)

      response.toCompletableFuture.get(1, TimeUnit.SECONDS).status() should be(StatusCodes.OK)
      server.toCompletableFuture.get(1, TimeUnit.SECONDS).unbind()
    }

  }

  override protected def afterAll(): Unit = {
    materializer.shutdown()
    system.terminate()
  }
}
