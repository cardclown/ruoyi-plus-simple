package org.dromara.system.service.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.system.service.ISysOssService;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Periodic cleanup for request-local multipart files and unbound article objects.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OssTemporaryCleanupJob {

    private final MultipartProperties multipartProperties;
    private final MultipartTempFileCleaner multipartTempFileCleaner;
    private final ISysOssService ossService;

    @Scheduled(fixedDelayString = "${oss.cleanup.interval:PT1H}")
    public void clean() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(24));
        String location = multipartProperties.getLocation();
        if (StringUtils.isNotBlank(location)) {
            try {
                multipartTempFileCleaner.clean(Path.of(location), cutoff);
            } catch (RuntimeException exception) {
                log.warn("Unable to clean multipart temporary directory: {}", location, exception);
            }
        }
        ossService.deleteExpiredArticleTemps(Date.from(cutoff));
    }
}
