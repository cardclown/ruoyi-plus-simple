package org.dromara.system.service.support;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.StampedLock;

/**
 * Coordinates multipart request activity with fallback temporary-file cleanup.
 */
@Component
public class MultipartActivityCoordinator {

    private final StampedLock activityLock = new StampedLock();

    public Lease beginRequest() {
        return new Lease(activityLock, activityLock.readLock());
    }

    public Lease tryBeginCleanup() {
        long stamp = activityLock.tryWriteLock();
        return stamp == 0L ? null : new Lease(activityLock, stamp);
    }

    public static final class Lease implements AutoCloseable {

        private final StampedLock lock;
        private final long stamp;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(StampedLock lock, long stamp) {
            this.lock = lock;
            this.stamp = stamp;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                lock.unlock(stamp);
            }
        }
    }
}
