package org.dromara.system.service.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

/**
 * Bounded fallback cleanup for servlet multipart temporary files.
 */
@Slf4j
@Component
public class MultipartTempFileCleaner {

    static final int MAX_ENTRIES_PER_RUN = 1_024;
    private static final int MAX_DEPTH = 8;

    public int clean(Path root, Instant cutoff) {
        Path safeRoot = validateRoot(root);
        Objects.requireNonNull(cutoff, "cutoff must not be null");
        AtomicInteger visited = new AtomicInteger();
        AtomicInteger deleted = new AtomicInteger();
        try {
            Files.walkFileTree(safeRoot, java.util.Set.of(), MAX_DEPTH, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return next(visited);
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (next(visited) == FileVisitResult.TERMINATE) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (attrs.isRegularFile() && attrs.lastModifiedTime().toInstant().isBefore(cutoff)) {
                        deleteIfInactive(file, deleted);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exception) {
                    log.warn("Unable to inspect multipart temporary file: {}", file, exception);
                    return next(visited);
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to scan multipart temporary directory: " + safeRoot, exception);
        }
        return deleted.get();
    }

    private static FileVisitResult next(AtomicInteger visited) {
        return visited.incrementAndGet() > MAX_ENTRIES_PER_RUN ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
    }

    private static Path validateRoot(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("Multipart temporary directory must be configured");
        }
        Path normalized = root.toAbsolutePath().normalize();
        if (normalized.getParent() == null) {
            throw new IllegalArgumentException("Refusing to clean a filesystem root");
        }
        if (!Files.isDirectory(normalized, NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Multipart temporary directory does not exist: " + normalized);
        }
        return normalized;
    }

    private static void deleteIfInactive(Path file, AtomicInteger deleted) {
        try {
            if (!canAcquireExclusiveLock(file)) {
                return;
            }
            if (Files.deleteIfExists(file)) {
                deleted.incrementAndGet();
            }
        } catch (IOException | SecurityException exception) {
            log.warn("Unable to delete multipart temporary file: {}", file, exception);
        }
    }

    private static boolean canAcquireExclusiveLock(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            try (FileLock lock = channel.tryLock()) {
                return lock != null;
            } catch (OverlappingFileLockException exception) {
                return false;
            }
        }
    }
}
