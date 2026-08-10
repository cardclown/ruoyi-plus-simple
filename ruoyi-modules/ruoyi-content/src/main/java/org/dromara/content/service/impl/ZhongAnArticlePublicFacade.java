package org.dromara.content.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.content.domain.bo.ContentArticleQuery;
import org.dromara.content.domain.vo.ContentArticlePublicVo;
import org.dromara.content.service.IContentArticlePublicFacade;
import org.dromara.content.service.support.ContentArticlePublishedQueryService;
import org.springframework.stereotype.Service;

/**
 * 中安建设官网文章门面，固定绑定有效租户 140872。
 */
@RequiredArgsConstructor
@Service
public class ZhongAnArticlePublicFacade implements IContentArticlePublicFacade {

    private static final String ZHONG_AN_TENANT_ID = "140872";

    private final ContentArticlePublishedQueryService queryService;

    @Override
    public TableDataInfo<ContentArticlePublicVo> queryPage(
            ContentArticleQuery query, PageQuery pageQuery) {
        return queryService.queryPage(ZHONG_AN_TENANT_ID, query, pageQuery);
    }

    @Override
    public ContentArticlePublicVo queryById(Long articleId) {
        return queryService.queryById(ZHONG_AN_TENANT_ID, articleId);
    }
}
