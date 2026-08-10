package org.dromara.system.service.support;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class MultipartTempFileCleanerTest {

    private static final Instant CUTOFF = Instant.parse("2026-08-09T00:00:00Z");
    private static final FileTime OLD = FileTime.from(Instant.parse("2026-08-08T00:00:00Z"));

    @TempDir
    private Path tempDir;

    private final MultipartActivityCoordinator coordinator = new MultipartActivityCoordinator();
    private final MultipartTempFileCleaner cleaner = new MultipartTempFileCleaner(coordinator);

    @Test
    void deletesOnlyFilesOlderThanTwentyFourHours() throws IOException {
        Path oldFile = Files.writeString(tempDir.resolve("old.tmp"), "old");
        Path recentFile = Files.writeString(tempDir.resolve("recent.tmp"), "recent");
        Files.setLastModifiedTime(oldFile, OLD);
        Files.setLastModifiedTime(recentFile, FileTime.from(Instant.parse("2026-08-09T12:30:00Z")));

        int deleted = cleaner.clean(tempDir, CUTOFF);

        assertThat(deleted).isEqualTo(1);
        assertThat(oldFile).doesNotExist();
        assertThat(recentFile).exists();
    }

    @Test
    void cannotSweepWhileMultipartRequestLeaseIsActiveAndProceedsAfterRelease() throws IOException {
        Path activeFile = Files.writeString(tempDir.resolve("active.tmp"), "active");
        Files.setLastModifiedTime(activeFile, OLD);

        try (MultipartActivityCoordinator.Lease ignored = coordinator.beginRequest()) {
            assertThat(cleaner.clean(tempDir, CUTOFF)).isZero();
            assertThat(activeFile).exists();
        }

        assertThat(cleaner.clean(tempDir, CUTOFF)).isEqualTo(1);
        assertThat(activeFile).doesNotExist();
    }

    @Test
    void replacementAfterObservationIsNotDeleted() throws IOException {
        Path file = Files.writeString(tempDir.resolve("replace.tmp"), "old");
        Files.setLastModifiedTime(file, OLD);
        MultipartTempFileCleaner.FileIdentity observed = cleaner.identity(file);
        Files.delete(file);
        Files.writeString(file, "replacement-is-different");
        Files.setLastModifiedTime(file, OLD);

        boolean deleted = cleaner.deleteIfUnchanged(file, observed, CUTOFF);

        assertThat(deleted).isFalse();
        assertThat(file).hasContent("replacement-is-different");
    }

    @Test
    void laterRunReachesExpiredEntriesBeyondFirstBoundedBatch() throws IOException {
        int total = MultipartTempFileCleaner.MAX_ENTRIES_PER_RUN + 1;
        for (int index = 0; index < total; index++) {
            Path file = Files.writeString(tempDir.resolve("entry-" + index + ".tmp"), "x");
            Files.setLastModifiedTime(file, OLD);
        }

        int firstRun = cleaner.clean(tempDir, CUTOFF);
        int secondRun = cleaner.clean(tempDir, CUTOFF);

        assertThat(firstRun).isEqualTo(MultipartTempFileCleaner.MAX_ENTRIES_PER_RUN);
        assertThat(secondRun).isEqualTo(1);
        try (Stream<Path> remaining = Files.list(tempDir)) {
            assertThat(remaining).isEmpty();
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
