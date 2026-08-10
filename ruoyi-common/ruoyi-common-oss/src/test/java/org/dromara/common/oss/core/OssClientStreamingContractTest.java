package org.dromara.common.oss.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.dromara.common.oss.entity.UploadResult;
import org.dromara.common.oss.exception.OssException;
import org.dromara.common.oss.properties.OssProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class OssClientStreamingContractTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void inputStreamUploadStartsObjectStoreRequestBeforeReadingWholeSource() throws Exception {
        AtomicBoolean requestStarted = new AtomicBoolean();
        server = server(exchange -> {
            requestStarted.set(true);
            exchange.getRequestBody().transferTo(OutputStream.nullOutputStream());
            exchange.getResponseHeaders().add("ETag", "\"streamed-etag\"");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        OssClient client = clientFor(server);
        InputStream source = new RequestAwareInputStream(2L * 1024 * 1024, 256 * 1024, requestStarted);

        UploadResult result = client.upload(source, "uploads/stream.bin", 2L * 1024 * 1024,
            "application/octet-stream");

        assertThat(result.getETag()).isEqualTo("\"streamed-etag\"");
        assertThat(requestStarted).isTrue();
    }

    @Test
    void inputStreamUploadWaitsForObjectStoreCompletion() throws Exception {
        CountDownLatch requestReceived = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);
        server = server(exchange -> {
            exchange.getRequestBody().transferTo(OutputStream.nullOutputStream());
            requestReceived.countDown();
            await(allowCompletion);
            exchange.getResponseHeaders().add("ETag", "\"completed-etag\"");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        OssClient client = clientFor(server);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<UploadResult> upload = CompletableFuture.supplyAsync(
                () -> client.upload(InputStream.nullInputStream(), "uploads/empty.bin", 0L,
                    "application/octet-stream"), executor);

            assertThat(requestReceived.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(upload).isNotDone();
            allowCompletion.countDown();
            assertThat(upload.get(5, TimeUnit.SECONDS).getETag()).isEqualTo("\"completed-etag\"");
        } finally {
            allowCompletion.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void inputStreamUploadPropagatesObjectStoreFailure() throws Exception {
        server = server(exchange -> respondWithError(exchange, 403, "store unavailable"));
        OssClient client = clientFor(server);

        assertThatThrownBy(() -> client.upload(InputStream.nullInputStream(), "uploads/fail.bin", 0L,
            "application/octet-stream"))
            .isInstanceOf(OssException.class)
            .hasMessageContaining("上传文件失败");
    }

    @Test
    void uploadLargerThan32MiBUsesAutomaticMultipart() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        AtomicInteger partNumber = new AtomicInteger();
        server = server(exchange -> {
            String requestQuery = exchange.getRequestURI().getRawQuery();
            if ("POST".equals(exchange.getRequestMethod()) && requestQuery != null
                && requestQuery.contains("uploads")) {
                method.compareAndSet(null, exchange.getRequestMethod());
                query.compareAndSet(null, requestQuery);
                respondWithMultipartUpload(exchange);
            } else if ("PUT".equals(exchange.getRequestMethod()) && requestQuery != null
                && requestQuery.contains("partNumber")) {
                exchange.getRequestBody().transferTo(OutputStream.nullOutputStream());
                exchange.getResponseHeaders().add("ETag", "\"part-" + partNumber.incrementAndGet() + "\"");
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            } else if ("POST".equals(exchange.getRequestMethod()) && requestQuery != null
                && requestQuery.contains("uploadId")) {
                exchange.getRequestBody().transferTo(OutputStream.nullOutputStream());
                respondWithCompletedMultipartUpload(exchange);
            } else {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            }
        });
        OssClient client = clientFor(server);
        long length = 32L * 1024 * 1024 + 1;

        UploadResult result = client.upload(new ZeroInputStream(length), "uploads/large.bin", length,
            "application/octet-stream");

        assertThat(method).hasValue("POST");
        assertThat(query.get()).contains("uploads");
        assertThat(partNumber).hasValue(2);
        assertThat(result.getETag()).isEqualTo("\"complete-etag\"");
    }

    private static HttpServer server(ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler::handle);
        server.start();
        return server;
    }

    private static OssClient clientFor(HttpServer server) {
        OssProperties properties = new OssProperties();
        properties.setAccessKey("access-key");
        properties.setSecretKey("secret-key");
        properties.setEndpoint("127.0.0.1:" + server.getAddress().getPort());
        properties.setBucketName("attachments");
        properties.setIsHttps("N");
        return new OssClient("test", properties);
    }

    private static void respondWithError(HttpExchange exchange, int status, String message) throws IOException {
        byte[] response = ("<Error><Code>AccessDenied</Code><Message>" + message
            + "</Message></Error>").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/xml");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static void respondWithMultipartUpload(HttpExchange exchange) throws IOException {
        byte[] response = ("<InitiateMultipartUploadResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">"
            + "<Bucket>attachments</Bucket><Key>uploads/large.bin</Key><UploadId>test-upload</UploadId>"
            + "</InitiateMultipartUploadResult>").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/xml");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static void respondWithCompletedMultipartUpload(HttpExchange exchange) throws IOException {
        byte[] response = ("<CompleteMultipartUploadResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">"
            + "<Location>http://localhost/attachments/uploads/large.bin</Location>"
            + "<Bucket>attachments</Bucket><Key>uploads/large.bin</Key><ETag>\"complete-etag\"</ETag>"
            + "</CompleteMultipartUploadResult>").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/xml");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IOException("timed out waiting for test completion");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting for test completion", e);
        }
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private static class ZeroInputStream extends InputStream {

        private final long length;
        private long position;

        private ZeroInputStream(long length) {
            this.length = length;
        }

        @Override
        public int read() {
            return position++ < length ? 0 : -1;
        }

        @Override
        public int read(byte[] bytes, int offset, int requestedLength) {
            if (position >= length) {
                return -1;
            }
            int count = (int) Math.min(requestedLength, length - position);
            position += count;
            return count;
        }
    }

    private static final class RequestAwareInputStream extends ZeroInputStream {

        private final long readLimitBeforeRequest;
        private final AtomicBoolean requestStarted;
        private long bytesRead;

        private RequestAwareInputStream(long length, long readLimitBeforeRequest, AtomicBoolean requestStarted) {
            super(length);
            this.readLimitBeforeRequest = readLimitBeforeRequest;
            this.requestStarted = requestStarted;
        }

        @Override
        public int read() {
            checkRequestStarted();
            int value = super.read();
            if (value >= 0) {
                bytesRead++;
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int requestedLength) {
            checkRequestStarted();
            int count = super.read(bytes, offset, requestedLength);
            if (count > 0) {
                bytesRead += count;
            }
            return count;
        }

        private void checkRequestStarted() {
            if (!requestStarted.get() && bytesRead >= readLimitBeforeRequest) {
                throw new AssertionError("upload source was buffered before the object-store request started");
            }
        }
    }
}
