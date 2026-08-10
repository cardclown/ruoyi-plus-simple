package org.dromara.content.service.support;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.content.domain.ContentArticle;
import org.dromara.content.domain.bo.ContentArticleQuery;
import org.dromara.content.domain.vo.ContentArticlePublicVo;
import org.dromara.content.mapper.ContentArticleMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class ContentArticlePublishedQueryServiceTest {

    private final ContentArticleMapper mapper = mock(ContentArticleMapper.class);
    private final AtomicReference<String> tenantSeen = new AtomicReference<>();
    private final ContentArticleTenantScope tenantScope = new ContentArticleTenantScope() {
        @Override
        public <T> T execute(String tenantId, Supplier<T> action) {
            tenantSeen.set(tenantId);
            return action.get();
        }
    };
    private final ContentArticlePublishedQueryService service =
        new ContentArticlePublishedQueryService(mapper, tenantScope);

    @Test
    void pagePassesTenantAndOnlyAllowedFiltersToMapper() {
        ContentArticleQuery query = new ContentArticleQuery();
        query.setTitle("工程");
        query.setCategoryDictCode(11L);
        query.setStatus("0");
        Page<ContentArticle> page = new Page<>(1, 10);
        page.setTotal(1);
        page.setRecords(List.of(article(100L, "正文")));
        when(mapper.selectPublishedArticlePage(any(), eq("140872"), eq("工程"), eq(11L)))
            .thenReturn(page);

        TableDataInfo<ContentArticlePublicVo> result = service.queryPage(
            "140872", query, new PageQuery(10, 1));

        assertThat(tenantSeen.get()).isEqualTo("140872");
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows()).singleElement().satisfies(vo -> {
            assertThat(vo.getArticleId()).isEqualTo(100L);
            assertThat(vo.getContent()).isEqualTo("正文");
        });
        verify(mapper).selectPublishedArticlePage(any(), eq("140872"), eq("工程"), eq(11L));
    }

    @Test
    void pageAcceptsNullQueryAsEmptyFilters() {
        Page<ContentArticle> page = new Page<>(1, 10);
        when(mapper.selectPublishedArticlePage(any(), eq("140872"), eq(null), eq(null)))
            .thenReturn(page);

        TableDataInfo<ContentArticlePublicVo> result = service.queryPage(
            "140872", null, new PageQuery(10, 1));

        assertThat(tenantSeen.get()).isEqualTo("140872");
        assertThat(result.getRows()).isEmpty();
        verify(mapper).selectPublishedArticlePage(any(), eq("140872"), eq(null), eq(null));
    }

    @Test
    void detailReturnsOnlyPublicFieldsFromMapperResult() {
        when(mapper.selectPublishedArticleById("140872", 100L))
            .thenReturn(article(100L, "正文"));

        ContentArticlePublicVo result = service.queryById("140872", 100L);

        assertThat(tenantSeen.get()).isEqualTo("140872");
        assertThat(result.getArticleId()).isEqualTo(100L);
        assertThat(result.getContent()).isEqualTo("正文");
    }

    @Test
    void detailUsesUniformNotFoundMessage() {
        when(mapper.selectPublishedArticleById("140872", 100L)).thenReturn(null);

        assertThatThrownBy(() -> service.queryById("140872", 100L))
            .isInstanceOf(ServiceException.class)
            .hasMessage("文章不存在");
    }

    @Test
    void blankTenantIsRejectedBeforeQuery() {
        assertThatThrownBy(() -> service.queryById(" ", 100L))
            .isInstanceOf(ServiceException.class)
            .hasMessage("租户ID不能为空");

        verifyNoInteractions(mapper);
    }

    private static ContentArticle article(Long articleId, String content) {
        ContentArticle article = new ContentArticle();
        article.setArticleId(articleId);
        article.setTitle("标题");
        article.setSummary("简介");
        article.setContent(content);
        article.setCategoryDictCode(11L);
        article.setTagIds(List.of(21L));
        article.setCoverOssId(99L);
        article.setStatus("1");
        article.setPublishBy(9L);
        article.setPublishTime(new Date(1_000L));
        article.setTenantId("140872");
        return article;
    }
}
