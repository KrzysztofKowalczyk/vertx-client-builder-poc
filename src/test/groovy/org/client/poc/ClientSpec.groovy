package org.client.poc

import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpClientRequest
import io.vertx.core.json.JsonObject
import spock.lang.Specification

import java.util.concurrent.TimeoutException

class ClientSpec extends Specification implements VertxUtils {

    def port = 8080
    def wrongPort = 8091

    def setup() {
        vertx = Vertx.vertx()
        deploy(new Server())
    }

    def cleanup() {
        block {
            vertx.close(it.completer())
        }
    }

    def "current vertx - client.get"() {
        given:
        HttpClient client = vertx.createHttpClient(new HttpClientOptions().setDefaultPort(port))

        when: "Simple get"

        Future<String> body = Future.future()
        HttpClientRequest request = client.get("/")
        request.exceptionHandler(body.&fail)
        request.handler { response ->
            response.exceptionHandler(body.&fail)
            response.bodyHandler{ buffer -> body.complete(buffer.toString()) }
        }
        request.end()

        then:
        block(body) == "Hello"
    }

    def "current vertx - client.getNow"() {
        given:
        HttpClient client = vertx.createHttpClient(new HttpClientOptions().setDefaultPort(port))

        when: "Simple getNow"

        Future<String> body = Future.future()
        client.getNow("/") {  response ->
            response.exceptionHandler(body.&fail)
            response.bodyHandler{ buffer -> body.complete(buffer.toString()) }
        }

        then:
        block(body) == "Hello"
    }

    def "current vertx - client.getNow never responds if request fails"() {
        given:
        HttpClient client = vertx.createHttpClient(new HttpClientOptions()
            .setDefaultPort(wrongPort)
        )

        when: "getNow will never return"

        Future<String> body = Future.future()
        client.getNow("/") {  response ->
            response.exceptionHandler(body.&fail)
            response.bodyHandler{ buffer -> body.complete(buffer.toString()) }
        }

        and: "wait for result"
        block(body)

        then: "connection refused is not thrown :("
        thrown TimeoutException

        // There is connection refused, but this way we never get it and handler never completes
        // You can see the exception in logs: SEVERE: io.netty.channel.AbstractChannel$AnnotatedConnectException: Connection refused: localhost/127.0.0.1:8091
        // getNow and others should be deprecated.
    }

    //
    // Prototype: decide on return type before doing request and deciding on method
    //

    def "builder - get string response"() {
        given:
        HttpClientBuilder builder = HttpClientBuilder
            .httpClient(vertx)
            .withPort(port)

        RequestExecutor<String> executor = builder
            .returningBodyAsString()

        when:
        Future<String> body = executor.get("/")

        then:
        block(body) == "Hello"
    }

    def "builder - get string response with status code and others"() {
        given:
        def builder = HttpClientBuilder.httpClient(vertx)
            .withPort(port)
            .returningCompletedResponse()

        when:
        CompletedResponse response = block builder.get("/")

        then:
        response.statusCode == 200
        response.bodyAsString() == "Hello"
        response.statusMessage == "OK"
    }

    def "builder - request failed - exception is propagated"() {
        given:
        def builder = HttpClientBuilder.httpClient(vertx)
            .withPort(wrongPort)
            .returningBodyAsString()

        when:
        block builder.get("/")

        then:
        Exception e = thrown()
        e.message.contains "Connection refused"
    }

    def "builder - get json"() {
        given:
        def builder = HttpClientBuilder.httpClient(vertx)
            .withPort(port)
            .returningBodyAsJsonObject()

        when:
        def result = block builder.get("/json")

        then:
        result instanceof JsonObject
        result.map.this.is == "JSON!"
    }

    def "builder - get class"() {
        given:
        def builder = HttpClientBuilder.httpClient(vertx)
            .withPort(port)
            .returning(Book)

        when:
        def result = block builder.get("/book")

        then:
        result instanceof Book
        result.title == "The Last Wish"
        result.author == "Andrzej Sapkowski"
    }
}
