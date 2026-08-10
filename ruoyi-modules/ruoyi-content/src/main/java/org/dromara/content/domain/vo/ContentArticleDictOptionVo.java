package org.dromara.content.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文章分类、标签选项。
 */
@Data
public class ContentArticleDictOptionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 对应系统字典数据的字典编码。 */
    private Long dictCode;

    /** 对应系统字典数据的显示排序。 */
    private Integer dictSort;

    /** 对应系统字典数据的字典标签。 */
    private String dictLabel;

    /** 对应系统字典数据的字典键值。 */
    private String dictValue;

    /** 对应系统字典数据的 CSS 类名。 */
    private String cssClass;

    /** 对应系统字典数据的列表样式类。 */
    private String listClass;

    /** 对应系统字典数据的默认选中标识。 */
    private String isDefault;
}
