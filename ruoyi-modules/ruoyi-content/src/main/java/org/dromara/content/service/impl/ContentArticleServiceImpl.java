package org.dromara.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.content.domain.ContentArticle;
import org.dromara.content.domain.ContentArticleTag;
import org.dromara.content.domain.bo.ContentArticleBo;
import org.dromara.content.domain.bo.ContentArticleQuery;
import org.dromara.content.domain.vo.ContentArticleVo;
import org.dromara.content.mapper.ContentArticleMapper;
import org.dromara.content.mapper.ContentArticleTagMapper;
import org.dromara.content.service.IContentArticleService;
import org.dromara.content.service.support.ContentArticleAttachmentManager;
import org.dromara.content.service.support.ContentArticleDictionaryService;
import org.dromara.content.service.support.ContentArticleHtmlSanitizer;
import org.dromara.content.service.support.ContentArticleOperationContext;
import org.dromara.content.service.support.ContentArticlePublishPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 文章 Service 业务层处理。
 */
@RequiredArgsConstructor
@Service
public class ContentArticleServiceImpl implements IContentArticleService {

    private final ContentArticleMapper articleMapper;
    private final ContentArticleTagMapper tagMapper;
    private final ContentArticleDictionaryService dictionaryService;
    private final ContentArticleHtmlSanitizer htmlSanitizer;
    private final ContentArticlePublishPolicy publishPolicy;
    private final ContentArticleOperationContext operationContext;
    private final ContentArticleAttachmentManager attachmentManager;

    @Override
    public ContentArticleVo queryById(Long articleId) {
        ContentArticle article = articleMapper.selectArticleById(articleId);
        if (article == null) {
            throw new ServiceException("文章不存在或无权操作");
        }
        ContentArticleVo vo = toVo(article);
        attachmentManager.populate(vo);
        return vo;
    }

    @Override
    public TableDataInfo<ContentArticleVo> queryPageList(ContentArticleQuery query, PageQuery pageQuery) {
        Page<ContentArticleVo> result = articleMapper.selectArticlePage(pageQuery.build(), buildQueryWrapper(query));
        attachmentManager.populate(result.getRecords());
        return TableDataInfo.build(result);
    }

    @Override
    public List<ContentArticleVo> queryList(ContentArticleQuery query) {
        List<ContentArticleVo> articles = articleMapper.selectArticleList(buildQueryWrapper(query));
        attachmentManager.populate(articles);
        return articles;
    }

    /**
     * 构造文章列表与导出查询条件，显式限定查询字段集合，并仅应用标题、分类和发布状态三个允许的筛选项。
     *
     * @param query 文章查询条件
     * @return 限定查询字段、筛选条件和排序规则的查询包装器
     */
    private LambdaQueryWrapper<ContentArticle> buildQueryWrapper(ContentArticleQuery query) {
        LambdaQueryWrapper<ContentArticle> wrapper = Wrappers.lambdaQuery();
        wrapper.select(
            ContentArticle::getArticleId,
            ContentArticle::getTitle,
            ContentArticle::getSummary,
            ContentArticle::getContent,
            ContentArticle::getCategoryDictCode,
            ContentArticle::getTagIds,
            ContentArticle::getStatus,
            ContentArticle::getPublishBy,
            ContentArticle::getPublishTime,
            ContentArticle::getCreateDept,
            ContentArticle::getCreateBy,
            ContentArticle::getCreateTime,
            ContentArticle::getUpdateBy,
            ContentArticle::getUpdateTime
        );
        wrapper.orderByDesc(ContentArticle::getArticleId);
        wrapper.like(StringUtils.isNotBlank(query.getTitle()), ContentArticle::getTitle, query.getTitle());
        wrapper.eq(query.getCategoryDictCode() != null, ContentArticle::getCategoryDictCode, query.getCategoryDictCode());
        wrapper.eq(StringUtils.isNotBlank(query.getStatus()), ContentArticle::getStatus, query.getStatus());
        return wrapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(ContentArticleBo bo) {
        ContentArticle article = prepareArticle(bo);
        publishPolicy.applyForCreate(article, operationContext.currentUserId(), operationContext.now());
        // 新增时不预设 articleId，由 Mapper 插入采用的雪花 ID 策略生成并回填实体。
        if (articleMapper.insert(article) != 1) {
            throw new ServiceException("文章新增失败");
        }
        bo.setArticleId(article.getArticleId());
        insertTags(article.getArticleId(), article.getTagIds());
        attachmentManager.replace(article.getArticleId(), bo.getAttachmentOssIds(), bo.getVideoOssIds());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(ContentArticleBo bo) {
        ContentArticle persisted = articleMapper.selectArticleById(bo.getArticleId());
        if (persisted == null) {
            throw new ServiceException("文章不存在或无权操作");
        }
        if ("1".equals(persisted.getStatus())) {
            throw new ServiceException("已发布文章请先撤回为草稿后再修改");
        }

        ContentArticle update = prepareArticle(bo);
        // 修改必须显式沿用请求中的 ID；prepareArticle 的新增映射边界故意不会复制 ID。
        update.setArticleId(bo.getArticleId());
        publishPolicy.applyForUpdate(update, persisted, operationContext.currentUserId(), operationContext.now());
        if (articleMapper.updateArticleById(update) != 1) {
            throw new ServiceException("文章修改失败");
        }
        tagMapper.deleteByArticleId(update.getArticleId());
        insertTags(update.getArticleId(), update.getTagIds());
        attachmentManager.replace(update.getArticleId(), bo.getAttachmentOssIds(), bo.getVideoOssIds());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean changeStatus(Long articleId, String status) {
        validateStatus(status);
        ContentArticle persisted = articleMapper.selectArticleById(articleId);
        if (persisted == null) {
            throw new ServiceException("文章不存在或无权操作");
        }
        if ("1".equals(persisted.getStatus()) && "1".equals(status)) {
            throw new ServiceException("文章已发布，请先撤回后再发布");
        }
        if ("0".equals(persisted.getStatus()) && "0".equals(status)) {
            return true;
        }

        Long currentUserId = operationContext.currentUserId();
        Date now = operationContext.now();
        ContentArticle update = new ContentArticle();
        update.setArticleId(articleId);
        update.setStatus(status);
        publishPolicy.applyForUpdate(update, persisted, currentUserId, now);
        update.setUpdateBy(currentUserId);
        update.setUpdateTime(now);
        if (articleMapper.updateArticleStatus(update) != 1) {
            throw new ServiceException("文章状态修改失败");
        }
        return true;
    }

    /**
     * 统一准备待持久化文章，依次完成状态、租户字典和富文本安全校验。
     *
     * @param bo 客户端提交的文章业务对象
     * @return 已规范化标签并完成富文本过滤的文章实体
     */
    private ContentArticle prepareArticle(ContentArticleBo bo) {
        validateStatus(bo.getStatus());
        List<Long> tagIds = dictionaryService.validateAndNormalize(bo.getCategoryDictCode(), bo.getTagIds());
        ContentArticle article = toEntity(bo);
        article.setTagIds(tagIds);
        article.setContent(htmlSanitizer.sanitize(bo.getContent()));
        return article;
    }

    /**
     * 将业务对象映射为文章实体，只复制客户端允许控制的业务字段，故意排除 ID 和审计字段。
     *
     * @param bo 客户端提交的文章业务对象
     * @return 仅包含客户端可控业务字段的文章实体
     */
    private ContentArticle toEntity(ContentArticleBo bo) {
        ContentArticle article = new ContentArticle();
        article.setTitle(bo.getTitle());
        article.setSummary(bo.getSummary());
        article.setContent(bo.getContent());
        article.setCategoryDictCode(bo.getCategoryDictCode());
        article.setTagIds(bo.getTagIds());
        article.setStatus(bo.getStatus());
        return article;
    }

    /**
     * 将详情查询得到的文章实体转换为响应对象。
     *
     * @param article 文章实体
     * @return 文章详情响应对象
     */
    private ContentArticleVo toVo(ContentArticle article) {
        ContentArticleVo vo = new ContentArticleVo();
        vo.setArticleId(article.getArticleId());
        vo.setTitle(article.getTitle());
        vo.setSummary(article.getSummary());
        vo.setContent(article.getContent());
        vo.setCategoryDictCode(article.getCategoryDictCode());
        vo.setTagIds(article.getTagIds());
        vo.setStatus(article.getStatus());
        vo.setPublishBy(article.getPublishBy());
        vo.setPublishTime(article.getPublishTime());
        vo.setCreateTime(article.getCreateTime());
        vo.setUpdateTime(article.getUpdateTime());
        return vo;
    }

    /**
     * 在服务层防御性校验发布状态，避免绕过入参校验后写入非法状态。
     *
     * @param status 发布状态，只允许草稿 {@code 0} 或已发布 {@code 1}
     */
    private void validateStatus(String status) {
        if (!"0".equals(status) && !"1".equals(status)) {
            throw new ServiceException("发布状态只能为0或1");
        }
    }

    /**
     * 批量创建文章与标签的关联，并由后台统一写入当前操作人和操作时间。
     *
     * @param articleId 文章 ID
     * @param tagIds 已校验并规范化的标签字典编码
     */
    private void insertTags(Long articleId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        Long currentUserId = operationContext.currentUserId();
        Date now = operationContext.now();
        List<ContentArticleTag> relations = new ArrayList<>(tagIds.size());
        for (Long tagId : tagIds) {
            ContentArticleTag relation = new ContentArticleTag();
            relation.setArticleId(articleId);
            relation.setTagDictCode(tagId);
            relation.setCreateBy(currentUserId);
            relation.setCreateTime(now);
            relations.add(relation);
        }
        if (!tagMapper.insertBatch(relations)) {
            throw new ServiceException("文章标签保存失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        List<Long> articleIds = normalizeIds(ids);
        List<Long> existingIds = articleMapper.selectExistingArticleIds(articleIds);
        // 删除前核对所有 ID 均可操作，防止只删除其中一部分而造成部分成功。
        if (existingIds.size() != articleIds.size()) {
            throw new ServiceException("部分文章不存在或无权操作");
        }
        int deleted = articleMapper.logicalDeleteByIds(articleIds);
        if (deleted != articleIds.size()) {
            throw new ServiceException("文章删除失败");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void physicalDeleteByIds(Collection<Long> ids) {
        List<Long> articleIds = normalizeIds(ids);
        List<Long> deletedIds = articleMapper.selectDeletedArticleIds(articleIds);
        // 物理删除前核对所有 ID 均已逻辑删除且可操作，防止部分成功。
        if (deletedIds.size() != articleIds.size()) {
            throw new ServiceException("只能物理删除已逻辑删除且有权操作的文章");
        }
        // 删除附件关联并标记独占 OSS 待删除，再清理标签和文章；对象仅在提交后删除。
        attachmentManager.deletePermanently(articleIds);
        tagMapper.deleteByArticleIds(articleIds);
        int deleted = articleMapper.physicalDeleteByIds(articleIds);
        if (deleted != articleIds.size()) {
            throw new ServiceException("文章物理删除失败");
        }
    }

    /**
     * 规范化批量操作 ID：拒绝空集合和空 ID，去重并保持调用方的原始输入顺序。
     *
     * @param ids 待操作的文章 ID 集合
     * @return 去重且保持原输入顺序的文章 ID 列表
     */
    private List<Long> normalizeIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ServiceException("文章ID不能为空");
        }
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(ids);
        if (uniqueIds.contains(null)) {
            throw new ServiceException("文章ID不能为空");
        }
        return new ArrayList<>(uniqueIds);
    }
}
