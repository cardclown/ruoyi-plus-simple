package org.dromara.content.service.support;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictDataDeleteValidator;
import org.dromara.content.enums.ContentArticleDictType;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * 禁止删除文章分类和标签字典项，避免现有文章产生悬空引用；展示内容可以修改，但编码不能删除。
 */
@Component
public class ContentArticleDictDeleteValidator implements DictDataDeleteValidator {

    /**
     * 拒绝删除文章模块使用的分类或标签编码。
     *
     * @param dictType 待删除字典项所属的字典类型
     * @param dictCodes 待删除的字典编码集合
     */
    @Override
    public void validate(String dictType, Collection<Long> dictCodes) {
        if (ContentArticleDictType.CATEGORY.getDictType().equals(dictType)
            || ContentArticleDictType.TAG.getDictType().equals(dictType)) {
            throw new ServiceException("文章分类和文章标签字典项禁止删除，可修改字典项内容");
        }
    }
}
