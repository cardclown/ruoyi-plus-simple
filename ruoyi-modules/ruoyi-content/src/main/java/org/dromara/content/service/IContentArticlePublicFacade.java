package org.dromara.content.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.content.domain.bo.ContentArticleQuery;
import org.dromara.content.domain.vo.ContentArticleMediaGroupVo;
import org.dromara.content.domain.vo.ContentArticlePublicVo;

import java.util.List;

/**
 * 官网匿名文章查询门面，不向调用方暴露租户参数。
 */
public interface IContentArticlePublicFacade {

    TableDataInfo<ContentArticlePublicVo> queryPage(
        ContentArticleQuery query, PageQuery pageQuery);

    ContentArticlePublicVo queryById(Long articleId);

    /**
     * 批量解析公开文章媒体；返回项与去重后的请求文章顺序一致。
     *
     * @param articleIds 文章 ID 集合
     * @return 按文章分组的媒体元数据
     */
    List<ContentArticleMediaGroupVo> queryMedia(List<Long> articleIds);
}
