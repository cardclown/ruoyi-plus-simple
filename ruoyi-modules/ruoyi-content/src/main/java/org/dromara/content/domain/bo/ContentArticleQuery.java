package org.dromara.content.domain.bo;

import lombok.Data;

/**
 * 仅承载文章列表和导出的业务筛选条件，分页与排序由独立的 {@code PageQuery} 提供。
 */
@Data
public class ContentArticleQuery {

    /** 文章标题，模糊匹配 */
    private String title;

    /** 分类字典编码 */
    private Long categoryDictCode;

    /** 发布状态（0草稿 1已发布） */
    private String status;
}
