### Proposal for Vert.x Http client builders 

```Groovy
Future<String> world = httpClient(vertx)
   .withHost("world.io")
   .returningBodyAsString()
   .get("/world")
   
Future<String> result = httpClient(vertx)
   .withHost("hello.io")
   .returningBodyAsString()
   .post("/hello", world)
   
Observable<String> multipart_body = Observable.just("a","b","c")
Single<String> rxjava = httpClient(vertx)
   .withPort(8080)
   .returningBodyAsStringSingle()
   .post("/", multipart_body)
```

Follow up to a discussion during Vert.x community meeting. Code written in Groovy for sanity and because lack of time, but the concept is Java compatible.

See **[ClientSpec](src/test/groovy/org/client/poc/ClientSpec.groovy)** for a working prototype in action.

The main idea is to have 2 steps and at least 2 types. First step should allow to specify options:

```Groovy
HttpClientBuilder builder = HttpClientBuilder
   .httpClient(vertx)
   .withHost("localhost")
   .withPort(port)
   .withPipelining(true)
   .withKeepAlive(true)
   .withHeaders("Accept", "text/json", "TraceId","1")
   //...
```

Type here should be immutable (Copy on Write) so it can act as template. It should:
- expose all settings from HttpClientOptions
- have some extra methods to define common headers or auth, base path etc.
- have methods that define the return type of actual call - those switch to next step

It should be side effect free. It could be made serializable if vertx would be provided
as parameter of last method not the first one.
There should be starting point for Vertx, HttpClientOptions and HttpClient. 
I think the one for HttpClient should have limited settings to those that can still be changed.

First type eventually switch to second stage by defining expected return type:

```Groovy
RequestExecutor<String> stringCall = builder.returningBodyAsString()
RequestExecutor<CompletedResponse> responseCall = builder.returningCompletedResponse()
RequestExecutor<Book> modelCall = builder.returning(Book.class)
RequestExecutor<HttpClientResponse> classicCall = builder.returningResponse()
```

Second type allow to do actual call and return expected response as per specification. It wraps a HttpClient instance.
Cool thing is that the whole second stage can be done as a single type with all REST verbs by passing strategy to it, see returning\* methods in [HttpClientBuilder](src/main/groovy/org/client/poc/HttpClientBuilder.groovy). It can support different types like RxJava Single, CompletableFuture without changes. Though splitting it to many types might make code simpler.

Once we have executor instance we can do the call and get future (or pass handlers)

```Groovy
Future<String> responseString = stringCall.get("/")
assert block(responseString) == "Hello"

Future<CompletedResponse> response = responseCall.get("/")
CompletedResponse r = block(response)

assert r.statusCode == 200
assert r.bodyAsString == "Hello"
```

This way once the executor is created, doing REST is trivial. 

I chose to return Future, but concept would work the same if get is taking 2 Handlers or AsyncResult.
Same for returning RxJava Single, CompletableFuture etc. RxJava observable should work nice with multipart post.

What next:
- Split rx out of main code
  - Start moving stuff to Java
  - Move rx code to specific class - create FutureExecutor, RxExecutor etc
  - ? Provide builder with parametrised return type for with\* methods ? 
  - ? RxBuilder as extension of main builder ?
- Support 3 stages, so one can construct client and then for the same client use different return types (String). Maybe getHttpClient() would be enough.
- Check how it would look like if we provide Vert.x at the end not at the start of building?


Language specific (Groovy) ?:
- Support map - typical for dynamic groovy
- Build and request with closure - static typed

```Groovy
HttpClientBuilder builder = HttpClientBuilder.httpClient(vertx) {
   host "localhost"
   port 8080
   pipelining true
   keepAlive true
   headers Accept: "text/json", TraceId: "1"
}
```   
   
Boring stuff:
- Sending headers, query params from request methods (get(path, params, headers)) - support for map and multimap
- Setting default headers on builder
- Explicit support for authentication, i.e. `withBasicAuth(user, password)` for one off calls and `get("/", basicAuth(user, password))` for recuring calls
