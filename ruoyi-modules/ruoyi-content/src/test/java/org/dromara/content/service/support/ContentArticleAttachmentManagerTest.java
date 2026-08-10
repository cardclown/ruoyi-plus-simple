package org.dromara.content.service.support;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.dromara.common.core.domain.dto.OssDTO;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.OssService;
import org.dromara.content.domain.ContentArticleAttachment;
import org.dromara.content.domain.vo.ContentArticleVo;
import org.dromara.content.enums.ContentArticleAttachmentType;
import org.dromara.content.mapper.ContentArticleAttachmentMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class ContentArticleAttachmentManagerTest {

    private final ContentArticleAttachmentMapper mapper = mock(ContentArticleAttachmentMapper.class);
    private final OssService ossService = mock(OssService.class);
    private final ContentArticleOperationContext operationContext = mock(ContentArticleOperationContext.class);
    private ContentArticleAttachmentManager manager;

    @BeforeEach
    void setUp() {
        when(operationContext.currentUserId()).thenReturn(9L);
        when(operationContext.now()).thenReturn(new Date(1_000L));
        manager = new ContentArticleAttachmentManager(mapper, ossService, operationContext);
    }

    @Test
    void rejectsMediaCountAndIdentifierMisuseBeforeReadingStorage() {
        assertThatThrownBy(() -> manager.replace(100L,
            LongStream.rangeClosed(1, 11).boxed().toList(), List.of()))
            .isInstanceOf(ServiceException.class).hasMessage("文章图片最多上传10张");
        assertThatThrownBy(() -> manager.replace(100L, List.of(), List.of(1L, 2L, 3L, 4L, 5L, 6L)))
            .isInstanceOf(ServiceException.class).hasMessage("文章视频最多上传5个");
        assertThatThrownBy(() -> manager.replace(100L, List.of(10L, 10L), List.of()))
            .isInstanceOf(ServiceException.class).hasMessage("文章图片附件不能重复");
        assertThatThrownBy(() -> manager.replace(100L, List.of(), List.of(20L, 20L)))
            .isInstanceOf(ServiceException.class).hasMessage("文章视频附件不能重复");
        assertThatThrownBy(() -> manager.replace(100L, List.of(10L), List.of(10L)))
            .isInstanceOf(ServiceException.class).hasMessage("同一附件不能同时作为图片和视频");
        assertThatThrownBy(() -> manager.replace(100L, java.util.Collections.singletonList(null), List.of()))
            .isInstanceOf(ServiceException.class).hasMessage("附件ID不能为空");

        verifyNoInteractions(ossService, mapper);
    }

    @Test
    void requiresOneExactBatchOfClassifiedMetadataBeforeAnyMutation() {
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(ossService.selectByIds(List.of(10L, 20L))).thenReturn(List.of(image(10L)));

        assertThatThrownBy(() -> manager.replace(100L, List.of(10L), List.of(20L)))
            .isInstanceOf(ServiceException.class).hasMessage("附件不存在或无权访问");

        verify(ossService).selectByIds(List.of(10L, 20L));
        verify(ossService, never()).bindToBusiness(anyCollection(), any(), any());
        verify(mapper, never()).insertBatch(anyCollection());
    }

    @Test
    void rejectsWrongClassificationSizeAndFormat() {
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        OssDTO wrongType = image(10L);
        wrongType.setFileType("VIDEO");
        when(ossService.selectByIds(List.of(10L))).thenReturn(List.of(wrongType));
        assertThatThrownBy(() -> manager.replace(100L, List.of(10L), List.of()))
            .isInstanceOf(ServiceException.class).hasMessage("附件类型与文章媒体类型不匹配");

        OssDTO oversized = image(10L);
        oversized.setFileSize(50L * 1024 * 1024 + 1);
        when(ossService.selectByIds(List.of(10L))).thenReturn(List.of(oversized));
        assertThatThrownBy(() -> manager.replace(100L, List.of(10L), List.of()))
            .isInstanceOf(ServiceException.class).hasMessage("单张图片不能超过50MB");

        OssDTO disguised = video(20L);
        disguised.setFileSuffix(".png");
        disguised.setContentType("image/png");
        when(ossService.selectByIds(List.of(20L))).thenReturn(List.of(disguised));
        assertThatThrownBy(() -> manager.replace(100L, List.of(), List.of(20L)))
            .isInstanceOf(ServiceException.class).hasMessage("视频文件格式不合法");
    }

    @Test
    void preservesOnlyAnUnchangedValidLegacyImageRelation() {
        ContentArticleAttachment existing = relation(1L, 100L, 10L, ContentArticleAttachmentType.IMAGE, 0);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(existing));
        OssDTO legacy = image(10L);
        legacy.setFileType(null);
        legacy.setIsTemp(false);
        legacy.setRefType("content_article");
        legacy.setRefId("100");
        when(ossService.selectByIds(List.of(10L))).thenReturn(List.of(legacy));

        manager.replace(100L, List.of(10L), List.of());

        verify(ossService, never()).bindToBusiness(anyCollection(), any(), any());
        verify(mapper, never()).insertBatch(anyCollection());

        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertThatThrownBy(() -> manager.replace(100L, List.of(10L), List.of()))
            .isInstanceOf(ServiceException.class).hasMessage("附件类型与文章媒体类型不匹配");
    }

    @Test
    void rejectsOssReferencesOrRelationsOwnedByAnotherArticle() {
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        OssDTO bound = image(10L);
        bound.setRefType("content_article");
        bound.setRefId("99");
        bound.setIsTemp(false);
        when(ossService.selectByIds(List.of(10L))).thenReturn(List.of(bound));
        assertThatThrownBy(() -> manager.replace(100L, List.of(10L), List.of()))
            .isInstanceOf(ServiceException.class).hasMessage("附件已绑定其他文章");

        ContentArticleAttachment foreign = relation(1L, 99L, 10L, ContentArticleAttachmentType.IMAGE, 0);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(foreign));
        OssDTO apparentlyFree = image(10L);
        when(ossService.selectByIds(List.of(10L))).thenReturn(List.of(apparentlyFree));
        assertThatThrownBy(() -> manager.replace(100L, List.of(10L), List.of()))
            .isInstanceOf(ServiceException.class).hasMessage("附件已绑定其他文章");
    }

    @Test
    void validatesEveryTargetBeforeBindingOrWritingRelations() {
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        OssDTO invalidVideo = video(20L);
        invalidVideo.setFileSize(2L * 1024 * 1024 * 1024 + 1);
        when(ossService.selectByIds(List.of(10L, 20L))).thenReturn(List.of(image(10L), invalidVideo));

        assertThatThrownBy(() -> manager.replace(100L, List.of(10L), List.of(20L)))
            .isInstanceOf(ServiceException.class).hasMessage("单个视频不能超过2GB");

        verify(ossService, never()).bindToBusiness(anyCollection(), any(), any());
        verify(mapper, never()).insertBatch(anyCollection());
    }

    @Test
    void bindsBeforeExposingNewRelationsAndDeletesOnlyRemovedOwnedOss() {
        ContentArticleAttachment image = relation(1L, 100L, 10L, ContentArticleAttachmentType.IMAGE, 0);
        ContentArticleAttachment oldVideo = relation(2L, 100L, 20L, ContentArticleAttachmentType.VIDEO, 0);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(image, oldVideo));
        when(ossService.selectByIds(List.of(10L, 21L))).thenReturn(List.of(image(10L), video(21L)));
        when(mapper.insertBatch(anyCollection())).thenReturn(true);
        when(mapper.deleteByIds(List.of(2L))).thenReturn(1);

        manager.replace(100L, List.of(10L), List.of(21L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ContentArticleAttachment>> inserted = ArgumentCaptor.forClass(Collection.class);
        verify(mapper).insertBatch(inserted.capture());
        assertThat(inserted.getValue())
            .extracting(ContentArticleAttachment::getArticleId, ContentArticleAttachment::getOssId,
                ContentArticleAttachment::getAttachmentType, ContentArticleAttachment::getSortNum,
                ContentArticleAttachment::getCreateBy, ContentArticleAttachment::getCreateTime)
            .containsExactly(tuple(100L, 21L, "1", 0, 9L, new Date(1_000L)));

        InOrder order = inOrder(ossService, mapper);
        order.verify(ossService).bindToBusiness(List.of(21L), "content_article", "100");
        order.verify(mapper).insertBatch(anyCollection());
        order.verify(mapper).deleteByIds(List.of(2L));
        order.verify(ossService).deleteByIds(List.of(20L));
    }

    @Test
    void updatesChangedOrderInOneBatchAndPropagatesBatchFailures() {
        ContentArticleAttachment first = relation(1L, 100L, 10L, ContentArticleAttachmentType.IMAGE, 0);
        ContentArticleAttachment second = relation(2L, 100L, 11L, ContentArticleAttachmentType.IMAGE, 1);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));
        when(ossService.selectByIds(List.of(11L, 10L))).thenReturn(List.of(image(11L), image(10L)));
        when(mapper.updateBatchById(anyCollection())).thenReturn(false);

        assertThatThrownBy(() -> manager.replace(100L, List.of(11L, 10L), List.of()))
            .isInstanceOf(ServiceException.class).hasMessage("文章附件更新失败");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<ContentArticleAttachment>> updated = ArgumentCaptor.forClass(Collection.class);
        verify(mapper).updateBatchById(updated.capture());
        assertThat(updated.getValue())
            .extracting(ContentArticleAttachment::getOssId, ContentArticleAttachment::getSortNum)
            .containsExactly(tuple(11L, 0), tuple(10L, 1));
    }

    @Test
    void populatesOnePageWithOneRelationQueryAndWritesEmptyLists() {
        ContentArticleVo first = articleVo(100L);
        ContentArticleVo second = articleVo(101L);
        ContentArticleVo missingId = articleVo(null);
        when(mapper.selectByArticleIds(List.of(100L, 101L))).thenReturn(List.of(
            relation(1L, 100L, 10L, ContentArticleAttachmentType.IMAGE, 0),
            relation(2L, 100L, 20L, ContentArticleAttachmentType.VIDEO, 0),
            relation(3L, 101L, 11L, ContentArticleAttachmentType.IMAGE, 0)));

        manager.populate(List.of(first, second, missingId));

        assertThat(first.getAttachmentOssIds()).containsExactly(10L);
        assertThat(first.getVideoOssIds()).containsExactly(20L);
        assertThat(second.getAttachmentOssIds()).containsExactly(11L);
        assertThat(second.getVideoOssIds()).isEmpty();
        assertThat(missingId.getAttachmentOssIds()).isEmpty();
        assertThat(missingId.getVideoOssIds()).isEmpty();
        verify(mapper).selectByArticleIds(List.of(100L, 101L));
    }

    @Test
    void permanentDeleteRemovesObjectsBeforeRelationsAndStopsOnObjectFailure() {
        List<ContentArticleAttachment> relations = List.of(
            relation(1L, 100L, 10L, ContentArticleAttachmentType.IMAGE, 0),
            relation(2L, 101L, 20L, ContentArticleAttachmentType.VIDEO, 0));
        when(mapper.selectByArticleIds(List.of(100L, 101L))).thenReturn(relations);
        when(mapper.deleteByArticleIds(List.of(100L, 101L))).thenReturn(2);

        manager.deletePermanently(List.of(100L, 101L));

        InOrder order = inOrder(ossService, mapper);
        order.verify(ossService).deleteByIds(List.of(10L, 20L));
        order.verify(mapper).deleteByArticleIds(List.of(100L, 101L));

        when(mapper.selectByArticleIds(List.of(100L))).thenReturn(List.of(relations.get(0)));
        org.mockito.Mockito.doThrow(new ServiceException("存储删除失败"))
            .when(ossService).deleteByIds(List.of(10L));
        assertThatThrownBy(() -> manager.deletePermanently(List.of(100L)))
            .isInstanceOf(ServiceException.class).hasMessage("存储删除失败");
        verify(mapper, never()).deleteByArticleIds(List.of(100L));
    }

    @Test
    void realTransactionRollsBackBindingAndRelationWriteWhenBatchReportsFailure() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TxConfig.class)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.execute("create table sys_oss (oss_id bigint primary key, ref_id varchar(32))");
            jdbc.execute("create table content_article_attachment (article_attachment_id bigint auto_increment primary key, article_id bigint, oss_id bigint unique)");
            jdbc.update("insert into sys_oss (oss_id, ref_id) values (10, null)");

            ContentArticleAttachmentMapper txMapper = context.getBean(ContentArticleAttachmentMapper.class);
            OssService txOss = context.getBean(OssService.class);
            when(txMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
            when(txOss.selectByIds(List.of(10L))).thenReturn(List.of(image(10L)));
            org.mockito.Mockito.doAnswer(invocation -> {
                jdbc.update("update sys_oss set ref_id = '100' where oss_id = 10");
                return null;
            }).when(txOss).bindToBusiness(List.of(10L), "content_article", "100");
            when(txMapper.insertBatch(anyCollection())).thenAnswer(invocation -> {
                jdbc.update("insert into content_article_attachment (article_id, oss_id) values (100, 10)");
                return false;
            });

            ContentArticleAttachmentManager transactional = context.getBean(ContentArticleAttachmentManager.class);
            assertThatThrownBy(() -> transactional.replace(100L, List.of(10L), List.of()))
                .isInstanceOf(ServiceException.class).hasMessage("文章附件保存失败");

            assertThat(jdbc.queryForObject("select ref_id from sys_oss where oss_id = 10", String.class)).isNull();
            assertThat(jdbc.queryForObject("select count(*) from content_article_attachment", Integer.class)).isZero();
        }
    }

    private static ContentArticleVo articleVo(Long articleId) {
        ContentArticleVo vo = new ContentArticleVo();
        vo.setArticleId(articleId);
        return vo;
    }

    private static ContentArticleAttachment relation(Long relationId, Long articleId, Long ossId,
                                                       ContentArticleAttachmentType type, int sortNum) {
        ContentArticleAttachment relation = new ContentArticleAttachment();
        relation.setArticleAttachmentId(relationId);
        relation.setArticleId(articleId);
        relation.setOssId(ossId);
        relation.setAttachmentType(type.getCode());
        relation.setSortNum(sortNum);
        return relation;
    }

    private static OssDTO image(Long ossId) {
        return media(ossId, ".png", "image/png", "IMAGE");
    }

    private static OssDTO video(Long ossId) {
        return media(ossId, ".mp4", "video/mp4", "VIDEO");
    }

    private static OssDTO media(Long ossId, String suffix, String contentType, String fileType) {
        OssDTO dto = new OssDTO();
        dto.setOssId(ossId);
        dto.setFileSuffix(suffix);
        dto.setContentType(contentType);
        dto.setFileSize(1L);
        dto.setFileType(fileType);
        dto.setIsTemp(true);
        return dto;
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }

    @Configuration
    @EnableTransactionManagement
    static class TxConfig {
        @Bean
        DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:attachment_manager_tx;MODE=MySQL;DB_CLOSE_DELAY=-1");
            dataSource.setUser("sa");
            return dataSource;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        ContentArticleAttachmentMapper attachmentMapper() {
            return mock(ContentArticleAttachmentMapper.class);
        }

        @Bean
        OssService ossService() {
            return mock(OssService.class);
        }

        @Bean
        ContentArticleOperationContext operationContext() {
            ContentArticleOperationContext context = mock(ContentArticleOperationContext.class);
            when(context.currentUserId()).thenReturn(9L);
            when(context.now()).thenReturn(new Date(1_000L));
            return context;
        }

        @Bean
        ContentArticleAttachmentManager manager(ContentArticleAttachmentMapper attachmentMapper,
                                                  OssService ossService,
                                                  ContentArticleOperationContext operationContext) {
            return new ContentArticleAttachmentManager(attachmentMapper, ossService, operationContext);
        }
    }
}
