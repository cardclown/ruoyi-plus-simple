package org.dromara.content.domain.bo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.beans.Introspector;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class ContentArticleRequestModelContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void writeModelDoesNotInheritFrameworkRequestFields() throws Exception {
        assertThat(BaseEntity.class.isAssignableFrom(ContentArticleBo.class)).isFalse();
        assertThat(propertyNames(ContentArticleBo.class))
            .doesNotContain("createDept", "createBy", "createTime", "updateBy", "updateTime", "params");
    }

    @Test
    void addViewDoesNotBindArticleId() throws Exception {
        Class<?> addView = Class.forName(ContentArticleBo.class.getName() + "$AddView");

        ContentArticleBo bo = objectMapper.readerWithView(addView)
            .forType(ContentArticleBo.class)
            .readValue("{\"articleId\":999,\"title\":\"标题\",\"content\":\"<p>正文</p>\",\"categoryDictCode\":11,\"status\":\"0\"}");

        assertThat(bo.getArticleId()).isNull();
        assertThat(bo.getTitle()).isEqualTo("标题");
    }

    @Test
    void editViewBindsArticleIdAndCommonFields() throws Exception {
        Class<?> editView = Class.forName(ContentArticleBo.class.getName() + "$EditView");

        ContentArticleBo bo = objectMapper.readerWithView(editView)
            .forType(ContentArticleBo.class)
            .readValue("{\"articleId\":999,\"title\":\"标题\",\"content\":\"<p>正文</p>\",\"categoryDictCode\":11,\"status\":\"1\"}");

        assertThat(bo.getArticleId()).isEqualTo(999L);
        assertThat(bo.getTitle()).isEqualTo("标题");
    }

    @Test
    void addAndEditViewsBindMediaLists() throws Exception {
        String json = """
            {"articleId":999,"title":"标题","content":"<p>正文</p>","categoryDictCode":11,
             "attachmentOssIds":[10,11],"videoOssIds":[20],"status":"0"}
            """;

        ContentArticleBo add = objectMapper.readerWithView(ContentArticleBo.AddView.class)
            .forType(ContentArticleBo.class).readValue(json);
        ContentArticleBo edit = objectMapper.readerWithView(ContentArticleBo.EditView.class)
            .forType(ContentArticleBo.class).readValue(json);

        assertThat(add.getArticleId()).isNull();
        assertThat(edit.getArticleId()).isEqualTo(999L);
        assertThat(add.getAttachmentOssIds()).containsExactly(10L, 11L);
        assertThat(add.getVideoOssIds()).containsExactly(20L);
        assertThat(edit.getAttachmentOssIds()).containsExactly(10L, 11L);
        assertThat(edit.getVideoOssIds()).containsExactly(20L);
    }

    @Test
    void rejectsRemovedLegacyCoverField() {
        String json = """
            {"title":"标题","content":"<p>正文</p>","categoryDictCode":11,
             "coverOssId":99,"status":"0"}
            """;

        assertThatThrownBy(() -> objectMapper.readerWithView(ContentArticleBo.AddView.class)
            .forType(ContentArticleBo.class).readValue(json))
            .hasMessageContaining("coverOssId");
    }

    @Test
    void queryModelOnlyContainsExplicitFilters() throws Exception {
        Class<?> queryType = Class.forName("org.dromara.content.domain.bo.ContentArticleQuery");

        assertThat(propertyNames(queryType))
            .containsExactlyInAnyOrder("title", "categoryDictCode", "status");
    }

    private static Set<String> propertyNames(Class<?> type) throws Exception {
        return Arrays.stream(Introspector.getBeanInfo(type, Object.class).getPropertyDescriptors())
            .map(descriptor -> descriptor.getName())
            .collect(Collectors.toSet());
    }
}
