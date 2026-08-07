package org.dromara.content.domain.bo;

import lombok.Data;

/**
 * 文章列表与导出查询条件。
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
