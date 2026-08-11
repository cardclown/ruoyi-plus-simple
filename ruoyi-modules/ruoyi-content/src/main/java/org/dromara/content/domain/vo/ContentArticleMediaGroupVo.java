package org.dromara.content.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 单篇公开文章的媒体解析结果。
 *
 * <p>批量接口按文章分组返回媒体，避免前端根据多篇文章逐一发起 URL 解析请求。</p>
 */
@Data
public class ContentArticleMediaGroupVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 文章 ID。 */
    private Long articleId;

    /** 该文章当前可访问的全部图片和视频。 */
    private List<ContentArticleMediaVo> media;
}
