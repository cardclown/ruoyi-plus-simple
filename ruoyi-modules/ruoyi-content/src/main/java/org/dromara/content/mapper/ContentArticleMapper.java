package org.dromara.content.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.annotation.DataColumn;
import org.dromara.common.mybatis.annotation.DataPermission;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.content.domain.ContentArticle;
import org.dromara.content.domain.vo.ContentArticleVo;

import java.util.Collection;
import java.util.List;

/**
 * 文章 Mapper 接口。
 * <p>
 * 本接口显式声明的查询、更新和删除方法使用 {@code @DataPermission}；租户插件另行约束租户边界。
 *
 * @author Lion Li
 * @date 2026-08-07
 */
public interface ContentArticleMapper extends BaseMapperPlus<ContentArticle, ContentArticleVo> {

    /**
     * 按指定租户分页查询已发布且未删除的公开文章。
     */
    Page<ContentArticle> selectPublishedArticlePage(
        Page<ContentArticle> page,
        @Param("tenantId") String tenantId,
        @Param("title") String title,
        @Param("categoryDictCode") Long categoryDictCode);

    /**
     * 按指定租户查询单篇已发布且未删除的公开文章。
     */
    ContentArticle selectPublishedArticleById(
        @Param("tenantId") String tenantId,
        @Param("articleId") Long articleId);

    /**
     * 在数据权限范围内读取单篇文章。
     *
     * @param articleId 文章 ID
     * @return 可见的文章；不存在或无权限时返回 {@code null}
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default ContentArticle selectArticleById(Long articleId) {
        return selectOne(new LambdaQueryWrapper<ContentArticle>()
            .eq(ContentArticle::getArticleId, articleId));
    }

    /**
     * 在附件全量替换前锁定单篇文章，包括尚无任何附件关联的文章。
     *
     * @param articleId 文章 ID
     * @return 已锁定的文章；不存在或无权时返回 {@code null}
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default ContentArticle selectByIdForUpdate(Long articleId) {
        return selectOne(new LambdaQueryWrapper<ContentArticle>()
            .eq(ContentArticle::getArticleId, articleId)
            .last("FOR UPDATE"));
    }

    /**
     * 在数据权限范围内分页读取文章视图对象。
     *
     * @param page 分页参数
     * @param wrapper 查询条件
     * @return 包含文章视图对象的分页结果
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default Page<ContentArticleVo> selectArticlePage(Page<ContentArticle> page, Wrapper<ContentArticle> wrapper) {
        return selectVoPage(page, wrapper);
    }

    /**
     * 在数据权限范围内读取文章视图对象，用于不分页导出。
     *
     * @param wrapper 查询条件
     * @return 可导出的文章视图对象列表
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default List<ContentArticleVo> selectArticleList(Wrapper<ContentArticle> wrapper) {
        return selectVoList(wrapper);
    }

    /**
     * 在批量操作前核对数据权限范围内可见且未逻辑删除的文章 ID。
     *
     * @param articleIds 待核对的文章 ID 集合
     * @return 实际可操作的文章 ID 列表
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default List<Long> selectExistingArticleIds(Collection<Long> articleIds) {
        return selectObjs(new LambdaQueryWrapper<ContentArticle>()
            .select(ContentArticle::getArticleId)
            .in(ContentArticle::getArticleId, articleIds), value -> (Long) value);
    }

    /**
     * 更新文章；更新操作同样受数据权限限制。
     *
     * @param article 待更新的文章
     * @return 受影响的记录数
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default int updateArticleById(ContentArticle article) {
        return updateById(article);
    }

    /**
     * 仅更新文章状态与发布、更新审计字段。
     *
     * @param article 包含文章 ID、目标状态和审计字段的部分实体
     * @return 受影响的记录数
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    int updateArticleStatus(@Param("article") ContentArticle article);

    /**
     * 通过 MyBatis-Plus 执行文章逻辑删除。
     *
     * @param articleIds 待逻辑删除的文章 ID 集合
     * @return 受影响的记录数
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default int logicalDeleteByIds(Collection<Long> articleIds) {
        return delete(new LambdaQueryWrapper<ContentArticle>()
            .in(ContentArticle::getArticleId, articleIds));
    }

    /**
     * 仅从已逻辑删除的文章中筛选数据权限范围内可物理删除的 ID。
     *
     * @param articleIds 待筛选的文章 ID 集合
     * @return 可物理删除的文章 ID 列表
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    List<Long> selectDeletedArticleIds(@Param("articleIds") Collection<Long> articleIds);

    /**
     * 真正删除数据权限范围内已逻辑删除的文章。
     *
     * @param articleIds 待物理删除的文章 ID 集合
     * @return 受影响的记录数
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    int physicalDeleteByIds(@Param("articleIds") Collection<Long> articleIds);
}
