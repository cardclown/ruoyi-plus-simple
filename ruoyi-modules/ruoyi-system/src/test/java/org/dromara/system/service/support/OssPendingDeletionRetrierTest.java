package org.dromara.system.service.support;

import org.dromara.system.domain.SysOss;
import org.dromara.system.mapper.SysOssMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class OssPendingDeletionRetrierTest {

    @Test
    void retryRunIsBoundedToOneHundredCandidates() {
        SysOssMapper mapper = mock(SysOssMapper.class);
        OssPendingDeletionWorker worker = mock(OssPendingDeletionWorker.class);
        OssPendingDeletionRetrier retrier = new OssPendingDeletionRetrier(mapper, worker);
        List<SysOss> candidates = LongStream.rangeClosed(1, 101)
            .mapToObj(OssPendingDeletionRetrierTest::pending)
            .toList();
        when(mapper.selectList(any())).thenReturn(candidates);

        retrier.retryBatch();

        verify(worker, times(100)).deleteCandidate(any(), any());
        verify(worker, never()).deleteCandidate(101L, "token-101");
    }

    private static SysOss pending(long id) {
        SysOss oss = new SysOss();
        oss.setOssId(id);
        oss.setExt1("{\"deleteStatus\":\"PENDING\",\"deleteToken\":\"token-" + id + "\"}");
        return oss;
    }
}
