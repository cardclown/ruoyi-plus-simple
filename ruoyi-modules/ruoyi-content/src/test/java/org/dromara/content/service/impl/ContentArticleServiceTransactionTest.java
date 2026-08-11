package org.dromara.content.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.content.domain.ContentArticle;
import org.dromara.content.domain.ContentArticleTag;
import org.dromara.content.domain.bo.ContentArticleBo;
import org.dromara.content.mapper.ContentArticleMapper;
import org.dromara.content.mapper.ContentArticleTagMapper;
import org.dromara.content.service.IContentArticleService;
import org.dromara.content.service.support.ContentArticleAttachmentManager;
import org.dromara.content.service.support.ContentArticleDictionaryService;
import org.dromara.content.service.support.ContentArticleHtmlSanitizer;
import org.dromara.content.service.support.ContentArticleOperationContext;
import org.dromara.content.service.support.ContentArticlePublishPolicy;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class ContentArticleServiceTransactionTest {

    @Test
    void updateRollsBackArticleAndTagChangesWhenFinalAttachmentReplaceFails() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TxConfig.class)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            createTables(jdbc);
            jdbc.update("insert into content_article (article_id, title) values (100, '旧标题')");
            jdbc.update("insert into content_article_tag (article_id, tag_dict_code) values (100, 21)");

            ContentArticleMapper articleMapper = context.getBean(ContentArticleMapper.class);
            ContentArticleTagMapper tagMapper = context.getBean(ContentArticleTagMapper.class);
            ContentArticleDictionaryService dictionaryService = context.getBean(ContentArticleDictionaryService.class);
            ContentArticleAttachmentManager attachmentManager = context.getBean(ContentArticleAttachmentManager.class);
            stubPersistedDraft(articleMapper);
            when(dictionaryService.validateAndNormalize(11L, List.of(22L))).thenReturn(List.of(22L));
            when(articleMapper.updateArticleById(any(ContentArticle.class))).thenAnswer(invocation ->
                jdbc.update("update content_article set title = ? where article_id = 100",
                    invocation.<ContentArticle>getArgument(0).getTitle()));
            when(tagMapper.deleteByArticleId(100L)).thenAnswer(invocation ->
                jdbc.update("delete from content_article_tag where article_id = 100"));
            stubTagInsert(jdbc, tagMapper);
            doAnswer(invocation -> {
                assertThat(jdbc.queryForObject(
                    "select title from content_article where article_id = 100", String.class))
                    .isEqualTo("新标题");
                assertThat(jdbc.queryForList(
                    "select tag_dict_code from content_article_tag where article_id = 100", Long.class))
                    .containsExactly(22L);
                throw new ServiceException("附件替换失败");
            }).when(attachmentManager).replace(100L, List.of(10L), List.of(20L));

            assertThatThrownBy(() -> context.getBean(IContentArticleService.class).updateByBo(updateBo()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("附件替换失败");

            assertThat(jdbc.queryForObject(
                "select title from content_article where article_id = 100", String.class))
                .isEqualTo("旧标题");
            assertThat(jdbc.queryForList(
                "select tag_dict_code from content_article_tag where article_id = 100", Long.class))
                .containsExactly(21L);
        }
    }

    @Test
    void addRollsBackArticleAndTagsWhenFinalAttachmentReplaceFails() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TxConfig.class)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            createTables(jdbc);

            ContentArticleMapper articleMapper = context.getBean(ContentArticleMapper.class);
            ContentArticleTagMapper tagMapper = context.getBean(ContentArticleTagMapper.class);
            ContentArticleDictionaryService dictionaryService = context.getBean(ContentArticleDictionaryService.class);
            ContentArticleAttachmentManager attachmentManager = context.getBean(ContentArticleAttachmentManager.class);
            when(dictionaryService.validateAndNormalize(11L, List.of(22L))).thenReturn(List.of(22L));
            when(articleMapper.insert(any(ContentArticle.class))).thenAnswer(invocation -> {
                ContentArticle article = invocation.getArgument(0);
                article.setArticleId(100L);
                return jdbc.update("insert into content_article (article_id, title) values (?, ?)",
                    article.getArticleId(), article.getTitle());
            });
            stubTagInsert(jdbc, tagMapper);
            doAnswer(invocation -> {
                assertThat(jdbc.queryForObject(
                    "select title from content_article where article_id = 100", String.class))
                    .isEqualTo("新标题");
                assertThat(jdbc.queryForList(
                    "select tag_dict_code from content_article_tag where article_id = 100", Long.class))
                    .containsExactly(22L);
                throw new ServiceException("附件替换失败");
            }).when(attachmentManager).replace(100L, List.of(10L), List.of(20L));

            assertThatThrownBy(() -> context.getBean(IContentArticleService.class).insertByBo(addBo()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("附件替换失败");

            assertThat(jdbc.queryForObject("select count(*) from content_article", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from content_article_tag", Integer.class)).isZero();
        }
    }

    private static void createTables(JdbcTemplate jdbc) {
        jdbc.execute("create table content_article (article_id bigint primary key, title varchar(200))");
        jdbc.execute("""
            create table content_article_tag (
                article_tag_id bigint auto_increment primary key,
                article_id bigint not null,
                tag_dict_code bigint not null)
            """);
    }

    private static void stubPersistedDraft(ContentArticleMapper articleMapper) {
        ContentArticle persisted = new ContentArticle();
        persisted.setArticleId(100L);
        persisted.setStatus("0");
        persisted.setTagIds(List.of(21L));
        when(articleMapper.selectArticleById(100L)).thenReturn(persisted);
    }

    private static void stubTagInsert(JdbcTemplate jdbc, ContentArticleTagMapper tagMapper) {
        when(tagMapper.insertBatch(anyCollection())).thenAnswer(invocation -> {
            int inserted = 0;
            for (ContentArticleTag tag : invocation.<Collection<ContentArticleTag>>getArgument(0)) {
                inserted += jdbc.update(
                    "insert into content_article_tag (article_id, tag_dict_code) values (?, ?)",
                    tag.getArticleId(), tag.getTagDictCode());
            }
            return inserted == invocation.<Collection<?>>getArgument(0).size();
        });
    }

    private static ContentArticleBo updateBo() {
        ContentArticleBo bo = addBo();
        bo.setArticleId(100L);
        return bo;
    }

    private static ContentArticleBo addBo() {
        ContentArticleBo bo = new ContentArticleBo();
        bo.setTitle("新标题");
        bo.setContent("<p>新正文</p>");
        bo.setCategoryDictCode(11L);
        bo.setTagIds(List.of(22L));
        bo.setAttachmentOssIds(List.of(10L));
        bo.setVideoOssIds(List.of(20L));
        bo.setStatus("0");
        return bo;
    }

    @Configuration
    @EnableTransactionManagement
    static class TxConfig {

        @Bean
        DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:article_service_tx_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
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
        ContentArticleMapper articleMapper() {
            return mock(ContentArticleMapper.class);
        }

        @Bean
        ContentArticleTagMapper tagMapper() {
            return mock(ContentArticleTagMapper.class);
        }

        @Bean
        ContentArticleDictionaryService dictionaryService() {
            return mock(ContentArticleDictionaryService.class);
        }

        @Bean
        ContentArticleAttachmentManager attachmentManager() {
            return mock(ContentArticleAttachmentManager.class);
        }

        @Bean
        ContentArticleOperationContext operationContext() {
            ContentArticleOperationContext context = mock(ContentArticleOperationContext.class);
            when(context.currentUserId()).thenReturn(9L);
            when(context.now()).thenReturn(new Date(1_000L));
            return context;
        }

        @Bean
        ContentArticleHtmlSanitizer htmlSanitizer() {
            return new ContentArticleHtmlSanitizer();
        }

        @Bean
        ContentArticlePublishPolicy publishPolicy() {
            return new ContentArticlePublishPolicy();
        }

        @Bean
        ContentArticleServiceImpl articleService(ContentArticleMapper articleMapper,
                                                 ContentArticleTagMapper tagMapper,
                                                 ContentArticleDictionaryService dictionaryService,
                                                 ContentArticleHtmlSanitizer htmlSanitizer,
                                                 ContentArticlePublishPolicy publishPolicy,
                                                 ContentArticleOperationContext operationContext,
                                                 ContentArticleAttachmentManager attachmentManager) {
            return new ContentArticleServiceImpl(articleMapper, tagMapper, dictionaryService,
                htmlSanitizer, publishPolicy, operationContext, attachmentManager);
        }
    }
}
