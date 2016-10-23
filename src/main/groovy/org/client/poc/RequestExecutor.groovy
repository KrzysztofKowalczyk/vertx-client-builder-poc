package org.client.poc

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.vertx.core.Future
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientRequest
import io.vertx.core.http.HttpClientResponse
import io.vertx.core.http.HttpMethod
import rx.Observable
import rx.Single

import java.util.function.BiConsumer
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Function

@Canonical
@CompileStatic
class RequestExecutor<T> {
    final HttpClient client
    final BiFunction<HttpClientRequest, BiConsumer<HttpClientRequest, Consumer<Throwable>>, T> requestHandler

    T get(String path) {
        requestHandler.apply(client.get(path), { HttpClientRequest req, errorHandler -> req.end() })
    }

    T post(String path) {
        requestHandler.apply(client.post(path), { HttpClientRequest req, errorHandler -> req.end() })
    }

    T post(String path, String body) {
        requestHandler.apply(client.post(path), { HttpClientRequest req, errorHandler -> req.end(body) })
    }


    T post(String path, Future<String> body) {
        requestHandler.apply(client.post(path), { HttpClientRequest req, Consumer<Throwable> errorHandler ->
            body.setHandler {
                if(it.succeeded()) {
                    req.end(it.result())
                } else {
                    errorHandler.accept(it.cause())
                }
            }
        })
    }

    T post(String path, Observable<String> body) {
        requestHandler.apply(client.post(path), { HttpClientRequest req, Consumer<Throwable> errorHandler ->
            req.setChunked(true)
            //TODO: should I protect it with observeOn ? This code may be invoked on some strange thread.
            body.subscribe (
                { req.write(it) },
                { errorHandler.accept(it) },
                { req.end() }
            )
        })
    }

    T post(String path, Single<String> body) {
        requestHandler.apply(client.post(path), { HttpClientRequest req, Consumer<Throwable> errorHandler ->
            //TODO: should I protect it with observeOn ? This code may be invoked on some strange thread.
            body.subscribe (
                { req.end(it) },
                { errorHandler.accept(it) },
            )
        })
    }
}
