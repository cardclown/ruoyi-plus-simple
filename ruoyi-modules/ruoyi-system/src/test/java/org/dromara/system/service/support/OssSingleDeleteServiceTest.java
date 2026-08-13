package org.dromara.system.service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.OssReferenceDeleteHandler;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.system.domain.SysOss;
import org.dromara.system.mapper.SysOssMapper;
import org.dromara.system.service.ISysOssService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 单个 OSS 删除的归属判断、权限边界和事务契约测试。
 */
@Tag("dev")
class OssSingleDeleteServiceTest {

    private final SysOssMapper mapper = mock(SysOssMapper.class);
    private final ISysOssService ossService = mock(ISysOssService.class);
    private final OssReferenceDeleteHandlerRegistry registry = mock(OssReferenceDeleteHandlerRegistry.class);
    private final OssBusinessDeletionCoordinator coordinator = mock(OssBusinessDeletionCoordinator.class);
    private final OssDeleteOperationContext operationContext = mock(OssDeleteOperationContext.class);
    private final OssReferenceDeleteHandler handler = mock(OssReferenceDeleteHandler.class);
    private final OssSingleDeleteService service = new OssSingleDeleteService(
        mapper, ossService, registry, coordinator, operationContext);

    @BeforeAll
    static void setUpJsonUtilities() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.getBeanFactory().registerSingleton("objectMapper", new ObjectMapper());
        context.refresh();
        new SpringUtils().setApplicationContext(context);
    }

    @Test
    void deletesTemporaryAttachmentUploadedByCurrentUser() {
        SysOss temporary = temporary(10L, 9L);
        when(mapper.selectById(10L)).thenReturn(temporary);
        when(mapper.selectOne(any())).thenReturn(temporary);
        when(operationContext.currentUserId()).thenReturn(9L);

        assertThat(service.deleteOne(10L)).isEqualTo("删除成功");

        verify(ossService).deleteByIds(List.of(10L));
        verifyNoInteractions(registry, coordinator);
    }

    @Test
    void refusesTemporaryAttachmentOwnedByAnotherUserWithoutOssPermission() {
        SysOss temporary = temporary(10L, 8L);
        when(mapper.selectById(10L)).thenReturn(temporary);
        when(mapper.selectOne(any())).thenReturn(temporary);
        when(operationContext.currentUserId()).thenReturn(9L);
        when(operationContext.canManageOss()).thenReturn(false);

        assertThatThrownBy(() -> service.deleteOne(10L))
            .isInstanceOf(ServiceException.class)
            .hasMessage("无权删除该附件");

        verify(ossService, never()).deleteByIds(any());
        verifyNoInteractions(registry, coordinator);
    }

    @Test
    void removesBusinessRelationBeforeSchedulingOwnedObjectDeletion() {
        when(mapper.selectById(10L)).thenReturn(bound(10L, "100", null));
        when(registry.require("content_article")).thenReturn(handler);

        assertThat(service.deleteOne(10L)).isEqualTo("删除成功");

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(handler, coordinator);
        order.verify(handler).deleteReference(10L, "100");
        order.verify(coordinator).schedule(Map.of(10L, "100"), "content_article");
        verify(mapper, never()).selectOne(any());
        verify(ossService, never()).deleteByIds(any());
    }

    @Test
    void rechecksTemporaryOwnershipAfterLockingAndRefusesAConcurrentBindingChange() {
        when(mapper.selectById(10L)).thenReturn(temporary(10L, 9L));
        when(mapper.selectOne(any())).thenReturn(bound(10L, "100", null));

        assertThatThrownBy(() -> service.deleteOne(10L))
            .isInstanceOf(ServiceException.class)
            .hasMessage("附件归属已变化，请重试");

        verify(ossService, never()).deleteByIds(any());
        verifyNoInteractions(registry, coordinator);
    }

    @Test
    void doesNotScheduleObjectDeletionWhenBusinessRelationCannotBeRemoved() {
        when(mapper.selectById(10L)).thenReturn(bound(10L, "100", null));
        when(registry.require("content_article")).thenReturn(handler);
        org.mockito.Mockito.doThrow(new ServiceException("附件业务关联不存在"))
            .when(handler).deleteReference(10L, "100");

        assertThatThrownBy(() -> service.deleteOne(10L))
            .isInstanceOf(ServiceException.class)
            .hasMessage("附件业务关联不存在");

        verifyNoInteractions(coordinator);
    }

    @Test
    void treatsAuthorizedPendingDeletionAsSuccessfulRetry() {
        when(mapper.selectById(10L)).thenReturn(bound(10L, "100", "PENDING"));
        when(registry.require("content_article")).thenReturn(handler);

        assertThat(service.deleteOne(10L)).isEqualTo("附件正在删除");

        verify(handler).validateDeleteAccess("100");
        verify(handler, never()).deleteReference(any(), any());
        verifyNoInteractions(coordinator, ossService);
    }

    @Test
    void reportsMissingAttachmentWithoutCallingDeletionDependencies() {
        when(mapper.selectById(10L)).thenReturn(null);

        assertThatThrownBy(() -> service.deleteOne(10L))
            .isInstanceOf(ServiceException.class)
            .hasMessage("附件不存在");

        verifyNoInteractions(ossService, registry, coordinator, operationContext);
    }

    @Test
    void runsEachItemInAnIndependentTransaction() throws NoSuchMethodException {
        Method method = OssSingleDeleteService.class.getMethod("deleteOne", Long.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(transactional.rollbackFor()).containsExactly(Exception.class);
    }

    private static SysOss temporary(Long ossId, Long createBy) {
        SysOss oss = new SysOss();
        oss.setOssId(ossId);
        oss.setCreateBy(createBy);
        oss.setExt1("{\"isTemp\":true}");
        return oss;
    }

    private static SysOss bound(Long ossId, String referenceId, String deleteStatus) {
        SysOss oss = new SysOss();
        oss.setOssId(ossId);
        String pending = deleteStatus == null ? "" : ",\"deleteStatus\":\"" + deleteStatus + "\"";
        oss.setExt1("{\"isTemp\":false,\"refType\":\"content_article\",\"refId\":\""
            + referenceId + "\"" + pending + "}");
        return oss;
    }
}
