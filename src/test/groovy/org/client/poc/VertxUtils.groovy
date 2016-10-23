package org.client.poc

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.vertx.core.Future
import io.vertx.core.Verticle
import io.vertx.core.Vertx

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

trait VertxUtils {
    Vertx vertx

    def <T> T block(Future<T> f, long timeout = 2000) {
        def cf = new CompletableFuture<T>()
        f.setHandler {
            if (it.succeeded()) cf.complete(it.result())
            else cf.completeExceptionally(it.cause())
        }

        try {
            cf.get(timeout, TimeUnit.MILLISECONDS)
        } catch (ExecutionException e) {
            throw e.cause
        }
    }

    def <T> T block(@ClosureParams(value=SimpleType, options = "io.vertx.core.Future") Closure c) {
        Future<T> f = Future.future()
        c.call(f)
        block(f)
    }

    String deploy(Verticle verticle) {
        block {
            vertx.deployVerticle(verticle, it.completer())
        }
    }
}
