package org.dromara.system.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 单个 OSS 删除结果。
 *
 * <p>批量删除允许部分成功，前端按 {@link #success} 决定是否从文件列表移除对应附件，
 * 提示内容统一使用后端返回的 {@link #message}。</p>
 */
@Data
@AllArgsConstructor
public class SysOssDeleteResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** OSS ID；使用字符串避免 JavaScript 大整数精度丢失。 */
    private String ossId;

    /** 是否已完成业务删除处理。 */
    private boolean success;

    /** 可直接展示给用户的处理结果。 */
    private String message;
}
