package org.dromara.content.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.dto.OssDTO;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.oss.TechnicalMediaMetadataPolicy;
import org.dromara.common.core.oss.TechnicalMediaType;
import org.dromara.common.core.service.OssService;
import org.dromara.content.domain.ContentArticleAttachment;
import org.dromara.content.domain.vo.ContentArticleVo;
import org.dromara.content.enums.ContentArticleAttachmentType;
import org.dromara.content.mapper.ContentArticleAttachmentMapper;
import org.dromara.content.mapper.ContentArticleMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 维护文章与 OSS 媒体之间的独占关联。
 */
@Component
@RequiredArgsConstructor
public class ContentArticleAttachmentManager {

    private static final String ARTICLE_REF_TYPE = "content_article";

    private final ContentArticleMapper articleMapper;
    private final ContentArticleAttachmentMapper mapper;
    private final OssService ossService;
    private final ContentArticleOperationContext operationContext;

    /**
     * 用目标图片和视频集合替换文章的全部媒体关联。
     */
    @Transactional(rollbackFor = Exception.class)
    public void replace(Long articleId, List<Long> attachmentOssIds, List<Long> videoOssIds) {
        if (articleId == null) {
            throw new ServiceException("文章ID不能为空");
        }
        List<Long> imageIds = normalizeMediaIds(attachmentOssIds, 10,
            "文章图片最多上传10张", "文章图片附件不能重复");
        List<Long> videoIds = normalizeMediaIds(videoOssIds, 5,
            "文章视频最多上传5个", "文章视频附件不能重复");
        if (!java.util.Collections.disjoint(imageIds, videoIds)) {
            throw new ServiceException("同一附件不能同时作为图片和视频");
        }

        List<Long> targetIds = new ArrayList<>(imageIds.size() + videoIds.size());
        targetIds.addAll(imageIds);
        targetIds.addAll(videoIds);
        if (articleMapper.selectByIdForUpdate(articleId) == null) {
            throw new ServiceException("文章不存在或无权操作");
        }
        List<ContentArticleAttachment> relevantRelations = selectRelevantRelationsForUpdate(articleId, targetIds);
        Map<Long, ContentArticleAttachment> currentByOssId = relevantRelations.stream()
            .filter(relation -> articleId.equals(relation.getArticleId()))
            .collect(Collectors.toMap(ContentArticleAttachment::getOssId, Function.identity(), (left, right) -> left,
                LinkedHashMap::new));
        rejectForeignRelations(articleId, targetIds, relevantRelations);

        Map<Long, OssDTO> metadataById = loadExactMetadata(targetIds);
        validateTargets(articleId, imageIds, ContentArticleAttachmentType.IMAGE, currentByOssId, metadataById);
        validateTargets(articleId, videoIds, ContentArticleAttachmentType.VIDEO, currentByOssId, metadataById);

        List<ContentArticleAttachment> inserts = new ArrayList<>();
        List<ContentArticleAttachment> updates = new ArrayList<>();
        buildChanges(articleId, imageIds, ContentArticleAttachmentType.IMAGE, currentByOssId, inserts, updates);
        buildChanges(articleId, videoIds, ContentArticleAttachmentType.VIDEO, currentByOssId, inserts, updates);
        Set<Long> targetIdSet = new HashSet<>(targetIds);
        List<ContentArticleAttachment> removals = currentByOssId.values().stream()
            .filter(relation -> !targetIdSet.contains(relation.getOssId()))
            .toList();

        // bindToBusiness 对 sys_oss 行加锁；外层事务保证后续关联失败时绑定一并回滚。
        List<Long> newIds = inserts.stream().map(ContentArticleAttachment::getOssId).toList();
        if (!newIds.isEmpty()) {
            ossService.bindToBusiness(newIds, ARTICLE_REF_TYPE, articleId.toString());
        }
        if (!inserts.isEmpty() && !mapper.insertBatch(inserts)) {
            throw new ServiceException("文章附件保存失败");
        }
        if (!updates.isEmpty() && !mapper.updateBatchById(updates)) {
            throw new ServiceException("文章附件更新失败");
        }
        if (!removals.isEmpty()) {
            List<Long> relationIds = removals.stream()
                .map(ContentArticleAttachment::getArticleAttachmentId).toList();
            if (relationIds.stream().anyMatch(java.util.Objects::isNull)
                || mapper.deleteByIds(relationIds) != relationIds.size()) {
                throw new ServiceException("文章附件删除失败");
            }
            // 置于数据库变更之后：对象删除失败会回滚关联删除。
            ossService.deleteByIds(removals.stream().map(ContentArticleAttachment::getOssId).toList());
        }
    }

    /**
     * 回显单篇文章媒体。
     */
    public void populate(ContentArticleVo article) {
        if (article != null) {
            populate(List.of(article));
        }
    }

    /**
     * 使用一次关联查询回显一批文章媒体。
     */
    public void populate(Collection<ContentArticleVo> articles) {
        if (articles == null || articles.isEmpty()) {
            return;
        }
        List<ContentArticleVo> present = articles.stream().filter(java.util.Objects::nonNull).toList();
        present.forEach(article -> {
            article.setAttachmentOssIds(new ArrayList<>());
            article.setVideoOssIds(new ArrayList<>());
        });
        List<Long> articleIds = present.stream().map(ContentArticleVo::getArticleId)
            .filter(java.util.Objects::nonNull).distinct().toList();
        if (articleIds.isEmpty()) {
            return;
        }
        Map<Long, List<ContentArticleVo>> articlesById = present.stream()
            .filter(article -> article.getArticleId() != null)
            .collect(Collectors.groupingBy(ContentArticleVo::getArticleId));
        List<ContentArticleAttachment> relations = mapper.selectByArticleIds(articleIds).stream()
            .sorted(Comparator.comparing(ContentArticleAttachment::getArticleId)
                .thenComparing(ContentArticleAttachment::getAttachmentType)
                .thenComparing(ContentArticleAttachment::getSortNum))
            .toList();
        for (ContentArticleAttachment relation : relations) {
            List<ContentArticleVo> matchingArticles = articlesById.get(relation.getArticleId());
            if (matchingArticles == null) {
                continue;
            }
            for (ContentArticleVo article : matchingArticles) {
                if (ContentArticleAttachmentType.IMAGE.getCode().equals(relation.getAttachmentType())) {
                    article.getAttachmentOssIds().add(relation.getOssId());
                } else if (ContentArticleAttachmentType.VIDEO.getCode().equals(relation.getAttachmentType())) {
                    article.getVideoOssIds().add(relation.getOssId());
                }
            }
        }
    }

    /**
     * 物理删除文章前，先删除其独占 OSS 对象和记录，再删除关联。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deletePermanently(Collection<Long> articleIds) {
        List<Long> ids = normalizeArticleIds(articleIds);
        if (ids.isEmpty()) {
            return;
        }
        List<ContentArticleAttachment> relations = mapper.selectByArticleIds(ids);
        List<Long> ossIds = relations.stream().map(ContentArticleAttachment::getOssId)
            .filter(java.util.Objects::nonNull).distinct().toList();
        if (!ossIds.isEmpty()) {
            ossService.deleteByIds(ossIds);
        }
        int deleted = mapper.deleteByArticleIds(ids);
        if (deleted != relations.size()) {
            throw new ServiceException("文章附件删除失败");
        }
    }

    private List<ContentArticleAttachment> selectRelevantRelationsForUpdate(Long articleId, List<Long> targetIds) {
        LambdaQueryWrapper<ContentArticleAttachment> query = Wrappers.lambdaQuery();
        query.eq(ContentArticleAttachment::getArticleId, articleId);
        if (!targetIds.isEmpty()) {
            query.or().in(ContentArticleAttachment::getOssId, targetIds);
        }
        return mapper.selectList(query.orderByAsc(ContentArticleAttachment::getArticleAttachmentId).last("FOR UPDATE"));
    }

    private static List<Long> normalizeMediaIds(List<Long> ids, int maximum, String countMessage,
                                                 String duplicateMessage) {
        List<Long> normalized = ids == null ? List.of() : new ArrayList<>(ids);
        if (normalized.size() > maximum) {
            throw new ServiceException(countMessage);
        }
        if (normalized.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ServiceException("附件ID不能为空");
        }
        if (new LinkedHashSet<>(normalized).size() != normalized.size()) {
            throw new ServiceException(duplicateMessage);
        }
        return normalized;
    }

    private static List<Long> normalizeArticleIds(Collection<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return List.of();
        }
        if (articleIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ServiceException("文章ID不能为空");
        }
        return new ArrayList<>(new LinkedHashSet<>(articleIds));
    }

    private Map<Long, OssDTO> loadExactMetadata(List<Long> targetIds) {
        if (targetIds.isEmpty()) {
            return Map.of();
        }
        List<OssDTO> metadata = ossService.selectByIds(targetIds);
        if (metadata == null || metadata.size() != targetIds.size()) {
            throw new ServiceException("附件不存在或无权访问");
        }
        Map<Long, OssDTO> byId = new HashMap<>();
        for (OssDTO file : metadata) {
            if (file == null || file.getOssId() == null || byId.put(file.getOssId(), file) != null) {
                throw new ServiceException("附件不存在或无权访问");
            }
        }
        if (!byId.keySet().equals(new HashSet<>(targetIds))) {
            throw new ServiceException("附件不存在或无权访问");
        }
        return byId;
    }

    private static void rejectForeignRelations(Long articleId, List<Long> targetIds,
                                               List<ContentArticleAttachment> relations) {
        Set<Long> targets = new HashSet<>(targetIds);
        boolean usedElsewhere = relations.stream().anyMatch(relation -> targets.contains(relation.getOssId())
            && !articleId.equals(relation.getArticleId()));
        if (usedElsewhere) {
            throw new ServiceException("附件已绑定其他文章");
        }
    }

    private static void validateTargets(Long articleId, List<Long> ids, ContentArticleAttachmentType type,
                                        Map<Long, ContentArticleAttachment> currentByOssId,
                                        Map<Long, OssDTO> metadataById) {
        for (Long id : ids) {
            OssDTO file = metadataById.get(id);
            validateReference(articleId, file);
            ContentArticleAttachment existing = currentByOssId.get(id);
            boolean unchangedLegacyImage = type == ContentArticleAttachmentType.IMAGE
                && existing != null
                && ContentArticleAttachmentType.IMAGE.getCode().equals(existing.getAttachmentType())
                && isBlank(file.getFileType());
            TechnicalMediaType technicalType = technicalType(type);
            if (!unchangedLegacyImage && !technicalType.name().equalsIgnoreCase(value(file.getFileType()))) {
                throw new ServiceException("附件类型与文章媒体类型不匹配");
            }
            validateMediaMetadata(file, type, technicalType);
        }
    }

    private static void validateReference(Long articleId, OssDTO file) {
        boolean unbound = isBlank(file.getRefType()) && isBlank(file.getRefId());
        boolean currentArticle = ARTICLE_REF_TYPE.equals(file.getRefType())
            && articleId.toString().equals(file.getRefId());
        if (!unbound && !currentArticle) {
            throw new ServiceException("附件已绑定其他文章");
        }
    }

    private static void validateMediaMetadata(OssDTO file, ContentArticleAttachmentType type,
                                              TechnicalMediaType technicalType) {
        Long size = file.getFileSize();
        if (size == null || size < 0) {
            throw new ServiceException("附件元数据不完整");
        }
        TechnicalMediaMetadataPolicy.validateSize(technicalType, size);
        if (TechnicalMediaMetadataPolicy.canonicalContentTypeForStoredSuffix(
            technicalType, file.getFileSuffix(), file.getContentType()).isEmpty()) {
            throw new ServiceException(type == ContentArticleAttachmentType.IMAGE
                ? "图片文件格式不合法" : "视频文件格式不合法");
        }
    }

    private static TechnicalMediaType technicalType(ContentArticleAttachmentType type) {
        return type == ContentArticleAttachmentType.IMAGE ? TechnicalMediaType.IMAGE : TechnicalMediaType.VIDEO;
    }

    private void buildChanges(Long articleId, List<Long> ids, ContentArticleAttachmentType type,
                              Map<Long, ContentArticleAttachment> currentByOssId,
                              List<ContentArticleAttachment> inserts,
                              List<ContentArticleAttachment> updates) {
        Date now = null;
        Long userId = null;
        for (int sort = 0; sort < ids.size(); sort++) {
            Long ossId = ids.get(sort);
            ContentArticleAttachment existing = currentByOssId.get(ossId);
            if (existing == null) {
                if (now == null) {
                    now = operationContext.now();
                    userId = operationContext.currentUserId();
                }
                ContentArticleAttachment relation = new ContentArticleAttachment();
                relation.setArticleId(articleId);
                relation.setOssId(ossId);
                relation.setAttachmentType(type.getCode());
                relation.setSortNum(sort);
                relation.setCreateBy(userId);
                relation.setCreateTime(now);
                inserts.add(relation);
            } else if (!type.getCode().equals(existing.getAttachmentType())
                || !Integer.valueOf(sort).equals(existing.getSortNum())) {
                existing.setAttachmentType(type.getCode());
                existing.setSortNum(sort);
                updates.add(existing);
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
