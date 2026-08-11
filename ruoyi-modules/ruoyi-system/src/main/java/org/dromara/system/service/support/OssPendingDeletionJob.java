package org.dromara.system.service.support;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically retries one bounded page of committed business-media deletions. */
@Component
@RequiredArgsConstructor
public class OssPendingDeletionJob {

    private final OssPendingDeletionRetrier retrier;

    @Scheduled(fixedDelayString = "${oss.pending-delete.retry-interval:PT5M}")
    public void retry() {
        retrier.retryBatch();
    }
}
