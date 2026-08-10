package org.dromara.system.service.support;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Holds a process-authoritative activity lease from multipart request entry through completion.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@RequiredArgsConstructor
public class MultipartActivityFilter extends OncePerRequestFilter {

    private final MultipartActivityCoordinator coordinator;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType == null
            || !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        MultipartActivityCoordinator.Lease lease = coordinator.beginRequest();
        boolean async = false;
        try {
            filterChain.doFilter(request, response);
            if (request.isAsyncStarted()) {
                request.getAsyncContext().addListener(new LeaseClosingAsyncListener(lease));
                async = true;
            }
        } finally {
            if (!async) {
                lease.close();
            }
        }
    }

    private record LeaseClosingAsyncListener(MultipartActivityCoordinator.Lease lease) implements AsyncListener {

        @Override
        public void onComplete(AsyncEvent event) {
            lease.close();
        }

        @Override
        public void onTimeout(AsyncEvent event) {
            lease.close();
        }

        @Override
        public void onError(AsyncEvent event) {
            lease.close();
        }

        @Override
        public void onStartAsync(AsyncEvent event) {
            event.getAsyncContext().addListener(this);
        }
    }
}
