package org.dromara.system.service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.oss.core.OssClient;
import org.dromara.system.domain.SysOss;
import org.dromara.system.mapper.SysOssMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
@ExtendWith(MockitoExtension.class)
class OssTemporaryObjectCleanerTest {

    @Mock
    private SysOssMapper mapper;

    @Mock
    private OssClientProvider clientProvider;

    @Mock
    private OssClient client;

    private OssTemporaryObjectCleaner cleaner;

    @BeforeAll
    static void setUpSpringUtilities() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.getBeanFactory().registerSingleton("objectMapper", new ObjectMapper());
        context.getBeanFactory().registerSingleton("cacheManager", new ConcurrentMapCacheManager());
        context.refresh();
        new SpringUtils().setApplicationContext(context);
    }

    @BeforeEach
    void setUp() {
        cleaner = new OssTemporaryObjectCleaner(mapper, clientProvider);
    }

    @Test
    void deletesExactGenericUnboundTemporaryObjectBeforeItsLockedRow() {
        SysOss eligible = stored(10L,
            "{\"fileType\":\"IMAGE\",\"source\":\"userUpload\",\"isTemp\":true}");
        when(mapper.selectOne(any())).thenReturn(eligible);
        when(clientProvider.byService("minio")).thenReturn(client);
        when(mapper.delete(any())).thenReturn(1);

        boolean deleted = cleaner.cleanCandidate(10L, new Date(1_786_291_200_000L));

        assertThat(deleted).isTrue();
        InOrder order = inOrder(client, mapper);
        order.verify(client).deleteObject("uploads/10");
        order.verify(mapper).delete(any());
    }

    @Test
    void objectDeletionFailureRetainsRowForRetry() {
        SysOss eligible = stored(10L,
            "{\"fileType\":\"VIDEO\",\"source\":\"userUpload\",\"isTemp\":true}");
        when(mapper.selectOne(any())).thenReturn(eligible);
        when(clientProvider.byService("minio")).thenReturn(client);
        doThrow(new IllegalStateException("object store unavailable")).when(client)
            .deleteObject("uploads/10");

        assertThatThrownBy(() -> cleaner.cleanCandidate(10L, new Date(1_786_291_200_000L)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("object store unavailable");

        verify(mapper, never()).delete(any());
    }

    @Test
    void boundOrLegacyRowsAreNeverDeleted() {
        SysOss bound = stored(10L,
            "{\"fileType\":\"IMAGE\",\"source\":\"userUpload\",\"isTemp\":true,\"refId\":\"article-1\"}");
        when(mapper.selectOne(any())).thenReturn(bound);

        boolean deleted = cleaner.cleanCandidate(10L, new Date(1_786_291_200_000L));

        assertThat(deleted).isFalse();
        verify(clientProvider, never()).byService(any());
        verify(mapper, never()).delete(any());
    }

    @Test
    void unclassifiedLegacyTemporaryRowIsNeverDeleted() {
        SysOss legacy = stored(10L, "{\"contentType\":\"image/png\",\"isTemp\":true}");
        when(mapper.selectOne(any())).thenReturn(legacy);

        boolean deleted = cleaner.cleanCandidate(10L, new Date(1_786_291_200_000L));

        assertThat(deleted).isFalse();
        verify(clientProvider, never()).byService(any());
        verify(mapper, never()).delete(any());
    }

    @Test
    void failedLockedStateDeletionLeavesRetryableMetadata() {
        SysOss eligible = stored(10L,
            "{\"fileType\":\"IMAGE\",\"source\":\"userUpload\",\"isTemp\":true}");
        when(mapper.selectOne(any())).thenReturn(eligible);
        when(clientProvider.byService("minio")).thenReturn(client);
        when(mapper.delete(any())).thenReturn(0);

        assertThatThrownBy(() -> cleaner.cleanCandidate(10L, new Date(1_786_291_200_000L)))
            .isInstanceOf(ServiceException.class)
            .hasMessage("临时附件记录删除失败");
    }

    private static SysOss stored(Long id, String ext1) {
        SysOss oss = new SysOss();
        oss.setOssId(id);
        oss.setService("minio");
        oss.setFileName("uploads/" + id);
        oss.setUrl("https://bucket.example/uploads/" + id);
        oss.setExt1(ext1);
        return oss;
    }
}
