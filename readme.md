### Proposal for Vert.x Http client builders 
  
See [ClientSpec](src/test/groovy/org/client/poc/ClientSpec.groovy) for working prototype.

The main idea is that there are 2 steps and at least 2 types. One that allow to specify options:

```
HttpClientBuilder builder = HttpClientBuilder
   .httpClient(vertx)
   .withHost("localhost")
   .withPort(port)
   .withPipelining(true)
   .withKeepAlive(true)
   .withHeaders("Accept", "text/json", "TraceId","1")
   //...
```

First one should be immutable (Copy on Write) and expose all settings from HttpClientOptions,
plus methods that define the return type of actual call and some extra methods like common headers or auth.
It should be side effect free. 
It could be made serializable if vertx would be provided as parameter of last method not the first one.
There should be starting point for Vertx, HttpClientOptions and HttpClient. 
I think the one for HttpClient should have limited settings to those that can still be changed.
s  
First type eventually return second type - the request executor:

```
RequestExecutor<String> stringCall = builder.returningBodyAsString()
RequestExecutor<CompletedResponse> responseCall = builder.returningCompletedResponse()
RequestExecutor<Book> modelCall = builder.returning(Book.class)
RequestExecutor<HttpClientResponse> classicCall = builder.returningResponse()
```

Second type allow to do actual call and return expected response as per specification.
```
Future<String> responseString = stringCall.get("/")
assert block(responseString) == "Hello"

Future<CompletedResponse> response = responseCall.get("/")
CompletedResponse r = block(response)

assert r.statusCode == 200
assert r.bodyAsString == "Hello"
```

This way once the executor is created, doing rest on any API is trivial. 

I chose to return Future, but concept would work the same if get is taking 2 Handlers or AsyncResult.
Samve for returning RxJava Single, CompletableFuture etc. RxJava observable should work nice with multipart post.

What next:
- Check how it would look like if we provide Vert.x at the end not at the start of building?
- Support for async body - post("/", futureBody)
- Support for RxJava Single
- Support for multipart request taking Observable

Language specific (Groovy) ?:
- Support map - typical for dynamic groovy
- Build with closure (client(vertx) { port 8080; headers hello: "world" }) - static typed

Boring stuff:
- Sending headers, query params from request methods (get(path, params, headers)) - support for map and multimap
- Setting default headers on builder
- Explicit support for authentication, i.e. withBasicAuth(user, password), get("/", basicAuth(user, password))
