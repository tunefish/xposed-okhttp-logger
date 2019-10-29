# Xposed Logger Module for OkHttp
This is a simple module for the [Xposed Framework](https://xposed.info) that logs all HTTP(S) requests made via [OkHttp](https://square.github.io/okhttp/). Supports all applications which are not using source code obfuscation (specifically, name mangling as done by e.g. [ProGuard](https://www.guardsquare.com/en/products/proguard)).


# Building
Built with gradle and most recent version of Android SDK build tooks v29.0.0 (as of 2019/10):

`./gradlew build`

The module's only dependency is Xposed Bridge.
Once installed, it should show up as `RequestLogger` in the Xposed Installer.

Tested on an Android Oreo 8.0.0 virtual device (x86) with Xposed Framework v90-beta3.


# Internals
There are several points during the lifetime of a OkHttp request at which we can hook in order to log each HTTP(S) request:
 * Hook **`okhttp3.Request` constructor or `okhttp3.Request.Builder` methods**: might yield some false positives, since Request objects can be created without issuing an HTTP call. Further, `okhttp3.Request` objects can be (re)created several times by interceptors before the actual request is issued, leading to duplicates.
 * Hook **`okhttp3.OkHttpClient.newCall(Request r)`**: similar to above, but guarantees to log each request only once.
 * Hook **internal implementations of `okhttp3.Call.execute()` and `okhttp3.Call.enqueue(Callback c)` by `okhttp3.RealCall`**: logs all HTTP(s) requests only when they are scheduled to be executed. This also includes calls that were only scheduled but not executed because the app terminated before the call was issued.
 * Hook **`okhttp3.Response` constructor or `okhttp3.Builder` methods**: has the same effect as above option, but only logs when a request is finished (successfully or not). Further, similar to the first option, `okhttp3.Response` objects can be (re)created several times after a request has been issued, leading to duplicates.

This module implements the 3rd option, hooking `okhttp3.RealCall.execute()` and `okhttp3.RealCall.enqueue(Callback c)` since it yields the most accurate results and no duplicates.
The same instance of `tunefish.requestlogger.RequestLogger` is installed for both methods.
It obtains the `okhttp3.Request` object from the issued call by calling `.request()` on the call.
The URL of the request can then be read from the `url` field of the request object.

All detected requests are logged with timestamps in a logfile `_requests.log` in the private storage of each application.


# Tricking this module
After a `RealCall` instance is created from a `Request` object using `okhttp3.OkHttpClient.newCall(Request r)`, interceptors registered with the client are able to transform the call before it is sent on the network (see [Interceptors Documentation](https://github.com/square/okhttp/blob/master/docs/interceptors.md)).
This means that an interceptor could change the requested URL after a `RealCall` has been created and before the request is sent.
This could be abused to make this module log a different URL than the one which is actually requested.
The only way to prevent this is to inject a network interceptor after the last user-supplied network interceptor and before the [`okhttp3.internal.http.CallServerInterceptor`](https://github.com/square/okhttp/blob/eee838aebe8d3524d7e0e2dbf8f9bf357512f038/okhttp/src/main/java/okhttp3/internal/http/CallServerInterceptor.kt), which issues the actual request on the network. (see [relevant code, lines 176-197](https://github.com/square/okhttp/blob/16173e2af93fe69ac39787e0e5d22649ff264cff/okhttp/src/main/java/okhttp3/RealCall.kt#L176)).
Alternatively to injecting a network interceptor, one could also hook the `okhttp3.internal.http.RealInterceptorChain.proceed` methods to log the request when the `CallServerInterceptor` is detected.

If requests served from OkHttp's cache should be logged as well, one also needs to log requested URLs between the last user-supplied application interceptor and the OkHttp core.
