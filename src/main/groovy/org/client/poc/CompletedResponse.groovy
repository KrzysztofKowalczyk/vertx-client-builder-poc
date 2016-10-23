package org.client.poc

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.vertx.core.MultiMap
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClientResponse
import io.vertx.core.json.JsonObject

@CompileStatic
@Canonical
class CompletedResponse {
    Buffer body
    int statusCode
    String statusMessage
    MultiMap headers

    CompletedResponse(HttpClientResponse response, Buffer buffer) {
        body = buffer
        statusCode = response.statusCode()
        statusMessage = response.statusMessage()
        headers = response.headers()
        // ...
    }

    JsonObject bodyAsJsonObject() {
        body.toJsonObject()
    }

    String bodyAsString() {
        body.toString()
    }
 }
