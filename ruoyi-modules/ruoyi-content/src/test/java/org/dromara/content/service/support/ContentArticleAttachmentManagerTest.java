package org.dromara.content.service.support;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.dromara.common.core.domain.dto.OssDTO;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.OssService;
import org.dromara.content.domain.ContentArticle;
import org.dromara.content.domain.ContentArticleAttachment;
import org.dromara.content.domain.vo.ContentArticleVo;
import org.dromara.content.domain.vo.ContentArticlePublicVo;
import org.dromara.content.domain.vo.ContentArticleMediaVo;
import org.dromara.content.enums.ContentArticleAttachmentType;
import org.dromara.content.mapper.ContentArticleAttachmentMapper;
import org.dromara.content.mapper.ContentArticleMapper;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final ContentArticleMapper articleMapper = mock(ContentArticleMapper.class);
    private final OssService ossService = mock(OssService.class);
    private final ContentArticleOperationContext operationContext = mock(ContentArticleOperationContext.class);
    private ContentArticleAttachmentManager manager;

    @BeforeEach
    void setUp() {
        when(operationContext.currentUserId()).thenReturn(9L);
        when(operationContext.now()).thenReturn(new Date(1_000L));
        when(articleMapper.selectByIdForUpdate(100L)).thenReturn(article(100L));
        manager = new ContentArticleAttachmentManager(articleMapper, mapper, ossService, operationContext);
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
        order.verify(ossService).scheduleBusinessDeletion(Map.of(20L, "100"), "content_article");
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
    void populatesEveryVoWhenThePageContainsDuplicateArticleIds() {
        ContentArticleVo first = articleVo(100L);
        ContentArticleVo duplicate = articleVo(100L);
        when(mapper.selectByArticleIds(List.of(100L))).thenReturn(List.of(
            relation(1L, 100L, 10L, ContentArticleAttachmentType.IMAGE, 0),
            relation(2L, 100L, 20L, ContentArticleAttachmentType.VIDEO, 0)));

        manager.populate(List.of(first, duplicate));

        assertThat(first.getAttachmentOssIds()).containsExactly(10L);
        assertThat(first.getVideoOssIds()).containsExactly(20L);
        assertThat(duplicate.getAttachmentOssIds()).containsExactly(10L);
        assertThat(duplicate.getVideoOssIds()).containsExactly(20L);
        verify(mapper).selectByArticleIds(List.of(100L));
    }

    @Test
    void populatesPublicPageWithOneRelationQuery() {
        ContentArticlePublicVo first = publicArticleVo(100L);
        ContentArticlePublicVo second = publicArticleVo(101L);
        when(mapper.selectByArticleIds(List.of(100L, 101L))).thenReturn(List.of(
            relation(1L, 100L, 10L, ContentArticleAttachmentType.IMAGE, 0),
            relation(2L, 100L, 20L, ContentArticleAttachmentType.VIDEO, 0),
            relation(3L, 101L, 11L, ContentArticleAttachmentType.IMAGE, 0)));

        manager.populatePublic(List.of(first, second));

        assertThat(first.getAttachmentOssIds()).containsExactly(10L);
        assertThat(first.getVideoOssIds()).containsExactly(20L);
        assertThat(second.getAttachmentOssIds()).containsExactly(11L);
        assertThat(second.getVideoOssIds()).isEmpty();
        verify(mapper).selectByArticleIds(List.of(100L, 101L));
    }

    @Test
    void publicResolverIntersectsRelationsAndReturnsOnlySanitizedCurrentMetadata() {
        when(mapper.selectByArticleIds(List.of(100L))).thenReturn(List.of(
            relation(1L, 100L, 10L, ContentArticleAttachmentType.IMAGE, 0),
            relation(2L, 100L, 20L, ContentArticleAttachmentType.VIDEO, 0)));
        OssDTO currentImage = image(10L);
        currentImage.setUrl("https://fresh.example/10");
        currentImage.setOriginalName("cover.png");
        OssDTO unrelated = image(999L);
        unrelated.setUrl("https://fresh.example/999");
        when(ossService.resolveByIds(List.of(10L, 20L)))
            .thenReturn(List.of(unrelated, currentImage));

        List<ContentArticleMediaVo> result = manager.resolvePublicMedia(100L);

        assertThat(result).singleElement().satisfies(media -> {
            assertThat(media.getOssId()).isEqualTo(10L);
            assertThat(media.getUrl()).isEqualTo("https://fresh.example/10");
            assertThat(media.getOriginalName()).isEqualTo("cover.png");
            assertThat(media.getFileType()).isEqualTo("IMAGE");
        });
        assertThat(ContentArticleMediaVo.class.getDeclaredFields())
            .extracting(java.lang.reflect.Field::getName)
            .doesNotContain("fileName", "service", "ext1", "refType", "refId", "isTemp");
        verify(ossService).resolveByIds(List.of(10L, 20L));
    }

    @Test
    void permanentDeleteMarksExactOwnersAfterRelationsAreRemoved() {
        List<ContentArticleAttachment> relations = List.of(
            relation(1L, 100L, 10L, ContentArticleAttachmentType.IMAGE, 0),
            relation(2L, 101L, 20L, ContentArticleAttachmentType.VIDEO, 0));
        when(mapper.selectByArticleIds(List.of(100L, 101L))).thenReturn(relations);
        when(mapper.deleteByArticleIds(List.of(100L, 101L))).thenReturn(2);

        manager.deletePermanently(List.of(100L, 101L));

        InOrder order = inOrder(ossService, mapper);
        order.verify(mapper).deleteByArticleIds(List.of(100L, 101L));
        order.verify(ossService).scheduleBusinessDeletion(
            Map.of(10L, "100", 20L, "101"), "content_article");
    }

    @Test
    void realTransactionRollsBackBindingAndRelationWriteWhenBatchReportsFailure() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TxConfig.class)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.execute("create table content_article (article_id bigint primary key)");
            jdbc.execute("create table sys_oss (oss_id bigint primary key, ref_id varchar(32))");
            jdbc.execute("create table content_article_attachment (article_attachment_id bigint auto_increment primary key, article_id bigint, oss_id bigint unique)");
            jdbc.update("insert into sys_oss (oss_id, ref_id) values (10, null)");

            ContentArticleAttachmentMapper txMapper = context.getBean(ContentArticleAttachmentMapper.class);
            ContentArticleMapper txArticleMapper = context.getBean(ContentArticleMapper.class);
            OssService txOss = context.getBean(OssService.class);
            when(txArticleMapper.selectByIdForUpdate(100L)).thenReturn(article(100L));
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

    @Test
    void parentArticleLockSerializesConcurrentReplacementWhenNoRelationsExist() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TxConfig.class)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.execute("create table content_article (article_id bigint primary key)");
            jdbc.execute("""
                create table content_article_attachment (
                    article_attachment_id bigint auto_increment primary key,
                    article_id bigint not null,
                    oss_id bigint not null unique,
                    attachment_type varchar(1) not null,
                    sort_num integer not null,
                    create_by bigint,
                    create_time timestamp)
                """);
            jdbc.update("insert into content_article (article_id) values (100)");

            ContentArticleMapper txArticleMapper = context.getBean(ContentArticleMapper.class);
            ContentArticleAttachmentMapper txMapper = context.getBean(ContentArticleAttachmentMapper.class);
            OssService txOss = context.getBean(OssService.class);
            CountDownLatch firstLocked = new CountDownLatch(1);
            CountDownLatch secondAttempted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            AtomicInteger lockAttempts = new AtomicInteger();
            when(txArticleMapper.selectByIdForUpdate(100L)).thenAnswer(invocation -> {
                int attempt = lockAttempts.incrementAndGet();
                if (attempt == 2) {
                    secondAttempted.countDown();
                }
                Long articleId = jdbc.queryForObject(
                    "select article_id from content_article where article_id = 100 for update", Long.class);
                if (attempt == 1) {
                    firstLocked.countDown();
                    if (!releaseFirst.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("first replacement was not released");
                    }
                }
                return article(articleId);
            });
            when(txMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> jdbc.query(
                "select * from content_article_attachment where article_id = 100 order by article_attachment_id",
                (rs, rowNum) -> relation(rs.getLong("article_attachment_id"), rs.getLong("article_id"),
                    rs.getLong("oss_id"), ContentArticleAttachmentType.valueOf(
                        "0".equals(rs.getString("attachment_type")) ? "IMAGE" : "VIDEO"),
                    rs.getInt("sort_num"))));
            when(txOss.selectByIds(anyCollection())).thenAnswer(invocation ->
                invocation.<Collection<Long>>getArgument(0).stream().map(ContentArticleAttachmentManagerTest::image).toList());
            when(txMapper.insertBatch(anyCollection())).thenAnswer(invocation -> {
                int inserted = 0;
                for (ContentArticleAttachment relation : invocation.<Collection<ContentArticleAttachment>>getArgument(0)) {
                    inserted += jdbc.update("""
                        insert into content_article_attachment
                            (article_id, oss_id, attachment_type, sort_num, create_by, create_time)
                        values (?, ?, ?, ?, ?, ?)
                        """, relation.getArticleId(), relation.getOssId(), relation.getAttachmentType(),
                        relation.getSortNum(), relation.getCreateBy(), relation.getCreateTime());
                }
                return inserted == invocation.<Collection<?>>getArgument(0).size();
            });
            when(txMapper.deleteByIds(anyCollection())).thenAnswer(invocation -> {
                Collection<Long> ids = invocation.getArgument(0);
                int deleted = 0;
                for (Long id : ids) {
                    deleted += jdbc.update(
                        "delete from content_article_attachment where article_attachment_id = ?", id);
                }
                return deleted;
            });

            ContentArticleAttachmentManager transactional = context.getBean(ContentArticleAttachmentManager.class);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<?> first = executor.submit(() -> transactional.replace(100L, List.of(10L), List.of()));
                assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
                Future<?> second = executor.submit(() -> transactional.replace(100L, List.of(11L), List.of()));
                assertThat(secondAttempted.await(5, TimeUnit.SECONDS)).isTrue();
                releaseFirst.countDown();
                first.get(5, TimeUnit.SECONDS);
                second.get(5, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }

            assertThat(jdbc.queryForList(
                "select oss_id from content_article_attachment order by oss_id", Long.class))
                .containsExactly(11L);
        }
    }

    private static ContentArticleVo articleVo(Long articleId) {
        ContentArticleVo vo = new ContentArticleVo();
        vo.setArticleId(articleId);
        return vo;
    }

    private static ContentArticlePublicVo publicArticleVo(Long articleId) {
        ContentArticlePublicVo vo = new ContentArticlePublicVo();
        vo.setArticleId(articleId);
        return vo;
    }

    private static ContentArticle article(Long articleId) {
        ContentArticle article = new ContentArticle();
        article.setArticleId(articleId);
        return article;
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
            dataSource.setURL("jdbc:h2:mem:attachment_manager_tx_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
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
        ContentArticleMapper articleMapper() {
            return mock(ContentArticleMapper.class);
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
        ContentArticleAttachmentManager manager(ContentArticleMapper articleMapper,
                                                  ContentArticleAttachmentMapper attachmentMapper,
                                                  OssService ossService,
                                                  ContentArticleOperationContext operationContext) {
            return new ContentArticleAttachmentManager(articleMapper, attachmentMapper, ossService, operationContext);
        }
    }
}
