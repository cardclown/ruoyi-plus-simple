package org.dromara.system.service.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class OssPublicUrlResolverTest {

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void loopbackMinioUrlUsesCurrentRequestHostAndKeepsObjectAddress() {
        bindRequest(request("10.50.3.55"));

        String result = OssPublicUrlResolver.resolveForCurrentRequest(
            "http://127.0.0.1:9000/ruoyi/2026/08/demo.mp4?download=1",
            "http://127.0.0.1:9000", "http://127.0.0.1:9000");

        assertThat(result).isEqualTo(
            "http://10.50.3.55:9000/ruoyi/2026/08/demo.mp4?download=1");
    }

    @Test
    void developmentProxyUsesBrowserOriginWhenItChangedRequestHostToLocalhost() {
        MockHttpServletRequest request = request("localhost");
        request.addHeader("Origin", "http://10.50.3.56:5173");
        bindRequest(request);

        String result = OssPublicUrlResolver.resolveForCurrentRequest(
            "http://127.0.0.1:9000/ruoyi/image.png",
            "http://127.0.0.1:9000", "http://127.0.0.1:9000");

        assertThat(result).isEqualTo("http://10.50.3.56:9000/ruoyi/image.png");
    }

    @Test
    void developmentProxyCanUseBrowserRefererForGetRequestsWithoutOrigin() {
        MockHttpServletRequest request = request("localhost");
        request.addHeader("Referer", "http://10.50.3.57:5173/article/list");
        bindRequest(request);

        String result = OssPublicUrlResolver.resolveForCurrentRequest(
            "http://127.0.0.1:9000/ruoyi/image.png",
            "http://127.0.0.1:9000", "http://127.0.0.1:9000");

        assertThat(result).isEqualTo("http://10.50.3.57:9000/ruoyi/image.png");
    }

    @Test
    void reverseProxyForwardedHostHasPriorityAndItsFrontendPortIsNotReused() {
        MockHttpServletRequest request = request("localhost");
        request.addHeader("Forwarded", "for=10.0.0.8;host=files.example.test:443;proto=https");
        bindRequest(request);

        String result = OssPublicUrlResolver.resolveForCurrentRequest(
            "http://127.0.0.1:9000/ruoyi/image.png",
            "http://127.0.0.1:9000", "http://127.0.0.1:9000");

        assertThat(result).isEqualTo("http://files.example.test:9000/ruoyi/image.png");
    }

    @Test
    void stableExternalEndpointIsNeverRewritten() {
        bindRequest(request("10.50.3.55"));

        String result = OssPublicUrlResolver.resolveForCurrentRequest(
            "https://bucket.example.com/image.png",
            "https://oss.example.com", "https://oss.example.com");

        assertThat(result).isEqualTo("https://bucket.example.com/image.png");
    }

    @Test
    void backgroundTaskWithoutRequestKeepsInternalUrl() {
        String result = OssPublicUrlResolver.resolveForCurrentRequest(
            "http://127.0.0.1:9000/ruoyi/image.png",
            "http://127.0.0.1:9000", "http://127.0.0.1:9000");

        assertThat(result).isEqualTo("http://127.0.0.1:9000/ruoyi/image.png");
    }

    @Test
    void configuredStableDomainOverridesDynamicHostMode() {
        bindRequest(request("10.50.3.55"));

        String result = OssPublicUrlResolver.resolveForCurrentRequest(
            "https://files.example.com/ruoyi/image.png",
            "http://127.0.0.1:9000", "https://files.example.com");

        assertThat(result).isEqualTo("https://files.example.com/ruoyi/image.png");
    }

    private static MockHttpServletRequest request(String serverName) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName(serverName);
        return request;
    }

    private static void bindRequest(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
