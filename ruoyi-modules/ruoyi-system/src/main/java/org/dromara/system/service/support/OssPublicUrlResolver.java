package org.dromara.system.service.support;

import jakarta.servlet.http.HttpServletRequest;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * 将本机 MinIO 的内部 URL 转换为当前请求方可访问的 URL。
 *
 * <p>数据库和 S3 客户端始终使用 {@code 127.0.0.1} 等稳定内部地址，避免 DHCP 地址变化导致
 * 上传、删除失效；只有向浏览器返回公共桶 URL 时才替换主机名。端口、桶名、对象路径和查询参数
 * 均保持不变。云 OSS、自定义稳定域名、无 HTTP 请求的后台任务和私有桶签名 URL 不使用该转换。</p>
 */
public final class OssPublicUrlResolver {

    private static final String FORWARDED = "Forwarded";
    private static final String X_FORWARDED_HOST = "X-Forwarded-Host";
    private static final String ORIGIN = "Origin";
    private static final String REFERER = "Referer";

    private OssPublicUrlResolver() {
    }

    /**
     * 根据当前 HTTP 请求解析公共 URL。
     *
     * @param storedUrl 数据库保存的稳定内部 URL
     * @param storageEndpoint OSS 客户端实际使用的内部端点
     * @param configuredDomain OSS 配置中的访问域名；未填写时与内部端点一致
     * @return 浏览器可访问的 URL；不满足转换条件时原样返回
     */
    public static String resolveForCurrentRequest(String storedUrl, String storageEndpoint,
                                                  String configuredDomain) {
        if (StringUtils.isBlank(storedUrl)
            || !usesLoopbackEndpoint(storageEndpoint)
            || !sameAuthority(storageEndpoint, configuredDomain)) {
            return storedUrl;
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return storedUrl;
        }
        return resolve(storedUrl, servletAttributes.getRequest());
    }

    private static String resolve(String storedUrl, HttpServletRequest request) {
        try {
            URI internalUrl = new URI(storedUrl);
            if (!isHttp(internalUrl) || StringUtils.isBlank(internalUrl.getHost())) {
                return storedUrl;
            }
            String requestHost = resolveRequestHost(request);
            if (StringUtils.isBlank(requestHost)) {
                return storedUrl;
            }
            return new URI(internalUrl.getScheme(), internalUrl.getUserInfo(), requestHost,
                internalUrl.getPort(), internalUrl.getPath(), internalUrl.getQuery(),
                internalUrl.getFragment()).toASCIIString();
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            return storedUrl;
        }
    }

    /**
     * 可信代理头优先；开发代理把 Host 改成 localhost 时，使用浏览器 Origin 恢复访问主机。
     */
    private static String resolveRequestHost(HttpServletRequest request) {
        String host = hostFromForwarded(request.getHeader(FORWARDED));
        if (StringUtils.isNotBlank(host)) {
            return host;
        }
        host = hostFromAuthority(firstHeaderValue(request.getHeader(X_FORWARDED_HOST)));
        if (StringUtils.isNotBlank(host)) {
            return host;
        }
        String serverName = request.getServerName();
        if (isLoopbackHost(serverName)) {
            String originHost = hostFromAbsoluteUrl(request.getHeader(ORIGIN));
            if (StringUtils.isNotBlank(originHost) && !isLoopbackHost(originHost)) {
                return originHost;
            }
            String refererHost = hostFromAbsoluteUrl(request.getHeader(REFERER));
            if (StringUtils.isNotBlank(refererHost) && !isLoopbackHost(refererHost)) {
                return refererHost;
            }
        }
        return hostFromAuthority(serverName);
    }

    private static String hostFromForwarded(String forwarded) {
        String first = firstHeaderValue(forwarded);
        if (StringUtils.isBlank(first)) {
            return null;
        }
        for (String parameter : first.split(";")) {
            String value = parameter.trim();
            if (value.toLowerCase(Locale.ROOT).startsWith("host=")) {
                return hostFromAuthority(unquote(value.substring("host=".length())));
            }
        }
        return null;
    }

    private static String firstHeaderValue(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return value.split(",", 2)[0].trim();
    }

    private static String unquote(String value) {
        String candidate = value.trim();
        if (candidate.length() >= 2 && candidate.startsWith("\"") && candidate.endsWith("\"")) {
            return candidate.substring(1, candidate.length() - 1);
        }
        return candidate;
    }

    private static String hostFromAbsoluteUrl(String value) {
        if (StringUtils.isBlank(value) || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        try {
            return new URI(value.trim()).getHost();
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private static String hostFromAuthority(String value) {
        if (StringUtils.isBlank(value) || value.contains("\r") || value.contains("\n")) {
            return null;
        }
        try {
            return new URI("http://" + value.trim()).getHost();
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private static boolean usesLoopbackEndpoint(String endpoint) {
        if (StringUtils.isBlank(endpoint)) {
            return false;
        }
        try {
            return isLoopbackHost(new URI(endpoint).getHost());
        } catch (URISyntaxException ignored) {
            return false;
        }
    }

    /**
     * OSS 配置填写了独立 domain 时必须尊重管理员配置，不能再按请求 Host 覆盖。
     */
    private static boolean sameAuthority(String endpoint, String domain) {
        if (StringUtils.isBlank(endpoint) || StringUtils.isBlank(domain)) {
            return false;
        }
        try {
            URI endpointUri = new URI(endpoint);
            URI domainUri = new URI(domain);
            return endpointUri.getPort() == domainUri.getPort()
                && java.util.Objects.equals(endpointUri.getHost(), domainUri.getHost())
                && java.util.Objects.equals(endpointUri.getScheme(), domainUri.getScheme());
        } catch (URISyntaxException ignored) {
            return false;
        }
    }

    private static boolean isHttp(URI uri) {
        return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
    }

    private static boolean isLoopbackHost(String host) {
        if (StringUtils.isBlank(host)) {
            return false;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
            || "::1".equals(normalized)
            || "0:0:0:0:0:0:0:1".equals(normalized)
            || normalized.startsWith("127.");
    }
}
