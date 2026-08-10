package org.dromara.content.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 文章标签关联对象 content_article_tag。
 */
@Data
@TableName("content_article_tag")
public class ContentArticleTag implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 文章标签关联主键。 */
    @TableId(value = "article_tag_id")
    private Long articleTagId;

    /** 所属文章 ID。 */
    private Long articleId;

    /** 标签字典编码。 */
    private Long tagDictCode;

    /** 创建人用户 ID，由文章服务在创建标签关系时显式写入。 */
    private Long createBy;

    /** 创建时间，由文章服务在创建标签关系时显式写入。 */
    private Date createTime;
}
