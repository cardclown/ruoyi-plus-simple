package org.dromara.content.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.content.domain.bo.ContentArticleQuery;
import org.dromara.content.domain.vo.ContentArticlePublicVo;

/**
 * 官网匿名文章查询门面，不向调用方暴露租户参数。
 */
public interface IContentArticlePublicFacade {

    TableDataInfo<ContentArticlePublicVo> queryPage(
        ContentArticleQuery query, PageQuery pageQuery);

    ContentArticlePublicVo queryById(Long articleId);
}
