package org.dromara.content.service.support;

import org.dromara.common.core.domain.dto.DictDataDTO;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.content.domain.vo.ContentArticleDictOptionVo;
import org.dromara.content.enums.ContentArticleDictType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class ContentArticleDictionaryServiceTest {

    private final DictService dictService = mock(DictService.class);
    private final ContentArticleDictionaryService service = new ContentArticleDictionaryService(dictService);

    @Test
    void returnsStableOptionContractForFixedDictionaryType() {
        DictDataDTO source = dict(11L, "公司新闻", "company_news");
        source.setDictSort(2);
        source.setCssClass("article-category");
        source.setListClass("primary");
        source.setIsDefault("Y");
        when(dictService.getDictData(ContentArticleDictType.CATEGORY.getDictType())).thenReturn(List.of(source));

        List<ContentArticleDictOptionVo> options = service.listOptions(ContentArticleDictType.CATEGORY);

        assertThat(options).singleElement().satisfies(option -> {
            assertThat(option.getDictCode()).isEqualTo(11L);
            assertThat(option.getDictSort()).isEqualTo(2);
            assertThat(option.getDictLabel()).isEqualTo("公司新闻");
            assertThat(option.getDictValue()).isEqualTo("company_news");
            assertThat(option.getCssClass()).isEqualTo("article-category");
            assertThat(option.getListClass()).isEqualTo("primary");
            assertThat(option.getIsDefault()).isEqualTo("Y");
        });
    }

    @Test
    void validatesCategoryAndDeduplicatesTagsInRequestOrder() {
        when(dictService.getDictData(ContentArticleDictType.CATEGORY.getDictType()))
            .thenReturn(List.of(dict(11L, "公司新闻", "company_news")));
        when(dictService.getDictData(ContentArticleDictType.TAG.getDictType()))
            .thenReturn(List.of(dict(21L, "云计算", "cloud"), dict(22L, "开源", "open_source")));

        List<Long> tags = service.validateAndNormalize(11L, List.of(22L, 21L, 22L));

        assertThat(tags).containsExactly(22L, 21L);
    }

    @Test
    void acceptsArticleWithoutTags() {
        when(dictService.getDictData(ContentArticleDictType.CATEGORY.getDictType()))
            .thenReturn(List.of(dict(11L, "公司新闻", "company_news")));

        assertThat(service.validateAndNormalize(11L, null)).isEmpty();
    }

    @Test
    void rejectsCategoryOutsideCurrentTenantDictionary() {
        when(dictService.getDictData(ContentArticleDictType.CATEGORY.getDictType())).thenReturn(List.of());

        assertThatThrownBy(() -> service.validateAndNormalize(99L, null))
            .isInstanceOf(ServiceException.class)
            .hasMessage("文章分类不存在或不属于当前租户");
    }

    @Test
    void rejectsTagOutsideCurrentTenantDictionary() {
        when(dictService.getDictData(ContentArticleDictType.CATEGORY.getDictType()))
            .thenReturn(List.of(dict(11L, "公司新闻", "company_news")));
        when(dictService.getDictData(ContentArticleDictType.TAG.getDictType()))
            .thenReturn(List.of(dict(21L, "云计算", "cloud")));

        assertThatThrownBy(() -> service.validateAndNormalize(11L, List.of(21L, 99L)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("文章标签不存在或不属于当前租户")
            .hasMessageContaining("99");
    }

    private static DictDataDTO dict(Long code, String label, String value) {
        DictDataDTO data = new DictDataDTO();
        data.setDictCode(code);
        data.setDictLabel(label);
        data.setDictValue(value);
        return data;
    }
}
