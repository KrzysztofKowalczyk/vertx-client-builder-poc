package org.client.poc

import groovy.transform.CompileStatic
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext

import static io.vertx.ext.web.Router.router

@CompileStatic
class Server extends AbstractVerticle {

    @Override
    void start(Future startup)  {
        def r = router()
        vertx
            .createHttpServer()
            .requestHandler(r.&accept)
            .listen(8080, startup.completer())
    }

    Router router(){
        def r = router(vertx)
        r.get("/") handler { it.response().end "Hello" }
        r.get("/json") handler { it.response().end '{ "this": { "is": "JSON!" } }' }
        r.get("/book") handler { it.response().end """
            {
                "title" : "The Last Wish",
                "author": "Andrzej Sapkowski"
            }
        """ }
        r
    }
}
