package org.dromara.system.service.support;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Coordinates multipart request activity with fallback temporary-file cleanup.
 */
@Component
public class MultipartActivityCoordinator {

    private final ReentrantReadWriteLock activityLock = new ReentrantReadWriteLock(true);

    public Lease beginRequest() {
        Lock requestLock = activityLock.readLock();
        requestLock.lock();
        return new Lease(requestLock);
    }

    public Lease tryBeginCleanup() {
        Lock cleanupLock = activityLock.writeLock();
        return cleanupLock.tryLock() ? new Lease(cleanupLock) : null;
    }

    public static final class Lease implements AutoCloseable {

        private final Lock lock;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(Lock lock) {
            this.lock = lock;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                lock.unlock();
            }
        }
    }
}
