package org.dromara.content.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ContentArticlePublicVoContractTest {

    @Test
    void serializesOnlyPublicArticleFieldsAndMediaLists() {
        ContentArticlePublicVo vo = new ContentArticlePublicVo();
        vo.setArticleId(100L);
        vo.setAttachmentOssIds(List.of(10L));
        vo.setVideoOssIds(List.of(20L));

        JsonNode json = new ObjectMapper().valueToTree(vo);

        assertThat(StreamSupport.stream(
                ((Iterable<String>) () -> json.fieldNames()).spliterator(), false))
            .containsExactlyInAnyOrder(
                "articleId",
                "title",
                "summary",
                "content",
                "categoryDictCode",
                "tagIds",
                "attachmentOssIds",
                "videoOssIds",
                "publishTime"
            );
        assertThat(json.get("attachmentOssIds").get(0).asLong()).isEqualTo(10L);
        assertThat(json.get("videoOssIds").get(0).asLong()).isEqualTo(20L);
        assertThat(json.has("coverOssId")).isFalse();
    }
}
