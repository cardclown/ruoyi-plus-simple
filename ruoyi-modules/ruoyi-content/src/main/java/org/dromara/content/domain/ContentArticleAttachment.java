package org.dromara.content.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 文章附件关联对象 content_article_attachment。
 */
@Data
@TableName("content_article_attachment")
public class ContentArticleAttachment implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 文章附件关联主键。 */
    @TableId(value = "article_attachment_id")
    private Long articleAttachmentId;

    /** 所属文章 ID。 */
    private Long articleId;

    /** OSS 附件 ID。 */
    private Long ossId;

    /** 附件类型（0图片 1视频）。 */
    private String attachmentType;

    /** 同类附件排序号。 */
    private Integer sortNum;

    /** 创建人用户 ID。 */
    private Long createBy;

    /** 创建时间。 */
    private Date createTime;
}
