package org.dromara.common.oss.core;

import org.dromara.common.oss.exception.OssException;
import org.dromara.common.oss.properties.OssProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class OssClientTest {

    @Test
    void deletePropagatesAsyncObjectStoreFailure() throws Exception {
        S3AsyncClient s3 = mock(S3AsyncClient.class);
        when(s3.deleteObject(anyDeleteRequest()))
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("async delete failed")));
        OssClient client = clientUsing(s3);

        assertThatThrownBy(() -> client.delete("http://localhost:9000/attachments/uploads/a.png"))
            .isInstanceOf(OssException.class)
            .hasMessageContaining("async delete failed");
    }

    @Test
    void deleteWaitsForSuccessfulAsyncObjectStoreCompletion() throws Exception {
        S3AsyncClient s3 = mock(S3AsyncClient.class);
        CompletableFuture<DeleteObjectResponse> completion = new CompletableFuture<>();
        CountDownLatch requestStarted = new CountDownLatch(1);
        when(s3.deleteObject(anyDeleteRequest())).thenAnswer(invocation -> {
            requestStarted.countDown();
            return completion;
        });
        OssClient client = clientUsing(s3);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<Void> deleteCall = CompletableFuture.runAsync(
                () -> client.delete("http://localhost:9000/attachments/uploads/a.png"), executor);

            assertThat(requestStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(deleteCall).isNotDone();
            completion.complete(DeleteObjectResponse.builder().build());
            deleteCall.join();
            assertThat(deleteCall).isCompleted();
        } finally {
            executor.shutdownNow();
        }
    }

    private static OssClient clientUsing(S3AsyncClient s3) throws Exception {
        OssProperties properties = new OssProperties();
        properties.setAccessKey("access-key");
        properties.setSecretKey("secret-key");
        properties.setEndpoint("localhost:9000");
        properties.setBucketName("attachments");
        properties.setIsHttps("N");
        OssClient client = new OssClient("test", properties);
        Field field = OssClient.class.getDeclaredField("client");
        field.setAccessible(true);
        field.set(client, s3);
        return client;
    }

    @SuppressWarnings("unchecked")
    private static Consumer<DeleteObjectRequest.Builder> anyDeleteRequest() {
        return any(Consumer.class);
    }
}
