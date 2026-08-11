package org.dromara.content.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 文章批量 ID 请求。
 *
 * <p>逻辑删除、物理删除和公开媒体查询共用该结构，前端始终以 JSON 数组提交。</p>
 */
@Data
public class ContentArticleIdsBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 待处理的文章 ID，单次最多 100 个。 */
    @NotEmpty(message = "文章ID不能为空")
    @Size(max = 100, message = "单次最多处理100篇文章")
    private List<@NotNull(message = "文章ID不能为空") Long> articleIds;
}
