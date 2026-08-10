package org.dromara.common.core.domain.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * OSS对象
 *
 * @author Lion Li
 */
@Data
@NoArgsConstructor
public class OssDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 对象存储主键
     */
    private Long ossId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 原名
     */
    private String originalName;

    /**
     * 文件后缀名
     */
    private String fileSuffix;

    /**
     * URL地址
     */
    private String url;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * MIME 类型
     */
    private String contentType;

    /**
     * 技术文件分类（IMAGE 或 VIDEO）
     */
    private String fileType;

    /**
     * 所属业务类型
     */
    private String bizType;

    /**
     * 业务引用 ID
     */
    private String refId;

    /**
     * 业务引用类型
     */
    private String refType;

    /**
     * 是否为临时文件
     */
    private Boolean isTemp;

}
