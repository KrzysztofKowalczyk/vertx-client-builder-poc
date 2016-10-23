package org.client.poc

import groovy.transform.CompileStatic
import groovy.transform.Immutable
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpClientRequest
import io.vertx.core.http.HttpClientResponse
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import rx.Single
import rx.SingleSubscriber

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
    boolean chunked

    private HttpClient buildClient(){
        vertx.createHttpClient(new HttpClientOptions()
            .setDefaultPort(port)
        )
    }

    // TODO: version with HttpClientOptions
    static HttpClientBuilder httpClient(Vertx vertx) {
        new HttpClientBuilder().withVertx(vertx)
    }

    RequestExecutor<Future<String>> returningBodyAsString(){
        returning { Future<String> result, HttpClientResponse response ->
            response.exceptionHandler(result.&fail)
            response.bodyHandler{ Buffer buffer -> result.complete(buffer?.toString())}
        }
    }

    //FIXME: not happy with the name
    // I could go even with CompletedResponse<JsonObject> -> returningCompletedJsonResponse?
    // for now I leave bodyAsJson on response itself
    RequestExecutor<Future<CompletedResponse>> returningCompletedResponse(){
        returning { Future<CompletedResponse> result, HttpClientResponse response ->
            response.exceptionHandler(result.&fail)
            response.bodyHandler{ Buffer buffer ->
                result.complete(new CompletedResponse(response, buffer))
            }
        }
    }

    RequestExecutor<Future<JsonObject>> returningBodyAsJsonObject(){
        returning { Future<JsonObject> result, HttpClientResponse response ->
            response.exceptionHandler(result.&fail)
            response.bodyHandler{ Buffer buffer -> result.complete(buffer?.toJsonObject())}
        }
    }

    // I can still return HttpClientResponse
    RequestExecutor<Future<HttpClientResponse>> returningResponse(){
        returning { Future<HttpClientResponse> result, HttpClientResponse response ->
            result.complete(response)
        }
    }

    // or buffer
    RequestExecutor<Future<Buffer>> returningBody(){
        returning { Future<Buffer> result, HttpClientResponse response ->
            response.exceptionHandler(result.&fail)
            response.bodyHandler(result.&complete)
        }
    }

    // or model
    def <T> RequestExecutor<Future<T>> returning(Class<T> clazz){
        returning { Future<T> result, HttpClientResponse response ->
            response.exceptionHandler(result.&fail)
            response.bodyHandler{ buffer -> result.complete(Json.decodeValue(buffer.toString(), clazz))}
        }
    }

    // This would much simpler if I would decide that RequestExecutor only support one type of async handling - i.e. Future
    // See comment below.
    def <T> RequestExecutor<Future<T>> returning(BiConsumer<Future<T>, HttpClientResponse> strategy){
        new RequestExecutor(buildClient(), { HttpClientRequest request, BiConsumer<HttpClientRequest, Consumer<Throwable>> bodyRequestWriter ->
            def result = Future.future();

            try{
                request.exceptionHandler(result.&fail)
                request.handler { HttpClientResponse response ->
                    strategy.accept(result, response)
                }
                bodyRequestWriter.accept(request, result.&fail as Consumer)
            } catch (Throwable t) {
                result.fail(t)
            }

            return result
        })
    }

    //
    // Rx Single
    //
    // For now I did everything in one type. Having one type for RequestExecutor is possible,
    // it limits repetition but increase complexity. Most likely I could make all those methods
    // significantly simpler by having different types for different async approaches.
    // Support for post with future body would be good to expose problems with design.
    //
    // I'm using RxJava 1 Single.create but I don't check for unsubscription. Final code should.

    // Not sure about the naming
    RequestExecutor<Single<HttpClientResponse>> returningResponseSingle() {
        new RequestExecutor(buildClient(), this.&responseSingle)
    }

    RequestExecutor<Single<CompletedResponse>> returningCompletedResponseSingle() {
        new RequestExecutor(buildClient(), this.&completedResponseSingle)
    }

    RequestExecutor<Single<String>> returningBodyAsStringSingle() {
        new RequestExecutor(buildClient(), this.&stringSingle)
    }

    RequestExecutor<Single<JsonObject>> returningBodyAsJsonObjectSingle() {
        new RequestExecutor(buildClient(), this.&jsonObjectSingle)
    }

    def <T> RequestExecutor<Single<T>> returningSingle(Class<T> type) {
        new RequestExecutor(buildClient(), { HttpClientRequest request, BiConsumer<HttpClientRequest, Consumer<Throwable>> bodyRequestWriter ->
            typeSingle(request, bodyRequestWriter, type)
        })
    }

    private Single<HttpClientResponse> responseSingle(HttpClientRequest request, BiConsumer<HttpClientRequest, Consumer<Throwable>> bodyRequestWriter) {
        Single.create( { SingleSubscriber<HttpClientResponse> result ->
            try {
                request.exceptionHandler(result.&onError)
                request.handler { HttpClientResponse response ->
                    result.onSuccess(response)
                }
                bodyRequestWriter.accept(request, result.&onError as Consumer)
            } catch (Throwable t) {
                result.onError(t)
            }
        } as Single.OnSubscribe)
    }

    private Single<CompletedResponse> completedResponseSingle(HttpClientRequest request, BiConsumer<HttpClientRequest, Consumer<Throwable>> bodyRequestWriter) {
        responseSingle(request, bodyRequestWriter).flatMap { HttpClientResponse response ->
            Single.create( { SingleSubscriber<CompletedResponse> result ->
                response.exceptionHandler(result.&onError)
                response.bodyHandler { Buffer responseBody ->
                    result.onSuccess(new CompletedResponse(response, responseBody))
                }
            } as Single.OnSubscribe)
        }
    }

    private Single<String> stringSingle(HttpClientRequest request, BiConsumer<HttpClientRequest, Consumer<Throwable>> bodyRequestWriter) {
        completedResponseSingle(request, bodyRequestWriter).map { it.bodyAsString() }
    }

    private Single<JsonObject> jsonObjectSingle(HttpClientRequest request, BiConsumer<HttpClientRequest, Consumer<Throwable>> bodyRequestWriter) {
        completedResponseSingle(request, bodyRequestWriter).map { it.bodyAsJsonObject() }
    }

    private <T> Single<T> typeSingle(HttpClientRequest request, BiConsumer<HttpClientRequest, Consumer<Throwable>> bodyRequestWriter, Class<T> type) {
        completedResponseSingle(request, bodyRequestWriter).map { CompletedResponse resp -> Json.decodeValue(resp.bodyAsString(), type)}
    }
}
