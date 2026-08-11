package org.dromara.system.service.support;

import org.dromara.common.oss.core.OssClient;
import org.dromara.system.domain.SysOss;
import org.dromara.system.mapper.SysOssMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class OssPendingDeletionWorkerTest {

    @Test
    void objectFailureLeavesPendingRowAndAHealthyRetryDeletesIt() {
        SysOssMapper mapper = mock(SysOssMapper.class);
        OssClientProvider clients = mock(OssClientProvider.class);
        OssClient client = mock(OssClient.class);
        OssPendingDeletionWorker worker = new OssPendingDeletionWorker(mapper, clients);
        when(mapper.selectOne(any())).thenReturn(pending(10L, "token-1"));
        when(clients.byService("minio")).thenReturn(client);
        doThrow(new IllegalStateException("object store unavailable"))
            .when(client).delete("https://bucket.example/10");

        assertThatThrownBy(() -> worker.deleteCandidate(10L, "token-1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("object store unavailable");
        verify(mapper, never()).delete(any());

        doNothing().when(client).delete("https://bucket.example/10");
        when(mapper.delete(any())).thenReturn(1);
        assertThat(worker.deleteCandidate(10L, "token-1")).isTrue();
        verify(mapper).delete(any());
    }

    @Test
    void staleRetryTokenCannotDeleteAChangedRowOrObject() {
        SysOssMapper mapper = mock(SysOssMapper.class);
        OssClientProvider clients = mock(OssClientProvider.class);
        OssPendingDeletionWorker worker = new OssPendingDeletionWorker(mapper, clients);
        when(mapper.selectOne(any())).thenReturn(pending(10L, "new-token"));

        assertThat(worker.deleteCandidate(10L, "old-token")).isFalse();

        verify(clients, never()).byService(any());
        verify(mapper, never()).delete(any());
    }

    private static SysOss pending(Long id, String token) {
        SysOss oss = new SysOss();
        oss.setOssId(id);
        oss.setService("minio");
        oss.setUrl("https://bucket.example/" + id);
        oss.setExt1("{\"refType\":\"content_article\",\"refId\":\"100\","
            + "\"deleteStatus\":\"PENDING\",\"deleteToken\":\"" + token + "\"}");
        return oss;
    }
}
