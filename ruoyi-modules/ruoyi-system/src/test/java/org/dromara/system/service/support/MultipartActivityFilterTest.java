package org.dromara.system.service.support;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

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
}
