package org.dromara.content.service.support;

import org.dromara.content.domain.ContentArticle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ContentArticlePublishPolicyTest {

    private final ContentArticlePublishPolicy policy = new ContentArticlePublishPolicy();

    @Test
    void keepsPublishFieldsEmptyWhenCreatingDraft() {
        ContentArticle article = new ContentArticle();
        article.setStatus("0");

        policy.applyForCreate(article, 12L, new Date(1_000L));

        assertThat(article.getPublishBy()).isNull();
        assertThat(article.getPublishTime()).isNull();
    }

    @Test
    void setsPublisherWhenCreatingPublishedArticle() {
        ContentArticle article = new ContentArticle();
        article.setStatus("1");
        Date now = new Date(1_000L);

        policy.applyForCreate(article, 12L, now);

        assertThat(article.getPublishBy()).isEqualTo(12L);
        assertThat(article.getPublishTime()).isEqualTo(now);
    }

    @Test
    void preservesOriginalPublisherWhenArticleRemainsPublished() {
        ContentArticle persisted = publishedArticle();
        ContentArticle update = new ContentArticle();
        update.setStatus("1");

        policy.applyForUpdate(update, persisted, 99L, new Date(2_000L));

        assertThat(update.getPublishBy()).isEqualTo(12L);
        assertThat(update.getPublishTime()).isEqualTo(new Date(1_000L));
    }

    @Test
    void setsCurrentPublisherWhenDraftIsPublished() {
        ContentArticle persisted = new ContentArticle();
        persisted.setStatus("0");
        ContentArticle update = new ContentArticle();
        update.setStatus("1");
        Date now = new Date(2_000L);

        policy.applyForUpdate(update, persisted, 99L, now);

        assertThat(update.getPublishBy()).isEqualTo(99L);
        assertThat(update.getPublishTime()).isEqualTo(now);
    }

    @Test
    void clearsPublisherWhenPublishedArticleReturnsToDraft() {
        ContentArticle persisted = publishedArticle();
        ContentArticle update = new ContentArticle();
        update.setStatus("0");

        policy.applyForUpdate(update, persisted, 99L, new Date(2_000L));

        assertThat(update.getPublishBy()).isNull();
        assertThat(update.getPublishTime()).isNull();
    }

    private static ContentArticle publishedArticle() {
        ContentArticle article = new ContentArticle();
        article.setStatus("1");
        article.setPublishBy(12L);
        article.setPublishTime(new Date(1_000L));
        return article;
    }
}
