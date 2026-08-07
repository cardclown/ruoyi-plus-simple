package org.dromara.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.dto.OssDTO;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.OssService;
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
    private final OssService ossService;
    private final ContentArticleHtmlSanitizer htmlSanitizer;
    private final ContentArticlePublishPolicy publishPolicy;
    private final ContentArticleOperationContext operationContext;

    @Override
    public ContentArticleVo queryById(Long articleId) {
        ContentArticle article = articleMapper.selectArticleById(articleId);
        if (article == null) {
            throw new ServiceException("文章不存在或无权操作");
        }
        return toVo(article);
    }

    @Override
    public TableDataInfo<ContentArticleVo> queryPageList(ContentArticleQuery query, PageQuery pageQuery) {
        Page<ContentArticleVo> result = articleMapper.selectArticlePage(pageQuery.build(), buildQueryWrapper(query));
        return TableDataInfo.build(result);
    }

    @Override
    public List<ContentArticleVo> queryList(ContentArticleQuery query) {
        return articleMapper.selectArticleList(buildQueryWrapper(query));
    }

    private LambdaQueryWrapper<ContentArticle> buildQueryWrapper(ContentArticleQuery query) {
        LambdaQueryWrapper<ContentArticle> wrapper = Wrappers.lambdaQuery();
        wrapper.select(
            ContentArticle::getArticleId,
            ContentArticle::getTitle,
            ContentArticle::getSummary,
            ContentArticle::getCategoryDictCode,
            ContentArticle::getTagIds,
            ContentArticle::getCoverOssId,
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
        if (articleMapper.insert(article) != 1) {
            throw new ServiceException("文章新增失败");
        }
        bo.setArticleId(article.getArticleId());
        insertTags(article.getArticleId(), article.getTagIds());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(ContentArticleBo bo) {
        ContentArticle persisted = articleMapper.selectArticleById(bo.getArticleId());
        if (persisted == null) {
            throw new ServiceException("文章不存在或无权操作");
        }

        ContentArticle update = prepareArticle(bo);
        update.setArticleId(bo.getArticleId());
        publishPolicy.applyForUpdate(update, persisted, operationContext.currentUserId(), operationContext.now());
        if (articleMapper.updateArticleById(update) != 1) {
            throw new ServiceException("文章修改失败");
        }
        tagMapper.deleteByArticleId(update.getArticleId());
        insertTags(update.getArticleId(), update.getTagIds());
        return true;
    }

    private ContentArticle prepareArticle(ContentArticleBo bo) {
        validateStatus(bo.getStatus());
        List<Long> tagIds = dictionaryService.validateAndNormalize(bo.getCategoryDictCode(), bo.getTagIds());
        validateCoverOss(bo.getCoverOssId());

        ContentArticle article = toEntity(bo);
        article.setTagIds(tagIds);
        article.setContent(htmlSanitizer.sanitize(bo.getContent()));
        return article;
    }

    private ContentArticle toEntity(ContentArticleBo bo) {
        ContentArticle article = new ContentArticle();
        article.setTitle(bo.getTitle());
        article.setSummary(bo.getSummary());
        article.setContent(bo.getContent());
        article.setCategoryDictCode(bo.getCategoryDictCode());
        article.setTagIds(bo.getTagIds());
        article.setCoverOssId(bo.getCoverOssId());
        article.setStatus(bo.getStatus());
        return article;
    }

    private ContentArticleVo toVo(ContentArticle article) {
        ContentArticleVo vo = new ContentArticleVo();
        vo.setArticleId(article.getArticleId());
        vo.setTitle(article.getTitle());
        vo.setSummary(article.getSummary());
        vo.setContent(article.getContent());
        vo.setCategoryDictCode(article.getCategoryDictCode());
        vo.setTagIds(article.getTagIds());
        vo.setCoverOssId(article.getCoverOssId());
        vo.setStatus(article.getStatus());
        vo.setPublishBy(article.getPublishBy());
        vo.setPublishTime(article.getPublishTime());
        vo.setCreateTime(article.getCreateTime());
        vo.setUpdateTime(article.getUpdateTime());
        return vo;
    }

    private void validateStatus(String status) {
        if (!"0".equals(status) && !"1".equals(status)) {
            throw new ServiceException("发布状态只能为0或1");
        }
    }

    private void validateCoverOss(Long coverOssId) {
        if (coverOssId == null) {
            return;
        }
        List<OssDTO> files = ossService.selectByIds(coverOssId.toString());
        boolean exists = files != null && files.stream().anyMatch(file -> coverOssId.equals(file.getOssId()));
        if (!exists) {
            throw new ServiceException("封面文件不存在或不属于当前租户");
        }
    }

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
        if (deletedIds.size() != articleIds.size()) {
            throw new ServiceException("只能物理删除已逻辑删除且有权操作的文章");
        }
        tagMapper.deleteByArticleIds(articleIds);
        int deleted = articleMapper.physicalDeleteByIds(articleIds);
        if (deleted != articleIds.size()) {
            throw new ServiceException("文章物理删除失败");
        }
    }

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
