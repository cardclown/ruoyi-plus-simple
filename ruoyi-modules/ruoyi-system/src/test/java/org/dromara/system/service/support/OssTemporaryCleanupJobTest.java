package org.dromara.system.service.support;

import org.dromara.system.service.ISysOssService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("dev")
class OssTemporaryCleanupJobTest {

    @Test
    void configuredLocationIsSweptAndExpiredOssRowsAreProcessed() {
        MultipartProperties properties = new MultipartProperties();
        properties.setLocation("C:/server/temp");
        MultipartTempFileCleaner cleaner = mock(MultipartTempFileCleaner.class);
        ISysOssService ossService = mock(ISysOssService.class);
        OssTemporaryCleanupJob job = new OssTemporaryCleanupJob(properties, cleaner, ossService);

        job.clean();

        verify(cleaner).clean(eq(Path.of("C:/server/temp")), any(Instant.class));
        verify(ossService).deleteExpiredArticleTemps(any(Date.class));
    }

    @Test
    void blankLocationNeverFallsBackToTheWorkingDirectory() {
        MultipartProperties properties = new MultipartProperties();
        MultipartTempFileCleaner cleaner = mock(MultipartTempFileCleaner.class);
        ISysOssService ossService = mock(ISysOssService.class);
        OssTemporaryCleanupJob job = new OssTemporaryCleanupJob(properties, cleaner, ossService);

        job.clean();

        verify(cleaner, never()).clean(any(Path.class), any(Instant.class));
        verify(ossService).deleteExpiredArticleTemps(any(Date.class));
    }
}
