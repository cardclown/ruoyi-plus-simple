package org.dromara.content.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 匿名文章媒体解析结果，只暴露展示所需的当前元数据。
 */
@Data
public class ContentArticleMediaVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long ossId;
    private String url;
    private String originalName;
    private String fileSuffix;
    private Long fileSize;
    private String contentType;
    private String fileType;
}
