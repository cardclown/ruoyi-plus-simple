package org.dromara.system.service.support;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.OssReferenceDeleteHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OSS 业务引用处理器注册约束测试。
 */
@Tag("dev")
class OssReferenceDeleteHandlerRegistryTest {

    @Test
    void returnsTheOnlyHandlerRegisteredForAReferenceType() {
        OssReferenceDeleteHandler handler = handler("content_article");
        OssReferenceDeleteHandlerRegistry registry = new OssReferenceDeleteHandlerRegistry(List.of(handler));

        assertThat(registry.require("content_article")).isSameAs(handler);
    }

    @Test
    void rejectsDuplicateReferenceTypesAtStartup() {
        OssReferenceDeleteHandler first = handler("content_article");
        OssReferenceDeleteHandler second = handler("content_article");

        assertThatThrownBy(() -> new OssReferenceDeleteHandlerRegistry(List.of(first, second)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("OSS业务引用删除处理器重复: content_article");
    }

    @Test
    void reportsUnsupportedBusinessTypeAsPerItemBusinessFailure() {
        OssReferenceDeleteHandlerRegistry registry = new OssReferenceDeleteHandlerRegistry(List.of());

        assertThatThrownBy(() -> registry.require("unknown"))
            .isInstanceOf(ServiceException.class)
            .hasMessage("附件所属业务暂不支持删除");
    }

    private static OssReferenceDeleteHandler handler(String referenceType) {
        OssReferenceDeleteHandler handler = mock(OssReferenceDeleteHandler.class);
        when(handler.supportedReferenceType()).thenReturn(referenceType);
        return handler;
    }
}
