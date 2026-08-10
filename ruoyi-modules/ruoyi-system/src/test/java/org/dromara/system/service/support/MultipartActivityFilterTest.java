package org.dromara.system.service.support;

import jakarta.servlet.FilterChain;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class MultipartActivityFilterTest {

    @Test
    void holdsRequestLeaseForTheEntireMultipartFilterChain() throws Exception {
        MultipartActivityCoordinator coordinator = new MultipartActivityCoordinator();
        MultipartActivityFilter filter = new MultipartActivityFilter(coordinator);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/resource/oss/upload");
        request.setContentType("multipart/form-data; boundary=test");
        AtomicBoolean cleanupEntered = new AtomicBoolean();
        FilterChain chain = (servletRequest, servletResponse) -> {
            try (MultipartActivityCoordinator.Lease ignored = coordinator.tryBeginCleanup()) {
                cleanupEntered.set(ignored != null);
            }
        };

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(cleanupEntered).isFalse();
        try (MultipartActivityCoordinator.Lease cleanup = coordinator.tryBeginCleanup()) {
            assertThat(cleanup).isNotNull();
        }
    }

    @Test
    void doesNotLeaseNonMultipartRequests() throws Exception {
        MultipartActivityCoordinator coordinator = new MultipartActivityCoordinator();
        MultipartActivityFilter filter = new MultipartActivityFilter(coordinator);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/resource/oss/list");
        AtomicBoolean cleanupEntered = new AtomicBoolean();

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
            try (MultipartActivityCoordinator.Lease cleanup = coordinator.tryBeginCleanup()) {
                cleanupEntered.set(cleanup != null);
            }
        });

        assertThat(cleanupEntered).isTrue();
    }

    @Test
    void asyncCompletionOnDifferentThreadReleasesLeaseIdempotently() throws Exception {
        MultipartActivityCoordinator coordinator = new MultipartActivityCoordinator();
        MultipartActivityFilter filter = new MultipartActivityFilter(coordinator);
        MockHttpServletRequest request = mock(MockHttpServletRequest.class);
        when(request.getContentType()).thenReturn("multipart/form-data; boundary=test");
        when(request.isAsyncStarted()).thenReturn(true);
        AsyncContext asyncContext = mock(AsyncContext.class);
        when(request.getAsyncContext()).thenReturn(asyncContext);
        AtomicReference<AsyncListener> listener = new AtomicReference<>();
        doAnswer(invocation -> {
            listener.set(invocation.getArgument(0));
            return null;
        }).when(asyncContext).addListener(any(AsyncListener.class));

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> { });

        assertThat(coordinator.tryBeginCleanup()).isNull();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> completion = executor.submit(() -> {
                listener.get().onComplete(new AsyncEvent(asyncContext));
                return null;
            });
            assertThatCode(completion::get).doesNotThrowAnyException();
            listener.get().onTimeout(new AsyncEvent(asyncContext));
        } finally {
            executor.shutdownNow();
        }
        try (MultipartActivityCoordinator.Lease cleanup = coordinator.tryBeginCleanup()) {
            assertThat(cleanup).isNotNull();
        }
    }
}
