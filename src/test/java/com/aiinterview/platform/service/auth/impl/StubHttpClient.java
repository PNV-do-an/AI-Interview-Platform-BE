package com.aiinterview.platform.service.auth.impl;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * HttpClient giả cho test OAuth/OIDC: trả lần lượt các response đã xếp hàng và ghi lại request.
 *
 * <p>Không dùng Mockito vì {@code send} là generic — stub bằng tay đọc dễ hơn và còn assert được
 * thứ tự gọi (GitHub gọi 2-3 endpoint theo trình tự). Không phải class {@code *Test} nên Surefire
 * bỏ qua; phải khai báo trong SourceList của script test.</p>
 *
 * <p>Response và lỗi nằm chung một hàng đợi, nên {@link #failNextWith} nghĩa đúng là "lời gọi HTTP
 * kế tiếp sẽ ném lỗi này" — muốn lỗi ở lời gọi thứ 2 thì enqueue response thứ nhất trước.</p>
 */
final class StubHttpClient extends HttpClient {

    private final Deque<Object> queue = new ArrayDeque<>();
    private final List<HttpRequest> requests = new ArrayList<>();

    StubHttpClient enqueue(int status, String body) {
        queue.add(new StubResponse(status, body));
        return this;
    }

    StubHttpClient failNextWith(IOException failure) {
        queue.add(new StubFailure(failure));
        return this;
    }

    List<String> requestUris() {
        return requests.stream().map(request -> request.uri().toString()).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException {
        requests.add(request);
        Object next = queue.poll();
        if (next instanceof StubFailure failure) {
            throw failure.cause();
        }
        if (next == null) {
            throw new IOException("StubHttpClient: khong con response cho " + request.uri());
        }
        StubResponse response = (StubResponse) next;
        return (HttpResponse<T>) new StubResponseHttp(request, response.status(), response.body());
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        throw new UnsupportedOperationException("sendAsync khong dung trong unit test");
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request,
            HttpResponse.BodyHandler<T> handler,
            HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        throw new UnsupportedOperationException("sendAsync khong dung trong unit test");
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return Optional.empty();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return Optional.of(Duration.ofSeconds(10));
    }

    @Override
    public Redirect followRedirects() {
        return Redirect.NEVER;
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return Optional.empty();
    }

    @Override
    public SSLContext sslContext() {
        return null;
    }

    @Override
    public SSLParameters sslParameters() {
        return new SSLParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return Optional.empty();
    }

    @Override
    public Version version() {
        return Version.HTTP_1_1;
    }

    @Override
    public Optional<Executor> executor() {
        return Optional.empty();
    }

    private record StubResponse(int status, String body) {
    }

    private record StubFailure(IOException cause) {
    }

    private record StubResponseHttp(HttpRequest request, int status, String body)
            implements HttpResponse<String> {

        @Override
        public int statusCode() {
            return status;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, value) -> true);
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
