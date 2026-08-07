package org.dromara.content.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.content.domain.bo.ContentArticleBo;
import org.dromara.content.domain.bo.ContentArticleQuery;
import org.dromara.content.domain.vo.ContentArticleVo;

import java.util.Collection;
import java.util.List;

/**
 * 文章Service接口
 *
 * @author Lion Li
 * @date 2026-08-07
 */
public interface IContentArticleService {

    ContentArticleVo queryById(Long articleId);

    TableDataInfo<ContentArticleVo> queryPageList(ContentArticleQuery query, PageQuery pageQuery);

    List<ContentArticleVo> queryList(ContentArticleQuery query);

    Boolean insertByBo(ContentArticleBo bo);

    Boolean updateByBo(ContentArticleBo bo);

    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    void physicalDeleteByIds(Collection<Long> articleIds);
}
