package org.dromara.content.service.support;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictDataDeleteValidator;
import org.dromara.content.enums.ContentArticleDictType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class ContentArticleDictDeleteValidatorTest {

    private final DictDataDeleteValidator validator = createValidator();

    @Test
    void rejectsDeletingArticleCategoryItems() {
        assertProtected(ContentArticleDictType.CATEGORY.getDictType());
    }

    @Test
    void rejectsDeletingArticleTagItems() {
        assertProtected(ContentArticleDictType.TAG.getDictType());
    }

    @Test
    void ignoresOtherDictionaryTypes() {
        assertThatCode(() -> validator.validate("sys_user_sex", List.of(1L)))
            .doesNotThrowAnyException();
    }

    private void assertProtected(String dictType) {
        assertThatThrownBy(() -> validator.validate(dictType, List.of(1L)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("禁止删除");
    }

    private DictDataDeleteValidator createValidator() {
        try {
            Class<?> type = Class.forName(
                "org.dromara.content.service.support.ContentArticleDictDeleteValidator");
            return (DictDataDeleteValidator) type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("文章字典删除保护器尚未实现", exception);
        }
    }
}
