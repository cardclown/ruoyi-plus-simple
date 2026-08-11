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

    private static final int PUBLIC_DEFAULT_PAGE_NUM = 1;
    private static final int PUBLIC_DEFAULT_PAGE_SIZE = 10;
    private static final int PUBLIC_MAX_PAGE_SIZE = 100;

    private final ContentArticleMapper articleMapper;
    private final ContentArticleTenantScope tenantScope;
    private final ContentArticleAttachmentManager attachmentManager;

    public TableDataInfo<ContentArticlePublicVo> queryPage(
            String tenantId, ContentArticleQuery query, PageQuery pageQuery) {
        validateTenantId(tenantId);
        ContentArticleQuery safeQuery = query == null ? new ContentArticleQuery() : query;
        return tenantScope.execute(tenantId, () -> {
            Page<ContentArticle> page = articleMapper.selectPublishedArticlePage(
                buildPublicPage(pageQuery), tenantId,
                safeQuery.getTitle(), safeQuery.getCategoryDictCode());
            var result = page.convert(this::toPublicVo);
            attachmentManager.populatePublic(result.getRecords());
            return TableDataInfo.build(result);
        });
    }

    public ContentArticlePublicVo queryById(String tenantId, Long articleId) {
        validateTenantId(tenantId);
        return tenantScope.execute(tenantId, () -> {
            ContentArticle article = articleMapper.selectPublishedArticleById(tenantId, articleId);
            if (article == null) {
                throw new ServiceException("文章不存在");
            }
            ContentArticlePublicVo vo = toPublicVo(article);
            attachmentManager.populate(vo);
            return vo;
        });
    }

    /**
     * 公开接口使用独立分页边界，避免全量或负数分页，并隔离调用方排序参数。
     */
    private Page<ContentArticle> buildPublicPage(PageQuery pageQuery) {
        Integer requestedPageNum = pageQuery == null ? null : pageQuery.getPageNum();
        Integer requestedPageSize = pageQuery == null ? null : pageQuery.getPageSize();
        int pageNum = requestedPageNum == null || requestedPageNum <= 0
            ? PUBLIC_DEFAULT_PAGE_NUM : requestedPageNum;
        int pageSize = requestedPageSize == null || requestedPageSize <= 0
            ? PUBLIC_DEFAULT_PAGE_SIZE : Math.min(requestedPageSize, PUBLIC_MAX_PAGE_SIZE);
        return new Page<>(pageNum, pageSize);
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
        vo.setPublishTime(article.getPublishTime());
        return vo;
    }
}
