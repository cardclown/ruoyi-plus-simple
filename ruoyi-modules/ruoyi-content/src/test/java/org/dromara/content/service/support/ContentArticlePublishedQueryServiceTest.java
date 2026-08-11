package org.dromara.content.service.support;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.content.domain.ContentArticle;
import org.dromara.content.domain.bo.ContentArticleQuery;
import org.dromara.content.domain.vo.ContentArticlePublicVo;
import org.dromara.content.domain.vo.ContentArticleMediaVo;
import org.dromara.content.mapper.ContentArticleMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

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
    private final ContentArticleAttachmentManager attachmentManager = mock(ContentArticleAttachmentManager.class);
    private final AtomicReference<String> tenantSeen = new AtomicReference<>();
    private final ContentArticleTenantScope tenantScope = new ContentArticleTenantScope() {
        @Override
        public <T> T execute(String tenantId, Supplier<T> action) {
            tenantSeen.set(tenantId);
            return action.get();
        }
    };
    private final ContentArticlePublishedQueryService service =
        new ContentArticlePublishedQueryService(mapper, tenantScope, attachmentManager);

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
        org.mockito.Mockito.doAnswer(invocation -> {
            List<ContentArticlePublicVo> articles = invocation.getArgument(0);
            articles.forEach(article -> {
                article.setAttachmentOssIds(List.of(10L));
                article.setVideoOssIds(List.of(20L));
            });
            return null;
        }).when(attachmentManager).populatePublic(any());

        TableDataInfo<ContentArticlePublicVo> result = service.queryPage(
            "140872", query, new PageQuery(10, 1));

        assertThat(tenantSeen.get()).isEqualTo("140872");
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows()).singleElement().satisfies(vo -> {
            assertThat(vo.getArticleId()).isEqualTo(100L);
            assertThat(vo.getContent()).isEqualTo("正文");
            assertThat(vo.getAttachmentOssIds()).containsExactly(10L);
            assertThat(vo.getVideoOssIds()).containsExactly(20L);
        });
        verify(mapper).selectPublishedArticlePage(any(), eq("140872"), eq("工程"), eq(11L));
        verify(attachmentManager).populatePublic(result.getRows());
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
    void pageUsesPublicDefaultsWhenPageQueryIsNull() {
        Page<?> mapperPage = captureMapperPage(null);

        assertThat(mapperPage.getCurrent()).isEqualTo(1);
        assertThat(mapperPage.getSize()).isEqualTo(10);
    }

    @Test
    void pageUsesPublicDefaultsWhenPaginationValuesAreNull() {
        Page<?> mapperPage = captureMapperPage(new PageQuery(null, null));

        assertThat(mapperPage.getCurrent()).isEqualTo(1);
        assertThat(mapperPage.getSize()).isEqualTo(10);
    }

    @ParameterizedTest
    @CsvSource({
        "0, -7",
        "-4, 0"
    })
    void pageNormalizesNonPositivePaginationValues(int pageNum, int pageSize) {
        Page<?> mapperPage = captureMapperPage(new PageQuery(pageSize, pageNum));

        assertThat(mapperPage.getCurrent()).isEqualTo(1);
        assertThat(mapperPage.getSize()).isEqualTo(10);
    }

    @Test
    void pageCapsOversizedPageSize() {
        Page<?> mapperPage = captureMapperPage(new PageQuery(1_000, 3));

        assertThat(mapperPage.getCurrent()).isEqualTo(3);
        assertThat(mapperPage.getSize()).isEqualTo(100);
    }

    @Test
    void pageIgnoresCallerProvidedSorting() {
        PageQuery pageQuery = new PageQuery(20, 2);
        pageQuery.setOrderByColumn("title");
        pageQuery.setIsAsc("asc");

        Page<?> mapperPage = captureMapperPage(pageQuery);

        assertThat(mapperPage.getCurrent()).isEqualTo(2);
        assertThat(mapperPage.getSize()).isEqualTo(20);
        assertThat(mapperPage.orders()).isEmpty();
    }

    @Test
    void detailReturnsOnlyPublicFieldsFromMapperResult() {
        when(mapper.selectPublishedArticleById("140872", 100L))
            .thenReturn(article(100L, "正文"));
        org.mockito.Mockito.doAnswer(invocation -> {
            ContentArticlePublicVo article = invocation.getArgument(0);
            article.setAttachmentOssIds(List.of(10L));
            article.setVideoOssIds(List.of(20L));
            return null;
        }).when(attachmentManager).populate(any(ContentArticlePublicVo.class));

        ContentArticlePublicVo result = service.queryById("140872", 100L);

        assertThat(tenantSeen.get()).isEqualTo("140872");
        assertThat(result.getArticleId()).isEqualTo(100L);
        assertThat(result.getContent()).isEqualTo("正文");
        assertThat(result.getAttachmentOssIds()).containsExactly(10L);
        assertThat(result.getVideoOssIds()).containsExactly(20L);
        verify(attachmentManager).populate(result);
    }

    @Test
    void detailUsesUniformNotFoundMessage() {
        when(mapper.selectPublishedArticleById("140872", 100L)).thenReturn(null);

        assertThatThrownBy(() -> service.queryById("140872", 100L))
            .isInstanceOf(ServiceException.class)
            .hasMessage("文章不存在");
    }

    @Test
    void mediaRejectsAnUnpublishedOrForeignArticleBeforeReadingRelations() {
        when(mapper.selectPublishedArticleById("140872", 100L)).thenReturn(null);

        assertThatThrownBy(() -> service.queryMedia("140872", 100L))
            .isInstanceOf(ServiceException.class)
            .hasMessage("文章不存在");

        verifyNoInteractions(attachmentManager);
    }

    @Test
    void mediaUsesPublishedArticlePathInsideTheFixedTenantScope() {
        ContentArticleMediaVo media = new ContentArticleMediaVo();
        media.setOssId(10L);
        when(mapper.selectPublishedArticleById("140872", 100L)).thenReturn(article(100L, "正文"));
        when(attachmentManager.resolvePublicMedia(100L)).thenReturn(List.of(media));

        assertThat(service.queryMedia("140872", 100L)).containsExactly(media);
        assertThat(tenantSeen.get()).isEqualTo("140872");
        verify(mapper).selectPublishedArticleById("140872", 100L);
        verify(attachmentManager).resolvePublicMedia(100L);
    }

    @Test
    void blankTenantIsRejectedBeforeQuery() {
        assertThatThrownBy(() -> service.queryById(" ", 100L))
            .isInstanceOf(ServiceException.class)
            .hasMessage("租户ID不能为空");

        verifyNoInteractions(mapper);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Page<?> captureMapperPage(PageQuery pageQuery) {
        when(mapper.selectPublishedArticlePage(any(), eq("140872"), eq(null), eq(null)))
            .thenReturn(new Page<>());

        service.queryPage("140872", null, pageQuery);

        ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(mapper).selectPublishedArticlePage(
            pageCaptor.capture(), eq("140872"), eq(null), eq(null));
        return pageCaptor.getValue();
    }

    private static ContentArticle article(Long articleId, String content) {
        ContentArticle article = new ContentArticle();
        article.setArticleId(articleId);
        article.setTitle("标题");
        article.setSummary("简介");
        article.setContent(content);
        article.setCategoryDictCode(11L);
        article.setTagIds(List.of(21L));
        article.setStatus("1");
        article.setPublishBy(9L);
        article.setPublishTime(new Date(1_000L));
        article.setTenantId("140872");
        return article;
    }
}
