package org.dromara.content.service.support;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.content.domain.ContentArticle;
import org.dromara.content.domain.bo.ContentArticleQuery;
import org.dromara.content.domain.vo.ContentArticlePublicVo;
import org.dromara.content.mapper.ContentArticleMapper;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ContentArticlePublishedQueryService {

    private final ContentArticleMapper articleMapper;
    private final ContentArticleTenantScope tenantScope;

    public TableDataInfo<ContentArticlePublicVo> queryPage(
            String tenantId, ContentArticleQuery query, PageQuery pageQuery) {
        validateTenantId(tenantId);
        ContentArticleQuery safeQuery = query == null ? new ContentArticleQuery() : query;
        return tenantScope.execute(tenantId, () -> {
            Page<ContentArticle> page = articleMapper.selectPublishedArticlePage(
                pageQuery.build(), tenantId, safeQuery.getTitle(), safeQuery.getCategoryDictCode());
            return TableDataInfo.build(page.convert(this::toPublicVo));
        });
    }

    public ContentArticlePublicVo queryById(String tenantId, Long articleId) {
        validateTenantId(tenantId);
        return tenantScope.execute(tenantId, () -> {
            ContentArticle article = articleMapper.selectPublishedArticleById(tenantId, articleId);
            if (article == null) {
                throw new ServiceException("文章不存在");
            }
            return toPublicVo(article);
        });
    }

    private void validateTenantId(String tenantId) {
        if (StringUtils.isBlank(tenantId)) {
            throw new ServiceException("租户ID不能为空");
        }
    }

    private ContentArticlePublicVo toPublicVo(ContentArticle article) {
        ContentArticlePublicVo vo = new ContentArticlePublicVo();
        vo.setArticleId(article.getArticleId());
        vo.setTitle(article.getTitle());
        vo.setSummary(article.getSummary());
        vo.setContent(article.getContent());
        vo.setCategoryDictCode(article.getCategoryDictCode());
        vo.setTagIds(article.getTagIds());
        vo.setCoverOssId(article.getCoverOssId());
        vo.setPublishTime(article.getPublishTime());
        return vo;
    }
}
