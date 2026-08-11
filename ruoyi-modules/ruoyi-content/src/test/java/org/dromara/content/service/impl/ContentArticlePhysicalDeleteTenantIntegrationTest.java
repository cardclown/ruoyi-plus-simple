package org.dromara.content.service.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.tenant.handle.PlusTenantLineHandler;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.common.tenant.properties.TenantProperties;
import org.dromara.content.mapper.ContentArticleMapper;
import org.dromara.content.mapper.ContentArticleTagMapper;
import org.dromara.content.service.support.ContentArticleAttachmentManager;
import org.dromara.content.service.support.ContentArticleDictionaryService;
import org.dromara.content.service.support.ContentArticleHtmlSanitizer;
import org.dromara.content.service.support.ContentArticleOperationContext;
import org.dromara.content.service.support.ContentArticlePublishPolicy;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@Tag("dev")
class ContentArticlePhysicalDeleteTenantIntegrationTest {

    @Test
    void physicalDeleteRejectsAnotherTenantsArticleBeforeDeletingAttachments() throws Exception {
        DataSource dataSource = dataSource();
        try (AnnotationConfigApplicationContext context = tenantContext();
             SqlSession session = sqlSessionFactory(dataSource).openSession(true)) {
            ContentArticleAttachmentManager attachmentManager = mock(ContentArticleAttachmentManager.class);
            ContentArticleServiceImpl service = new ContentArticleServiceImpl(
                session.getMapper(ContentArticleMapper.class),
                mock(ContentArticleTagMapper.class),
                mock(ContentArticleDictionaryService.class),
                new ContentArticleHtmlSanitizer(),
                new ContentArticlePublishPolicy(),
                mock(ContentArticleOperationContext.class),
                attachmentManager);

            TenantHelper.dynamic("140872", () ->
                assertThatThrownBy(() -> service.physicalDeleteByIds(List.of(100L)))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("只能物理删除已逻辑删除且有权操作的文章"));

            verify(attachmentManager, never()).deletePermanently(List.of(100L));
            assertThat(countArticles(dataSource)).isEqualTo(1);
        }
    }

    private static AnnotationConfigApplicationContext tenantContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "tenant.enable=true");
        context.registerBean(SpringUtil.class);
        context.refresh();
        return context;
    }

    private static DataSource dataSource() throws SQLException {
        DataSource dataSource = new PooledDataSource(
            "org.h2.Driver",
            "jdbc:h2:mem:physical_delete_tenant_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "sa",
            "");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                create table content_article
                (
                    article_id bigint primary key,
                    tenant_id varchar(20) not null,
                    del_flag char(1) not null
                )
                """);
            statement.execute("""
                insert into content_article (article_id, tenant_id, del_flag)
                values (100, '420542', '1')
                """);
        }
        return dataSource;
    }

    private static int countArticles(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             var result = statement.executeQuery("select count(*) from content_article")) {
            result.next();
            return result.getInt(1);
        }
    }

    private static SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment(
            "test", new JdbcTransactionFactory(), dataSource));

        TenantProperties tenantProperties = new TenantProperties();
        tenantProperties.setEnable(true);
        tenantProperties.setExcludes(List.of());
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(
            new PlusTenantLineHandler(tenantProperties)));
        configuration.addInterceptor(interceptor);

        String resource = "mapper/content/ContentArticleMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(
                input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }
}
