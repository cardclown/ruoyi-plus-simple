package org.dromara.system.service.support;

import org.dromara.system.domain.SysOss;
import org.dromara.system.mapper.SysOssMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class OssBusinessDeletionCoordinatorTest {

    private final SysOssMapper mapper = mock(SysOssMapper.class);
    private final OssPendingDeletionWorker worker = mock(OssPendingDeletionWorker.class);
    private final OssBusinessDeletionCoordinator coordinator =
        new OssBusinessDeletionCoordinator(mapper, worker);
    private final TransactionTemplate transactions = new TransactionTemplate(new TestTransactionManager());

    @Test
    void rollbackNeverDeletesTheObject() {
        when(mapper.selectList(any())).thenReturn(java.util.List.of(owned(10L)));
        when(mapper.updateById(any(SysOss.class))).thenReturn(1);

        transactions.executeWithoutResult(status -> {
            coordinator.schedule(Map.of(10L, "100"), "content_article");
            status.setRollbackOnly();
        });

        verify(worker, never()).deleteCandidate(any(), any());
    }

    @Test
    void commitMarksPendingAndTriggersAfterCommitDeletion() {
        when(mapper.selectList(any())).thenReturn(java.util.List.of(owned(10L)));
        when(mapper.updateById(any(SysOss.class))).thenReturn(1);

        transactions.executeWithoutResult(status ->
            coordinator.schedule(Map.of(10L, "100"), "content_article"));

        org.mockito.ArgumentCaptor<SysOss> marked = org.mockito.ArgumentCaptor.forClass(SysOss.class);
        verify(mapper).updateById(marked.capture());
        assertThat(marked.getValue().getExt1())
            .contains("\"deleteStatus\":\"PENDING\"")
            .contains("\"deleteToken\":");
        org.mockito.ArgumentCaptor<String> token = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(worker).deleteCandidate(org.mockito.ArgumentMatchers.eq(10L), token.capture());
        assertThat(token.getValue()).isNotBlank();
    }

    @Test
    void refusesSchedulingWithoutAnActiveTransaction() {
        assertThatThrownBy(() -> coordinator.schedule(Map.of(10L, "100"), "content_article"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("业务附件删除必须在活动事务中调度");

        verify(mapper, never()).selectList(any());
    }

    @Test
    void refusesAChangedOwnerBeforeWritingPendingState() {
        SysOss changed = owned(10L);
        changed.setExt1("{\"refType\":\"content_article\",\"refId\":\"other\",\"isTemp\":false}");
        when(mapper.selectList(any())).thenReturn(java.util.List.of(changed));

        assertThatThrownBy(() -> transactions.executeWithoutResult(status ->
            coordinator.schedule(Map.of(10L, "100"), "content_article")))
            .isInstanceOf(org.dromara.common.core.exception.ServiceException.class)
            .hasMessage("附件归属已变化，不能删除");

        verify(mapper, never()).updateById(any(SysOss.class));
        verify(worker, never()).deleteCandidate(any(), any());
    }

    private static SysOss owned(Long id) {
        SysOss oss = new SysOss();
        oss.setOssId(id);
        oss.setExt1("{\"refType\":\"content_article\",\"refId\":\"100\",\"isTemp\":false}");
        return oss;
    }

    private static final class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
