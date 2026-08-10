package org.dromara.content.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.content.domain.ContentArticleTag;

import java.util.Collection;

/**
 * 文章标签关联 Mapper。
 */
public interface ContentArticleTagMapper extends BaseMapperPlus<ContentArticleTag, ContentArticleTag> {

    /**
     * 修改文章时清理原有的单篇文章标签关系，以便随后重建。
     *
     * @param articleId 正在修改的文章 ID
     * @return 删除的标签关联记录数
     */
    default int deleteByArticleId(Long articleId) {
        return delete(new LambdaQueryWrapper<ContentArticleTag>()
            .eq(ContentArticleTag::getArticleId, articleId));
    }

    /**
     * 物理删除文章前清理多篇文章的标签关系。
     *
     * @param articleIds 待物理删除文章的 ID 集合
     * @return 删除的标签关联记录数
     */
    default int deleteByArticleIds(Collection<Long> articleIds) {
        return delete(new LambdaQueryWrapper<ContentArticleTag>()
            .in(ContentArticleTag::getArticleId, articleIds));
    }
}
