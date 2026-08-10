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

    /**
     * 根据文章 ID 查询当前租户和数据权限范围内的文章详情。
     *
     * @param articleId 文章 ID
     * @return 文章详情
     */
    ContentArticleVo queryById(Long articleId);

    /**
     * 分页查询当前数据权限范围内的文章，分页参数独立于业务筛选条件。
     *
     * @param query 文章业务筛选条件
     * @param pageQuery 分页与排序参数
     * @return 分页文章数据
     */
    TableDataInfo<ContentArticleVo> queryPageList(ContentArticleQuery query, PageQuery pageQuery);

    /**
     * 查询当前数据权限范围内的文章用于导出，查询结果不分页。
     *
     * @param query 文章业务筛选条件
     * @return 导出文章数据
     */
    List<ContentArticleVo> queryList(ContentArticleQuery query);

    /**
     * 新增文章，文章 ID 由持久层生成。
     *
     * @param bo 新增文章业务数据
     * @return 新增结果
     */
    Boolean insertByBo(ContentArticleBo bo);

    /**
     * 修改文章，使用请求中的文章 ID 定位待修改数据。
     *
     * @param bo 修改文章业务数据
     * @return 修改结果
     */
    Boolean updateByBo(ContentArticleBo bo);

    /**
     * 逻辑删除文章并保留数据。
     *
     * @param ids 待删除的文章 ID 集合
     * @param isValid 是否校验文章 ID 集合的数据权限和存在性
     * @return 删除结果
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    /**
     * 物理删除文章，仅接受已逻辑删除的数据。
     *
     * @param articleIds 已逻辑删除的文章 ID 集合
     */
    void physicalDeleteByIds(Collection<Long> articleIds);
}
