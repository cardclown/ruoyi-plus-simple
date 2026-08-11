package org.dromara.content.domain.bo;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.dromara.common.core.validate.AddGroup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ContentArticleBoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void allowsNullTags() {
        ContentArticleBo article = validArticle();
        article.setTagIds(null);

        assertThat(validator.validate(article, AddGroup.class))
            .noneMatch(violation -> "tagIds".equals(violation.getPropertyPath().toString()));
    }

    @Test
    void allowsEmptyTags() {
        ContentArticleBo article = validArticle();
        article.setTagIds(List.of());

        assertThat(validator.validate(article, AddGroup.class))
            .noneMatch(violation -> "tagIds".equals(violation.getPropertyPath().toString()));
    }

    @Test
    void allowsTenTagCodes() {
        ContentArticleBo article = validArticle();
        article.setTagIds(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L));

        assertThat(validator.validate(article, AddGroup.class))
            .noneMatch(violation -> "tagIds".equals(violation.getPropertyPath().toString()));
    }

    @Test
    void rejectsElevenTagCodes() {
        ContentArticleBo article = validArticle();
        article.setTagIds(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L));

        assertThat(validator.validate(article, AddGroup.class))
            .anyMatch(violation -> "tagIds".equals(violation.getPropertyPath().toString()));
    }

    @Test
    void rejectsUnknownStatus() {
        ContentArticleBo article = validArticle();
        article.setStatus("2");

        assertThat(validator.validate(article, AddGroup.class))
            .anyMatch(violation -> "status".equals(violation.getPropertyPath().toString()));
    }

    @Test
    void acceptsTenImagesAndFiveVideos() {
        ContentArticleBo article = validArticle();
        article.setAttachmentOssIds(LongStream.rangeClosed(1, 10).boxed().toList());
        article.setVideoOssIds(LongStream.rangeClosed(11, 15).boxed().toList());

        assertThat(validator.validate(article, AddGroup.class)).isEmpty();
    }

    @Test
    void rejectsElevenImages() {
        ContentArticleBo article = validArticle();
        article.setAttachmentOssIds(LongStream.rangeClosed(1, 11).boxed().toList());

        assertThat(validator.validate(article, AddGroup.class))
            .extracting(violation -> violation.getMessage())
            .contains("文章图片最多上传10张");
    }

    @Test
    void rejectsSixVideos() {
        ContentArticleBo article = validArticle();
        article.setVideoOssIds(LongStream.rangeClosed(1, 6).boxed().toList());

        assertThat(validator.validate(article, AddGroup.class))
            .extracting(violation -> violation.getMessage())
            .contains("文章视频最多上传5个");
    }

    @Test
    void rejectsNullMediaIds() {
        ContentArticleBo article = validArticle();
        article.setAttachmentOssIds(java.util.Arrays.asList(10L, null));
        article.setVideoOssIds(java.util.Arrays.asList(20L, null));

        assertThat(validator.validate(article, AddGroup.class))
            .extracting(violation -> violation.getMessage())
            .contains("文章图片附件ID不能为空", "文章视频附件ID不能为空");
    }

    private static ContentArticleBo validArticle() {
        ContentArticleBo article = new ContentArticleBo();
        article.setTitle("公司新闻");
        article.setContent("<p>正文</p>");
        article.setCategoryDictCode(36L);
        article.setStatus("0");
        return article;
    }
}
