package org.dromara.content.mapper;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.dromara.content.domain.ContentArticle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ContentArticlePublicMapperSqlTest {

    @Test
    void listReturnsOnlyPublishedUndeletedRowsFromRequestedTenant() throws Exception {
        try (SqlSession session = sqlSessionFactory(dataSource()).openSession(true)) {
            ContentArticleMapper mapper = session.getMapper(ContentArticleMapper.class);
            Page<ContentArticle> result = mapper.selectPublishedArticlePage(
                new Page<>(1, 10), "140872", null, null);

            assertThat(result.getRecords())
                .extracting(ContentArticle::getArticleId)
                .containsExactly(104L, 100L);
            assertThat(result.getRecords())
                .allSatisfy(article -> assertThat(article.getContent()).isEqualTo("正文"));
        }
    }

    @Test
    void listAppliesTitleAndCategoryWithoutAcceptingStatus() throws Exception {
        try (SqlSession session = sqlSessionFactory(dataSource()).openSession(true)) {
            ContentArticleMapper mapper = session.getMapper(ContentArticleMapper.class);
            Page<ContentArticle> result = mapper.selectPublishedArticlePage(
                new Page<>(1, 10), "140872", "公开一", 11L);

            assertThat(result.getRecords())
                .extracting(ContentArticle::getArticleId)
                .containsExactly(100L);
        }
    }

    @Test
    void detailRejectsDraftDeletedAndOtherTenantRows() throws Exception {
        try (SqlSession session = sqlSessionFactory(dataSource()).openSession(true)) {
            ContentArticleMapper mapper = session.getMapper(ContentArticleMapper.class);

            assertThat(mapper.selectPublishedArticleById("140872", 100L)).isNotNull();
            assertThat(mapper.selectPublishedArticleById("140872", 101L)).isNull();
            assertThat(mapper.selectPublishedArticleById("140872", 102L)).isNull();
            assertThat(mapper.selectPublishedArticleById("140872", 103L)).isNull();
        }
    }

    private static DataSource dataSource() throws SQLException {
        DataSource dataSource = new PooledDataSource(
            "org.h2.Driver",
            "jdbc:h2:mem:public_article_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        );
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                create table content_article
                (
                    article_id bigint primary key,
                    tenant_id varchar(20) not null,
                    title varchar(200) not null,
                    summary varchar(500),
                    content varchar(2000),
                    category_dict_code bigint not null,
                    tag_ids varchar(200),
                    status char(1) not null,
                    publish_by bigint,
                    publish_time timestamp,
                    del_flag char(1) not null
                )
                """);
            insert(statement, 100L, "140872", "公开一", 11L, "1", "0");
            insert(statement, 101L, "140872", "草稿", 11L, "0", "0");
            insert(statement, 102L, "140872", "已删除", 11L, "1", "1");
            insert(statement, 103L, "420542", "其他租户", 11L, "1", "0");
            insert(statement, 104L, "140872", "公开二", 12L, "1", "0");
        }
        return dataSource;
    }

    private static void insert(Statement statement, Long articleId, String tenantId,
                               String title, Long categoryDictCode,
                               String status, String delFlag) throws SQLException {
        statement.executeUpdate("""
            insert into content_article
                (article_id, tenant_id, title, summary, content,
                 category_dict_code, tag_ids,
                 status, publish_by, publish_time, del_flag)
            values
                (%d, '%s', '%s', '简介', '正文', %d, '21,22',
                 '%s', 9, timestamp '2026-08-10 10:00:00', '%s')
            """.formatted(articleId, tenantId, title, categoryDictCode, status, delFlag));
    }

    private static SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment(
            "test", new JdbcTransactionFactory(), dataSource));

        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        configuration.addInterceptor(interceptor);

        String resource = "mapper/content/ContentArticleMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(
                input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }
}
