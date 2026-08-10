package org.dromara.system.service.support;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

/**
 * Bounded fallback cleanup for servlet multipart temporary files.
 *
 * <p>The configured multipart location must be private to this application instance. Multipart
 * containers place request files directly in that directory; retaining its iterator lets later
 * runs continue without rescanning an unbounded prefix.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultipartTempFileCleaner {

    static final int MAX_ENTRIES_PER_RUN = 1_024;

    private final MultipartActivityCoordinator coordinator;
    private final Map<Path, DirectoryCursor> cursors = new ConcurrentHashMap<>();

    public int clean(Path root, Instant cutoff) {
        Path safeRoot = validateRoot(root);
        Objects.requireNonNull(cutoff, "cutoff must not be null");
        try (MultipartActivityCoordinator.Lease lease = coordinator.tryBeginCleanup()) {
            if (lease == null) {
                return 0;
            }
            DirectoryCursor cursor = cursors.computeIfAbsent(safeRoot, DirectoryCursor::new);
            return cursor.cleanNext(cutoff);
        }
    }

    FileIdentity identity(Path file) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class, NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) {
            throw new IOException("Not a regular file: " + file);
        }
        return new FileIdentity(attributes.fileKey(), attributes.size(), attributes.creationTime(),
            attributes.lastModifiedTime());
    }

    boolean deleteIfUnchanged(Path file, FileIdentity observed, Instant cutoff) {
        try {
            FileIdentity current = identity(file);
            if (!observed.equals(current) || !current.lastModified().toInstant().isBefore(cutoff)) {
                return false;
            }
            return Files.deleteIfExists(file);
        } catch (IOException | SecurityException exception) {
            log.warn("Unable to delete multipart temporary file: {}", file, exception);
            return false;
        }
    }

    @PreDestroy
    public void close() {
        cursors.values().forEach(DirectoryCursor::closeQuietly);
        cursors.clear();
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

    record FileIdentity(Object fileKey, long size, FileTime creationTime, FileTime lastModified) {
    }

    private final class DirectoryCursor {

        private final Path root;
        private DirectoryStream<Path> stream;
        private Iterator<Path> iterator;

        private DirectoryCursor(Path root) {
            this.root = root;
        }

        private synchronized int cleanNext(Instant cutoff) {
            int deleted = 0;
            try {
                ensureOpen();
                int visited = 0;
                while (visited < MAX_ENTRIES_PER_RUN && iterator.hasNext()) {
                    Path file = iterator.next();
                    visited++;
                    try {
                        FileIdentity observed = identity(file);
                        if (observed.lastModified().toInstant().isBefore(cutoff)
                            && deleteIfUnchanged(file, observed, cutoff)) {
                            deleted++;
                        }
                    } catch (IOException | SecurityException exception) {
                        log.warn("Unable to inspect multipart temporary file: {}", file, exception);
                    }
                }
                if (!iterator.hasNext()) {
                    closeQuietly();
                }
                return deleted;
            } catch (IOException exception) {
                closeQuietly();
                throw new IllegalStateException("Unable to scan multipart temporary directory: " + root, exception);
            }
        }

        private void ensureOpen() throws IOException {
            if (stream == null) {
                stream = Files.newDirectoryStream(root);
                iterator = stream.iterator();
            }
        }

        private synchronized void closeQuietly() {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException exception) {
                    log.warn("Unable to close multipart temporary directory cursor: {}", root, exception);
                } finally {
                    stream = null;
                    iterator = null;
                }
            }
        }
    }
}
