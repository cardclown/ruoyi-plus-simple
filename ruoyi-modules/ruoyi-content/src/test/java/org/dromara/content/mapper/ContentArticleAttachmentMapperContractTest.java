package org.dromara.content.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.dromara.content.domain.ContentArticleAttachment;
import org.dromara.content.enums.ContentArticleAttachmentType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ContentArticleAttachmentMapperContractTest {

    @Test
    void selectsOrderedRelationsAndDeletesOnlyRequestedArticles() throws SQLException {
        try (SqlSession session = sqlSessionFactory().openSession(true)) {
            ContentArticleAttachmentMapper mapper = session.getMapper(ContentArticleAttachmentMapper.class);
            insert(mapper, 1L, 10L, 1001L, ContentArticleAttachmentType.VIDEO.getCode(), 0);
            insert(mapper, 2L, 10L, 1002L, ContentArticleAttachmentType.IMAGE.getCode(), 1);
            insert(mapper, 3L, 10L, 1003L, ContentArticleAttachmentType.IMAGE.getCode(), 0);
            insert(mapper, 4L, 20L, 2001L, ContentArticleAttachmentType.VIDEO.getCode(), 0);
            insert(mapper, 5L, 20L, 2002L, ContentArticleAttachmentType.IMAGE.getCode(), 0);
            insert(mapper, 6L, 30L, 3001L, ContentArticleAttachmentType.IMAGE.getCode(), 0);

            assertThat(mapper.selectByArticleIds(List.of(20L, 10L)))
                .extracting(
                    ContentArticleAttachment::getArticleId,
                    ContentArticleAttachment::getAttachmentType,
                    ContentArticleAttachment::getSortNum,
                    ContentArticleAttachment::getOssId
                )
                .containsExactly(
                    tuple(10L, "0", 0, 1003L),
                    tuple(10L, "0", 1, 1002L),
                    tuple(10L, "1", 0, 1001L),
                    tuple(20L, "0", 0, 2002L),
                    tuple(20L, "1", 0, 2001L)
                );

            assertThat(mapper.selectByArticleAndOssForUpdate(10L, 1002L))
                .extracting(ContentArticleAttachment::getArticleAttachmentId,
                    ContentArticleAttachment::getArticleId, ContentArticleAttachment::getOssId)
                .containsExactly(2L, 10L, 1002L);
            assertThat(mapper.selectByArticleAndOssForUpdate(20L, 1002L)).isNull();

            assertThat(mapper.deleteByArticleIds(List.of(10L))).isEqualTo(3);
            assertThat(mapper.selectByArticleIds(List.of(10L, 20L)))
                .extracting(ContentArticleAttachment::getArticleId, ContentArticleAttachment::getOssId)
                .containsExactly(tuple(20L, 2002L), tuple(20L, 2001L));
            assertThat(mapper.selectByArticleIds(List.of(30L)))
                .extracting(ContentArticleAttachment::getOssId)
                .containsExactly(3001L);

            assertThat(mapper.selectByArticleIds(null)).isEmpty();
            assertThat(mapper.selectByArticleIds(List.of())).isEmpty();
            assertThat(mapper.deleteByArticleIds(null)).isZero();
            assertThat(mapper.deleteByArticleIds(List.of())).isZero();
        }
    }

    private static SqlSessionFactory sqlSessionFactory() throws SQLException {
        DataSource dataSource = new PooledDataSource(
            "org.h2.Driver",
            "jdbc:h2:mem:content_article_attachment_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        );
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                create table content_article_attachment
                (
                    article_attachment_id bigint primary key,
                    article_id bigint not null,
                    oss_id bigint not null unique,
                    attachment_type char(1) not null,
                    sort_num integer not null,
                    create_by bigint,
                    create_time timestamp
                )
                """);
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(ContentArticleAttachmentMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private static void insert(ContentArticleAttachmentMapper mapper, Long relationId, Long articleId,
                               Long ossId, String attachmentType, Integer sortNum) {
        ContentArticleAttachment attachment = new ContentArticleAttachment();
        attachment.setArticleAttachmentId(relationId);
        attachment.setArticleId(articleId);
        attachment.setOssId(ossId);
        attachment.setAttachmentType(attachmentType);
        attachment.setSortNum(sortNum);
        assertThat(mapper.insert(attachment)).isEqualTo(1);
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }
}
