package org.dromara.content.service.impl;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.content.domain.bo.ContentArticleQuery;
import org.dromara.content.domain.vo.ContentArticlePublicVo;
import org.dromara.content.service.support.ContentArticlePublishedQueryService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class ZhongAnArticlePublicFacadeTest {

    private final ContentArticlePublishedQueryService queryService =
        mock(ContentArticlePublishedQueryService.class);
    private final ZhongAnArticlePublicFacade facade =
        new ZhongAnArticlePublicFacade(queryService);

    @Test
    void pageAlwaysUsesZhongAnTenant() {
        ContentArticleQuery query = new ContentArticleQuery();
        PageQuery pageQuery = new PageQuery(10, 1);
        TableDataInfo<ContentArticlePublicVo> expected = TableDataInfo.build();
        when(queryService.queryPage("140872", query, pageQuery)).thenReturn(expected);

        assertThat(facade.queryPage(query, pageQuery)).isSameAs(expected);
        verify(queryService).queryPage("140872", query, pageQuery);
    }

    @Test
    void detailAlwaysUsesZhongAnTenant() {
        ContentArticlePublicVo expected = new ContentArticlePublicVo();
        when(queryService.queryById("140872", 100L)).thenReturn(expected);

        assertThat(facade.queryById(100L)).isSameAs(expected);
        verify(queryService).queryById("140872", 100L);
    }
}
