package org.dromara.content.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 官网匿名接口使用的文章响应对象，只暴露公开展示字段。
 */
@Data
public class ContentArticlePublicVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 文章 ID。 */
    private Long articleId;

    /** 文章标题。 */
    private String title;

    /** 文章简介。 */
    private String summary;

    /** 文章正文；列表查询不加载该字段。 */
    private String content;

    /** 分类字典编码。 */
    private Long categoryDictCode;

    /** 标签字典编码集合。 */
    private List<Long> tagIds;

    /** 封面 OSS 附件 ID。 */
    private Long coverOssId;

    /** 发布时间。 */
    private Date publishTime;
}
