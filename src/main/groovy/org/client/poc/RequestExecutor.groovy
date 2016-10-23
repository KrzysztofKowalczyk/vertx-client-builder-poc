package org.client.poc

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.vertx.core.Future
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientRequest
import io.vertx.core.http.HttpClientResponse
import io.vertx.core.http.HttpMethod

import java.util.function.BiConsumer

@CompileStatic
@Canonical
class RequestExecutor<T> {
    HttpClient client
    BiConsumer<Future<T>, HttpClientResponse> responseHandler

    Future<T> get(String path) {
        Future<T> result = Future.future()
        def request = client.get(path)
        request.exceptionHandler(result.&fail)
        request.handler { response ->
            responseHandler.accept(result, response)
        }
        request.end()

        result
    }
}
