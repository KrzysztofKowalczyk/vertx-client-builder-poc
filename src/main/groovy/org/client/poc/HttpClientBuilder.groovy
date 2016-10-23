package org.client.poc

import groovy.transform.CompileStatic
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpClientResponse
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject

import java.util.function.BiConsumer
import java.util.function.Consumer


/**
 * This is not meant to be a proper implementation. It only shows how I see the API.
 */
@CompileStatic
@Builder(builderStrategy = SimpleStrategy, prefix = "with")
class HttpClientBuilder {
    Vertx vertx
    int port

    // TODO: version with HttpClientOptions
    static HttpClientBuilder httpClient(Vertx vertx) {
        new HttpClientBuilder().withVertx(vertx)
    }

    RequestExecutor<String> returningBodyAsString(){
        returning { Future<String> result, HttpClientResponse response ->
            response.exceptionHandler(result.&fail)
            response.bodyHandler{ Buffer buffer -> result.complete(buffer?.toString())}
        }
    }

    //FIXME: not happy with the name
    // I could go even with CompletedResponse<JsonObject> -> returningCompletedJsonResponse?
    // for now I leave bodyAsJson on response itself
    RequestExecutor<CompletedResponse> returningCompletedResponse(){
        returning { Future<CompletedResponse> result, HttpClientResponse response ->
            response.exceptionHandler(result.&fail)
            response.bodyHandler{ Buffer buffer ->
                result.complete(new CompletedResponse(response, buffer))
            }
        }
    }

    RequestExecutor<JsonObject> returningBodyAsJsonObject(){
        returning { Future<JsonObject> result, HttpClientResponse response ->
            response.exceptionHandler(result.&fail)
            response.bodyHandler{ Buffer buffer -> result.complete(buffer?.toJsonObject())}
        }
    }

    // I can still return HttpClientResponse
    RequestExecutor<HttpClientResponse> returningResponse(){
        returning { Future<HttpClientResponse> result, HttpClientResponse response ->
            result.complete(response)
        }
    }

    // or buffer
    RequestExecutor<Buffer> returningBody(){
        returning { Future<Buffer> result, HttpClientResponse response ->
            response.exceptionHandler(result.&fail)
            response.bodyHandler(result.&complete)
        }
    }

    // or model
    def <T> RequestExecutor<T> returning(Class<T> clazz){
        returning { Future<T> result, HttpClientResponse response ->
            response.exceptionHandler(result.&fail)
            response.bodyHandler{ buffer -> result.complete(Json.decodeValue(buffer.toString(), clazz))}
        }
    }

    def <T> RequestExecutor<T> returning(BiConsumer<Future<T>, HttpClientResponse> strategy){
        new RequestExecutor(buildClient(), strategy)
    }

    private HttpClient buildClient(){
        vertx.createHttpClient(new HttpClientOptions().setDefaultPort(port))
    }
}
