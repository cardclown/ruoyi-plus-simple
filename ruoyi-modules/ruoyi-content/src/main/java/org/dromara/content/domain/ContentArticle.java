package org.dromara.content.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;
import org.dromara.content.mybatis.LongListTypeHandler;

import java.io.Serial;
import java.util.Date;
import java.util.List;

/**
 * 文章对象 content_article。继承 {@link TenantEntity} 以获得租户、部门、创建与更新审计字段；
 * 这些字段由后台填充，不来自请求。
 *
 * @author Lion Li
 * @date 2026-08-07
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "content_article", autoResultMap = true)
public class ContentArticle extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 文章ID */
    @TableId(value = "article_id")
    private Long articleId;

    /** 文章标题 */
    private String title;

    /** 文章简介 */
    private String summary;

    /** Quill富文本HTML正文 */
    private String content;

    /** 分类字典编码 */
    private Long categoryDictCode;

    /** 标签字典编码集合，最多10个 */
    @TableField(value = "tag_ids", typeHandler = LongListTypeHandler.class, updateStrategy = FieldStrategy.ALWAYS)
    private List<Long> tagIds;

    /** 发布状态（0草稿 1已发布） */
    private String status;

    /** 发布人 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long publishBy;

    /** 发布时间 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Date publishTime;

    /** 删除标志（0正常 1删除） */
    @TableLogic
    private String delFlag;
}
