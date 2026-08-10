package org.dromara.content.domain.vo;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.beans.Introspector;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ContentArticlePublicVoContractTest {

    @Test
    void exposesOnlyPublicArticleFields() throws Exception {
        assertThat(Arrays.stream(Introspector.getBeanInfo(ContentArticlePublicVo.class)
                .getPropertyDescriptors())
            .map(descriptor -> descriptor.getName())
            .filter(name -> !"class".equals(name)))
            .containsExactlyInAnyOrder(
                "articleId",
                "title",
                "summary",
                "content",
                "categoryDictCode",
                "tagIds",
                "coverOssId",
                "publishTime"
            );
    }
}
