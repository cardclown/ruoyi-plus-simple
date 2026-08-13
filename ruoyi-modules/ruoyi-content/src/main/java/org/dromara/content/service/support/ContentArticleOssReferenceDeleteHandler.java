package org.dromara.content.service.support;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.OssReferenceDeleteHandler;
import org.dromara.content.domain.ContentArticleAttachment;
import org.dromara.content.mapper.ContentArticleAttachmentMapper;
import org.dromara.content.mapper.ContentArticleMapper;
import org.springframework.stereotype.Component;

/**
 * 文章附件的公共 OSS 删除处理器。
 *
 * <p>处理器同时校验文章编辑权限、租户边界、数据权限和真实附件关系。这里只删除文章关系，
 * OSS 待删除状态及对象清理由系统模块在外层独立事务中统一处理。</p>
 */
@Component
@RequiredArgsConstructor
public class ContentArticleOssReferenceDeleteHandler implements OssReferenceDeleteHandler {

    private final ContentArticleMapper articleMapper;
    private final ContentArticleAttachmentMapper attachmentMapper;
    private final ContentArticleOperationContext operationContext;

    @Override
    public String supportedReferenceType() {
        return ContentArticleAttachmentManager.ARTICLE_REF_TYPE;
    }

    @Override
    public void validateDeleteAccess(String referenceId) {
        Long articleId = parseArticleId(referenceId);
        requireEditPermission();
        if (articleMapper.selectArticleById(articleId) == null) {
            throw new ServiceException("文章不存在或无权操作");
        }
    }

    @Override
    public void deleteReference(Long ossId, String referenceId) {
        Long articleId = parseArticleId(referenceId);
        requireEditPermission();
        if (articleMapper.selectByIdForUpdate(articleId) == null) {
            throw new ServiceException("文章不存在或无权操作");
        }
        ContentArticleAttachment relation = attachmentMapper.selectByArticleAndOssForUpdate(articleId, ossId);
        if (relation == null) {
            throw new ServiceException("附件业务关联不存在");
        }
        Long relationId = relation.getArticleAttachmentId();
        if (relationId == null || attachmentMapper.deleteById(relationId) != 1) {
            throw new ServiceException("文章附件删除失败");
        }
    }

    private void requireEditPermission() {
        if (!operationContext.canEditArticle()) {
            throw new ServiceException("无权删除该附件");
        }
    }

    private Long parseArticleId(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            throw new ServiceException("附件归属数据格式错误");
        }
        try {
            return Long.valueOf(referenceId);
        } catch (NumberFormatException exception) {
            throw new ServiceException("附件归属数据格式错误");
        }
    }
}
