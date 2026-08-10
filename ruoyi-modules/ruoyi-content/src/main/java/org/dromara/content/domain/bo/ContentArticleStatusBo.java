package org.dromara.content.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 文章发布状态修改请求。
 */
@Data
public class ContentArticleStatusBo {

    /**
     * 文章 ID。
     */
    @NotNull(message = "文章ID不能为空")
    private Long articleId;

    /**
     * 目标发布状态：0 草稿，1 已发布。
     */
    @NotBlank(message = "发布状态不能为空")
    @Pattern(regexp = "^[01]$", message = "发布状态只能为0或1")
    private String status;
}
