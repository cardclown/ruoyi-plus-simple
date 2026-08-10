package org.dromara.content.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ContentArticleMapperStatusSqlContractTest {

    @Test
    void statusUpdateChangesOnlyStatusAndAuditColumnsAndCanClearPublishFields() throws Exception {
        DataSource dataSource = dataSource();
        try (SqlSession session = sqlSessionFactory(dataSource).openSession(true)) {
            ContentArticleMapper mapper = session.getMapper(ContentArticleMapper.class);

            ContentArticle publish = statusUpdate("1", 9L, new Date(1_000L));
            assertThat(mapper.updateArticleStatus(publish)).isEqualTo(1);
            assertRow(dataSource, "1", 9L, true);

            ContentArticle withdraw = statusUpdate("0", null, null);
            assertThat(mapper.updateArticleStatus(withdraw)).isEqualTo(1);
            assertRow(dataSource, "0", null, false);
        }
    }

    private static DataSource dataSource() throws SQLException {
        DataSource dataSource = new PooledDataSource(
            "org.h2.Driver",
            "jdbc:h2:mem:content_article_status_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        );
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                create table content_article
                (
                    article_id bigint primary key,
                    status char(1) not null,
                    publish_by bigint,
                    publish_time timestamp,
                    update_by bigint,
                    update_time timestamp,
                    title varchar(200) not null,
                    content varchar(1000) not null,
                    category_dict_code bigint not null,
                    tag_ids varchar(200),
                    cover_oss_id bigint,
                    del_flag char(1) not null
                )
                """);
            statement.execute("""
                insert into content_article
                    (article_id, status, title, content, category_dict_code, tag_ids, cover_oss_id, del_flag)
                values
                    (100, '0', '标题', '正文', 11, '21,22', 99, '0')
                """);
        }
        return dataSource;
    }

    private static SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        String resource = "mapper/content/ContentArticleMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private static ContentArticle statusUpdate(String status, Long publishBy, Date publishTime) {
        ContentArticle article = new ContentArticle();
        article.setArticleId(100L);
        article.setStatus(status);
        article.setPublishBy(publishBy);
        article.setPublishTime(publishTime);
        article.setUpdateBy(9L);
        article.setUpdateTime(new Date(1_000L));
        return article;
    }

    private static void assertRow(DataSource dataSource, String status,
                                  Long publishBy, boolean hasPublishTime) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select * from content_article where article_id = 100")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("status")).isEqualTo(status);
            assertThat(result.getObject("publish_by", Long.class)).isEqualTo(publishBy);
            assertThat(result.getTimestamp("publish_time") != null).isEqualTo(hasPublishTime);
            assertThat(result.getLong("update_by")).isEqualTo(9L);
            assertThat(result.getTimestamp("update_time")).isNotNull();
            assertThat(result.getString("title")).isEqualTo("标题");
            assertThat(result.getString("content")).isEqualTo("正文");
            assertThat(result.getLong("category_dict_code")).isEqualTo(11L);
            assertThat(result.getString("tag_ids")).isEqualTo("21,22");
            assertThat(result.getLong("cover_oss_id")).isEqualTo(99L);
            assertThat(result.getString("del_flag")).isEqualTo("0");
        }
    }
}
