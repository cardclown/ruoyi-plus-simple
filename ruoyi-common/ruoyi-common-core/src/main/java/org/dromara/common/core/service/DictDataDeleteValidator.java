package org.dromara.common.core.service;

import java.util.Collection;

/**
 * 字典数据删除前的业务扩展校验。
 */
@FunctionalInterface
public interface DictDataDeleteValidator {

    /**
     * 校验指定字典类型的数据项是否允许删除。
     *
     * @param dictType 字典类型
     * @param dictCodes 待删除的字典编码
     */
    void validate(String dictType, Collection<Long> dictCodes);
}
