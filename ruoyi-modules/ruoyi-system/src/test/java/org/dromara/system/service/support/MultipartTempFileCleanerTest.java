package org.dromara.system.service.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class MultipartTempFileCleanerTest {

    @TempDir
    private Path tempDir;

    private final MultipartTempFileCleaner cleaner = new MultipartTempFileCleaner();

    @Test
    void deletesOnlyFilesOlderThanTwentyFourHours() throws IOException {
        Path oldFile = Files.writeString(tempDir.resolve("old.tmp"), "old");
        Path recentFile = Files.writeString(tempDir.resolve("recent.tmp"), "recent");
        Files.setLastModifiedTime(oldFile, FileTime.from(Instant.parse("2026-08-08T00:00:00Z")));
        Files.setLastModifiedTime(recentFile, FileTime.from(Instant.parse("2026-08-09T12:30:00Z")));

        int deleted = cleaner.clean(tempDir, Instant.parse("2026-08-09T00:00:00Z"));

        assertThat(deleted).isEqualTo(1);
        assertThat(oldFile).doesNotExist();
        assertThat(recentFile).exists();
    }

    @Test
    void keepsAnOldFileThatIsActivelyLocked() throws IOException {
        Path activeFile = Files.writeString(tempDir.resolve("active.tmp"), "active");
        Files.setLastModifiedTime(activeFile, FileTime.from(Instant.parse("2026-08-08T00:00:00Z")));

        try (FileChannel channel = FileChannel.open(activeFile, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            int deleted = cleaner.clean(tempDir, Instant.parse("2026-08-09T00:00:00Z"));

            assertThat(deleted).isZero();
            assertThat(activeFile).exists();
        }
    }

    @Test
    void refusesUnsafeOrMissingRoots() {
        Path filesystemRoot = tempDir.toAbsolutePath().getRoot();

        assertThatThrownBy(() -> cleaner.clean(null, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cleaner.clean(filesystemRoot, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cleaner.clean(tempDir.resolve("missing"), Instant.now()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
