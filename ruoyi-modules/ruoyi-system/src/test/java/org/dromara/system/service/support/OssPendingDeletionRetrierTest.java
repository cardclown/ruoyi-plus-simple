package org.dromara.system.service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.system.domain.SysOss;
import org.dromara.system.mapper.SysOssMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class OssPendingDeletionRetrierTest {

    @BeforeAll
    static void setUpJsonUtils() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.getBeanFactory().registerSingleton("objectMapper", new ObjectMapper());
        context.refresh();
        new SpringUtils().setApplicationContext(context);
    }

    @Test
    void retryCursorReachesLaterRowsDespiteFailureThenWrapsToEarlyRows() {
        SysOssMapper mapper = mock(SysOssMapper.class);
        OssPendingDeletionWorker worker = mock(OssPendingDeletionWorker.class);
        OssPendingDeletionRetrier retrier = new OssPendingDeletionRetrier(mapper, worker);
        List<SysOss> firstPage = LongStream.rangeClosed(1, 100)
            .mapToObj(OssPendingDeletionRetrierTest::pending)
            .toList();
        AtomicInteger baselinePredicateSize = new AtomicInteger(-1);
        when(mapper.selectList(any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysOss> query =
                invocation.getArgument(0);
            int predicateSize = query.getExpression().getNormal().size();
            int baseline = baselinePredicateSize.updateAndGet(previous ->
                previous < 0 ? predicateSize : previous);
            return predicateSize > baseline ? List.of(pending(101L)) : firstPage;
        });
        doThrow(new IllegalStateException("persistent object-store failure"))
            .when(worker).deleteCandidate(1L, "token-1");

        retrier.retryBatch();
        retrier.retryBatch();
        retrier.retryBatch();

        verify(worker).deleteCandidate(101L, "token-101");
        verify(worker, times(2)).deleteCandidate(1L, "token-1");
    }

    private static SysOss pending(long id) {
        SysOss oss = new SysOss();
        oss.setOssId(id);
        oss.setExt1("{\"deleteStatus\":\"PENDING\",\"deleteToken\":\"token-" + id + "\"}");
        return oss;
    }
}
