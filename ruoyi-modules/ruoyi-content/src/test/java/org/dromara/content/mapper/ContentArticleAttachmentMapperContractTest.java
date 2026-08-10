package org.dromara.content.mapper;

import org.dromara.content.enums.ContentArticleAttachmentType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ContentArticleAttachmentMapperContractTest {

    @Test
    void attachmentTypeCodesMatchPersistedValues() {
        assertThat(ContentArticleAttachmentType.IMAGE.getCode()).isEqualTo("0");
        assertThat(ContentArticleAttachmentType.VIDEO.getCode()).isEqualTo("1");
    }

    @Test
    void batchOperationsDoNotIssuePersistenceCallsForMissingArticleIds() {
        ContentArticleAttachmentMapper mapper = shortCircuitMapper();

        assertThat(mapper.selectByArticleIds(null)).isEqualTo(List.of());
        assertThat(mapper.selectByArticleIds(List.of())).isEqualTo(List.of());
        assertThat(mapper.deleteByArticleIds(null)).isZero();
        assertThat(mapper.deleteByArticleIds(List.of())).isZero();
    }

    private static ContentArticleAttachmentMapper shortCircuitMapper() {
        return (ContentArticleAttachmentMapper) Proxy.newProxyInstance(
            ContentArticleAttachmentMapper.class.getClassLoader(),
            new Class<?>[]{ContentArticleAttachmentMapper.class},
            (proxy, method, arguments) -> {
                if (method.isDefault()) {
                    return InvocationHandler.invokeDefault(proxy, method, arguments);
                }
                throw new AssertionError("Unexpected persistence call: " + method.getName());
            }
        );
    }
}
