package org.dromara.content.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.content.domain.ContentArticleAttachment;

import java.util.Collection;
import java.util.List;

/**
 * 文章附件关联 Mapper。
 */
public interface ContentArticleAttachmentMapper
    extends BaseMapperPlus<ContentArticleAttachment, ContentArticleAttachment> {

    /**
     * 按文章 ID 集合读取附件，并按文章、类型和排序号稳定排序。
     *
     * @param articleIds 文章 ID 集合
     * @return 附件关联记录
     */
    default List<ContentArticleAttachment> selectByArticleIds(Collection<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<ContentArticleAttachment>()
            .in(ContentArticleAttachment::getArticleId, articleIds)
            .orderByAsc(ContentArticleAttachment::getArticleId)
            .orderByAsc(ContentArticleAttachment::getAttachmentType)
            .orderByAsc(ContentArticleAttachment::getSortNum));
    }

    /**
     * 按文章 ID 集合删除附件关联。
     *
     * @param articleIds 文章 ID 集合
     * @return 删除的附件关联记录数
     */
    default int deleteByArticleIds(Collection<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return 0;
        }
        return delete(new LambdaQueryWrapper<ContentArticleAttachment>()
            .in(ContentArticleAttachment::getArticleId, articleIds));
    }
}
