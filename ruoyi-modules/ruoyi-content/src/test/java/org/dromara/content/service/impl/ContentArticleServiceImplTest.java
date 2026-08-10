package org.dromara.content.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.OssService;
import org.dromara.content.domain.ContentArticle;
import org.dromara.content.domain.ContentArticleTag;
import org.dromara.content.domain.bo.ContentArticleBo;
import org.dromara.content.mapper.ContentArticleMapper;
import org.dromara.content.mapper.ContentArticleTagMapper;
import org.dromara.content.service.support.ContentArticleDictionaryService;
import org.dromara.content.service.support.ContentArticleHtmlSanitizer;
import org.dromara.content.service.support.ContentArticleOperationContext;
import org.dromara.content.service.support.ContentArticlePublishPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class ContentArticleServiceImplTest {

    private final ContentArticleMapper articleMapper = mock(ContentArticleMapper.class);
    private final ContentArticleTagMapper tagMapper = mock(ContentArticleTagMapper.class);
    private final ContentArticleDictionaryService dictionaryService = mock(ContentArticleDictionaryService.class);
    private final OssService ossService = mock(OssService.class);
    private final ContentArticleOperationContext operationContext = mock(ContentArticleOperationContext.class);
    private ContentArticleServiceImpl service;

    @BeforeEach
    void setUp() {
        when(operationContext.currentUserId()).thenReturn(9L);
        when(operationContext.now()).thenReturn(new Date(1_000L));
        service = new ContentArticleServiceImpl(
            articleMapper,
            tagMapper,
            dictionaryService,
            ossService,
            new ContentArticleHtmlSanitizer(),
            new ContentArticlePublishPolicy(),
            operationContext
        );
    }

    @Test
    void insertsArticleAndTagRelationsTogether() {
        ContentArticleBo bo = articleBo();
        bo.setContent("<p onclick=\"bad()\">正文</p>");
        bo.setTagIds(List.of(22L, 21L, 22L));
        when(dictionaryService.validateAndNormalize(11L, bo.getTagIds())).thenReturn(List.of(22L, 21L));
        when(articleMapper.insert(any(ContentArticle.class))).thenAnswer(invocation -> {
            invocation.<ContentArticle>getArgument(0).setArticleId(100L);
            return 1;
        });
        when(tagMapper.insertBatch(anyList())).thenReturn(true);

        assertThat(service.insertByBo(bo)).isTrue();

        ArgumentCaptor<ContentArticle> articleCaptor = ArgumentCaptor.forClass(ContentArticle.class);
        verify(articleMapper).insert(articleCaptor.capture());
        assertThat(articleCaptor.getValue().getTagIds()).containsExactly(22L, 21L);
        assertThat(articleCaptor.getValue().getContent()).isEqualTo("<p>正文</p>");
        assertThat(bo.getArticleId()).isEqualTo(100L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ContentArticleTag>> relationsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(tagMapper).insertBatch(relationsCaptor.capture());
        assertThat(relationsCaptor.getValue())
            .extracting(ContentArticleTag::getArticleId, ContentArticleTag::getTagDictCode)
            .containsExactly(tuple(100L, 22L), tuple(100L, 21L));
    }

    @Test
    void insertNeverCopiesClientSuppliedArticleId() {
        ContentArticleBo bo = articleBo();
        bo.setArticleId(999L);
        AtomicReference<Long> idSeenBeforeInsert = new AtomicReference<>();
        when(dictionaryService.validateAndNormalize(11L, null)).thenReturn(List.of());
        when(articleMapper.insert(any(ContentArticle.class))).thenAnswer(invocation -> {
            ContentArticle article = invocation.getArgument(0);
            idSeenBeforeInsert.set(article.getArticleId());
            article.setArticleId(100L);
            return 1;
        });

        assertThat(service.insertByBo(bo)).isTrue();

        assertThat(idSeenBeforeInsert.get()).isNull();
        assertThat(bo.getArticleId()).isEqualTo(100L);
    }

    @Test
    void throwsWhenTagRelationsCannotBeInserted() {
        ContentArticleBo bo = articleBo();
        bo.setTagIds(List.of(21L));
        when(dictionaryService.validateAndNormalize(11L, bo.getTagIds())).thenReturn(List.of(21L));
        when(articleMapper.insert(any(ContentArticle.class))).thenAnswer(invocation -> {
            invocation.<ContentArticle>getArgument(0).setArticleId(100L);
            return 1;
        });
        when(tagMapper.insertBatch(anyList())).thenReturn(false);

        assertThatThrownBy(() -> service.insertByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessage("文章标签保存失败");
    }

    @Test
    void updateCanClearAllTagsAndRelations() {
        ContentArticle persisted = persistedArticle();
        ContentArticleBo bo = articleBo();
        bo.setArticleId(100L);
        bo.setTagIds(null);
        when(articleMapper.selectArticleById(100L)).thenReturn(persisted);
        when(dictionaryService.validateAndNormalize(11L, null)).thenReturn(List.of());
        when(articleMapper.updateArticleById(any(ContentArticle.class))).thenReturn(1);

        assertThat(service.updateByBo(bo)).isTrue();

        ArgumentCaptor<ContentArticle> updateCaptor = ArgumentCaptor.forClass(ContentArticle.class);
        verify(articleMapper).updateArticleById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getTagIds()).isEmpty();
        verify(tagMapper).deleteByArticleId(100L);
        verify(tagMapper, never()).insertBatch(anyList());
    }

    @Test
    void updateRejectsPublishedArticleEvenWhenRequestWithdrawsIt() {
        ContentArticle persisted = persistedArticle();
        persisted.setStatus("1");
        ContentArticleBo bo = articleBo();
        bo.setArticleId(100L);
        bo.setStatus("0");
        when(articleMapper.selectArticleById(100L)).thenReturn(persisted);

        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessage("已发布文章请先撤回为草稿后再修改");

        verify(articleMapper, never()).updateArticleById(any(ContentArticle.class));
        verify(tagMapper, never()).deleteByArticleId(any());
    }

    @Test
    void updateRejectsPublishedArticleThatRemainsPublished() {
        ContentArticle persisted = persistedArticle();
        persisted.setStatus("1");
        ContentArticleBo bo = articleBo();
        bo.setArticleId(100L);
        bo.setStatus("1");
        when(articleMapper.selectArticleById(100L)).thenReturn(persisted);

        assertThatThrownBy(() -> service.updateByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessage("已发布文章请先撤回为草稿后再修改");
    }

    @Test
    void updateAllowsDraftToBeSavedAndPublished() {
        ContentArticleBo bo = articleBo();
        bo.setArticleId(100L);
        bo.setStatus("1");
        when(articleMapper.selectArticleById(100L)).thenReturn(persistedArticle());
        when(dictionaryService.validateAndNormalize(11L, null)).thenReturn(List.of());
        when(articleMapper.updateArticleById(any(ContentArticle.class))).thenReturn(1);

        assertThat(service.updateByBo(bo)).isTrue();

        ArgumentCaptor<ContentArticle> captor = ArgumentCaptor.forClass(ContentArticle.class);
        verify(articleMapper).updateArticleById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("1");
        assertThat(captor.getValue().getPublishBy()).isEqualTo(9L);
        assertThat(captor.getValue().getPublishTime()).isEqualTo(new Date(1_000L));
    }

    @Test
    void draftToDraftIsSuccessfulWithoutWriting() {
        when(articleMapper.selectArticleById(100L)).thenReturn(persistedArticle());

        assertThat(service.changeStatus(100L, "0")).isTrue();

        verify(articleMapper, never()).updateArticleStatus(any(ContentArticle.class));
        verifyNoInteractions(tagMapper);
    }

    @Test
    void draftToPublishedWritesCurrentPublisherAndTime() {
        when(articleMapper.selectArticleById(100L)).thenReturn(persistedArticle());
        when(articleMapper.updateArticleStatus(any(ContentArticle.class))).thenReturn(1);

        assertThat(service.changeStatus(100L, "1")).isTrue();

        ArgumentCaptor<ContentArticle> captor = ArgumentCaptor.forClass(ContentArticle.class);
        verify(articleMapper).updateArticleStatus(captor.capture());
        assertThat(captor.getValue().getArticleId()).isEqualTo(100L);
        assertThat(captor.getValue().getStatus()).isEqualTo("1");
        assertThat(captor.getValue().getPublishBy()).isEqualTo(9L);
        assertThat(captor.getValue().getPublishTime()).isEqualTo(new Date(1_000L));
        assertThat(captor.getValue().getUpdateBy()).isEqualTo(9L);
        assertThat(captor.getValue().getUpdateTime()).isEqualTo(new Date(1_000L));
        verifyNoInteractions(tagMapper);
    }

    @Test
    void publishedToDraftClearsPublisherAndTime() {
        ContentArticle persisted = persistedArticle();
        persisted.setStatus("1");
        persisted.setPublishBy(8L);
        persisted.setPublishTime(new Date(500L));
        when(articleMapper.selectArticleById(100L)).thenReturn(persisted);
        when(articleMapper.updateArticleStatus(any(ContentArticle.class))).thenReturn(1);

        assertThat(service.changeStatus(100L, "0")).isTrue();

        ArgumentCaptor<ContentArticle> captor = ArgumentCaptor.forClass(ContentArticle.class);
        verify(articleMapper).updateArticleStatus(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("0");
        assertThat(captor.getValue().getPublishBy()).isNull();
        assertThat(captor.getValue().getPublishTime()).isNull();
        verifyNoInteractions(tagMapper);
    }

    @Test
    void publishedToPublishedRequiresWithdrawalFirst() {
        ContentArticle persisted = persistedArticle();
        persisted.setStatus("1");
        when(articleMapper.selectArticleById(100L)).thenReturn(persisted);

        assertThatThrownBy(() -> service.changeStatus(100L, "1"))
            .isInstanceOf(ServiceException.class)
            .hasMessage("文章已发布，请先撤回后再发布");

        verify(articleMapper, never()).updateArticleStatus(any(ContentArticle.class));
        verifyNoInteractions(tagMapper);
    }

    @Test
    void changeStatusRejectsUnknownStatusBeforeReadingArticle() {
        assertThatThrownBy(() -> service.changeStatus(100L, "2"))
            .isInstanceOf(ServiceException.class)
            .hasMessage("发布状态只能为0或1");

        verify(articleMapper, never()).selectArticleById(any());
    }

    @Test
    void changeStatusRejectsMissingOrUnauthorizedArticle() {
        when(articleMapper.selectArticleById(100L)).thenReturn(null);

        assertThatThrownBy(() -> service.changeStatus(100L, "1"))
            .isInstanceOf(ServiceException.class)
            .hasMessage("文章不存在或无权操作");
    }

    @Test
    void changeStatusFailsWhenAffectedRowsDoNotMatch() {
        when(articleMapper.selectArticleById(100L)).thenReturn(persistedArticle());
        when(articleMapper.updateArticleStatus(any(ContentArticle.class))).thenReturn(0);

        assertThatThrownBy(() -> service.changeStatus(100L, "1"))
            .isInstanceOf(ServiceException.class)
            .hasMessage("文章状态修改失败");
    }

    @Test
    void logicalDeleteKeepsTagRelations() {
        when(articleMapper.selectExistingArticleIds(List.of(100L))).thenReturn(List.of(100L));
        when(articleMapper.logicalDeleteByIds(List.of(100L))).thenReturn(1);

        assertThat(service.deleteWithValidByIds(List.of(100L), true)).isTrue();

        verify(tagMapper, never()).deleteByArticleIds(any());
    }

    @Test
    void logicalDeleteFailsWhenAffectedRowsDoNotMatch() {
        when(articleMapper.selectExistingArticleIds(List.of(100L, 101L))).thenReturn(List.of(100L, 101L));
        when(articleMapper.logicalDeleteByIds(List.of(100L, 101L))).thenReturn(1);

        assertThatThrownBy(() -> service.deleteWithValidByIds(List.of(100L, 101L), true))
            .isInstanceOf(ServiceException.class)
            .hasMessage("文章删除失败");
    }

    @Test
    void physicalDeleteRemovesRelationsBeforeArticles() {
        when(articleMapper.selectDeletedArticleIds(List.of(100L))).thenReturn(List.of(100L));
        when(articleMapper.physicalDeleteByIds(List.of(100L))).thenReturn(1);

        service.physicalDeleteByIds(List.of(100L));

        verify(tagMapper).deleteByArticleIds(List.of(100L));
        verify(articleMapper).physicalDeleteByIds(List.of(100L));
    }

    @Test
    void physicalDeleteRejectsArticleThatIsNotLogicallyDeleted() {
        when(articleMapper.selectDeletedArticleIds(List.of(100L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.physicalDeleteByIds(List.of(100L)))
            .isInstanceOf(ServiceException.class)
            .hasMessage("只能物理删除已逻辑删除且有权操作的文章");

        verify(tagMapper, never()).deleteByArticleIds(any());
    }

    @Test
    void physicalDeleteFailsWhenAffectedRowsDoNotMatch() {
        when(articleMapper.selectDeletedArticleIds(List.of(100L, 101L))).thenReturn(List.of(100L, 101L));
        when(articleMapper.physicalDeleteByIds(List.of(100L, 101L))).thenReturn(1);

        assertThatThrownBy(() -> service.physicalDeleteByIds(List.of(100L, 101L)))
            .isInstanceOf(ServiceException.class)
            .hasMessage("文章物理删除失败");
    }

    @Test
    void allWriteMethodsDeclareRollbackForException() throws Exception {
        assertTransactional("insertByBo", ContentArticleBo.class);
        assertTransactional("updateByBo", ContentArticleBo.class);
        assertTransactional("changeStatus", Long.class, String.class);
        assertTransactional("deleteWithValidByIds", Collection.class, Boolean.class);
        assertTransactional("physicalDeleteByIds", Collection.class);
    }

    private static void assertTransactional(String name, Class<?>... parameterTypes) throws Exception {
        Method method = ContentArticleServiceImpl.class.getMethod(name, parameterTypes);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.rollbackFor()).contains(Exception.class);
    }

    private static ContentArticleBo articleBo() {
        ContentArticleBo bo = new ContentArticleBo();
        bo.setTitle("标题");
        bo.setContent("<p>正文</p>");
        bo.setCategoryDictCode(11L);
        bo.setStatus("0");
        return bo;
    }

    private static ContentArticle persistedArticle() {
        ContentArticle article = new ContentArticle();
        article.setArticleId(100L);
        article.setStatus("0");
        article.setTagIds(List.of(21L));
        return article;
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }
}
