package org.dromara.content.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import cn.hutool.extra.spring.SpringUtil;
import io.github.linpeilie.Converter;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.dromara.content.domain.bo.ContentArticleQuery;
import org.dromara.content.domain.vo.ContentArticleVo;
import org.dromara.content.service.impl.ContentArticleServiceImpl;
import org.dromara.content.service.support.ContentArticleAttachmentManager;
import org.dromara.content.service.support.ContentArticleDictionaryService;
import org.dromara.content.service.support.ContentArticleHtmlSanitizer;
import org.dromara.content.service.support.ContentArticleOperationContext;
import org.dromara.content.service.support.ContentArticlePublishPolicy;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Tag("dev")
class ContentArticleAdminListPersistenceTest {

    @Test
    void adminListReturnsStoredArticleContentThroughRealMapperAndService() throws Exception {
        try (AnnotationConfigApplicationContext context = mapstructContext();
             SqlSession session = sqlSessionFactory(dataSource()).openSession(true)) {
            ContentArticleMapper articleMapper = session.getMapper(ContentArticleMapper.class);
            ContentArticleAttachmentManager attachmentManager = mock(ContentArticleAttachmentManager.class);
            ContentArticleServiceImpl service = new ContentArticleServiceImpl(
                articleMapper,
                mock(ContentArticleTagMapper.class),
                mock(ContentArticleDictionaryService.class),
                new ContentArticleHtmlSanitizer(),
                new ContentArticlePublishPolicy(),
                mock(ContentArticleOperationContext.class),
                attachmentManager);

            List<ContentArticleVo> result = service.queryList(new ContentArticleQuery());

            assertThat(result).singleElement().satisfies(article -> {
                assertThat(article.getArticleId()).isEqualTo(100L);
                assertThat(article.getContent()).isEqualTo("数据库完整正文");
            });
        }
    }

    private static AnnotationConfigApplicationContext mapstructContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(SpringUtil.class);
        context.registerBean(Converter.class, () -> new Converter());
        context.refresh();
        return context;
    }

    private static DataSource dataSource() throws SQLException {
        DataSource dataSource = new PooledDataSource(
            "org.h2.Driver",
            "jdbc:h2:mem:admin_article_list_" + UUID.randomUUID()
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
                    tenant_id varchar(20),
                    title varchar(200) not null,
                    summary varchar(500),
                    content varchar(2000),
                    category_dict_code bigint not null,
                    tag_ids varchar(200),
                    status char(1) not null,
                    publish_by bigint,
                    publish_time timestamp,
                    create_dept bigint,
                    create_by bigint,
                    create_time timestamp,
                    update_by bigint,
                    update_time timestamp,
                    del_flag char(1) not null
                )
                """);
            statement.execute("""
                insert into content_article
                    (article_id, tenant_id, title, summary, content, category_dict_code,
                     tag_ids, status, del_flag)
                values
                    (100, '140872', '标题', '简介', '数据库完整正文', 11, '21,22', '0', '0')
                """);
        }
        return dataSource;
    }

    private static SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment(
            "test", new JdbcTransactionFactory(), dataSource));

        String resource = "mapper/content/ContentArticleMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(
                input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }
}
