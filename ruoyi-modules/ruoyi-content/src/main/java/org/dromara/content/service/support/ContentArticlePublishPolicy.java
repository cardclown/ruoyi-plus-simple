package org.dromara.content.service.support;

import org.dromara.content.domain.ContentArticle;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 统一维护文章发布人和发布时间，禁止信任客户端传入值。
 */
@Component
public class ContentArticlePublishPolicy {

    private static final String PUBLISHED = "1";

    /**
     * 应用新建文章的发布规则：新建草稿清空发布信息，新建已发布文章记录当前用户和当前时间。
     *
     * @param article 待新增的文章实体
     * @param currentUserId 本次操作人 ID
     * @param now 本次操作时间
     */
    public void applyForCreate(ContentArticle article, Long currentUserId, Date now) {
        if (PUBLISHED.equals(article.getStatus())) {
            article.setPublishBy(currentUserId);
            article.setPublishTime(now);
        } else {
            clearPublishFields(article);
        }
    }

    /**
     * 应用修改文章的发布规则：已发布保持已发布时保留首次发布人和首次发布时间，草稿变已发布时记录
     * 本次操作人和时间，任意状态变草稿时清空发布信息。
     *
     * @param update 待更新的文章实体
     * @param persisted 数据库中当前文章实体
     * @param currentUserId 本次操作人 ID
     * @param now 本次操作时间
     */
    public void applyForUpdate(ContentArticle update, ContentArticle persisted, Long currentUserId, Date now) {
        if (!PUBLISHED.equals(update.getStatus())) {
            clearPublishFields(update);
            return;
        }
        if (PUBLISHED.equals(persisted.getStatus())) {
            update.setPublishBy(persisted.getPublishBy());
            update.setPublishTime(persisted.getPublishTime());
        } else {
            update.setPublishBy(currentUserId);
            update.setPublishTime(now);
        }
    }

    private void clearPublishFields(ContentArticle article) {
        article.setPublishBy(null);
        article.setPublishTime(null);
    }
}
