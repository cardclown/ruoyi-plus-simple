package org.dromara.content.service.support;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.content.domain.ContentArticle;
import org.dromara.content.domain.ContentArticleAttachment;
import org.dromara.content.mapper.ContentArticleAttachmentMapper;
import org.dromara.content.mapper.ContentArticleMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 文章附件接入公共 OSS 删除接口的权限和关系一致性测试。
 */
@Tag("dev")
class ContentArticleOssReferenceDeleteHandlerTest {

    private final ContentArticleMapper articleMapper = mock(ContentArticleMapper.class);
    private final ContentArticleAttachmentMapper attachmentMapper = mock(ContentArticleAttachmentMapper.class);
    private final ContentArticleOperationContext operationContext = mock(ContentArticleOperationContext.class);
    private final ContentArticleOssReferenceDeleteHandler handler =
        new ContentArticleOssReferenceDeleteHandler(articleMapper, attachmentMapper, operationContext);

    @Test
    void deletesOnlyTheRelationOwnedByTheAuthorizedArticle() {
        when(operationContext.canEditArticle()).thenReturn(true);
        when(articleMapper.selectByIdForUpdate(100L)).thenReturn(article(100L));
        when(attachmentMapper.selectByArticleAndOssForUpdate(100L, 10L))
            .thenReturn(relation(1L, 100L, 10L));
        when(attachmentMapper.deleteById(1L)).thenReturn(1);

        handler.deleteReference(10L, "100");

        verify(attachmentMapper).deleteById(1L);
    }

    @Test
    void rejectsDeletionWithoutArticleEditPermissionBeforeReadingBusinessData() {
        when(operationContext.canEditArticle()).thenReturn(false);

        assertThatThrownBy(() -> handler.deleteReference(10L, "100"))
            .isInstanceOf(ServiceException.class)
            .hasMessage("无权删除该附件");

        verifyNoInteractions(articleMapper, attachmentMapper);
    }

    @Test
    void rejectsArticleOutsideCurrentUsersDataPermission() {
        when(operationContext.canEditArticle()).thenReturn(true);
        when(articleMapper.selectByIdForUpdate(100L)).thenReturn(null);

        assertThatThrownBy(() -> handler.deleteReference(10L, "100"))
            .isInstanceOf(ServiceException.class)
            .hasMessage("文章不存在或无权操作");

        verifyNoInteractions(attachmentMapper);
    }

    @Test
    void rejectsOssThatIsNotActuallyRelatedToThePersistedOwner() {
        when(operationContext.canEditArticle()).thenReturn(true);
        when(articleMapper.selectByIdForUpdate(100L)).thenReturn(article(100L));
        when(attachmentMapper.selectByArticleAndOssForUpdate(100L, 10L)).thenReturn(null);

        assertThatThrownBy(() -> handler.deleteReference(10L, "100"))
            .isInstanceOf(ServiceException.class)
            .hasMessage("附件业务关联不存在");

        verify(attachmentMapper, never()).deleteById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void pendingRetryChecksArticlePermissionWithoutRequiringDeletedRelation() {
        when(operationContext.canEditArticle()).thenReturn(true);
        when(articleMapper.selectArticleById(100L)).thenReturn(article(100L));

        handler.validateDeleteAccess("100");

        verify(articleMapper).selectArticleById(100L);
        verifyNoInteractions(attachmentMapper);
    }

    @Test
    void exposesTheSameStableReferenceTypeUsedByArticleBinding() {
        assertThat(handler.supportedReferenceType()).isEqualTo(ContentArticleAttachmentManager.ARTICLE_REF_TYPE);
    }

    private static ContentArticle article(Long articleId) {
        ContentArticle article = new ContentArticle();
        article.setArticleId(articleId);
        return article;
    }

    private static ContentArticleAttachment relation(Long relationId, Long articleId, Long ossId) {
        ContentArticleAttachment relation = new ContentArticleAttachment();
        relation.setArticleAttachmentId(relationId);
        relation.setArticleId(articleId);
        relation.setOssId(ossId);
        return relation;
    }
}
