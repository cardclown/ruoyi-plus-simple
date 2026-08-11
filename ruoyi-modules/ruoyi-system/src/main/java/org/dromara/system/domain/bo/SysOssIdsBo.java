package org.dromara.system.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * OSS 批量 ID 请求。
 *
 * <p>使用 JSON 数组承载批量 ID，避免把逗号分隔的长 ID 串放入 URL。</p>
 */
@Data
public class SysOssIdsBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 待查询的 OSS ID，单次最多 100 个。 */
    @NotEmpty(message = "OSS ID不能为空")
    @Size(max = 100, message = "单次最多查询100个OSS文件")
    private List<@NotNull(message = "OSS ID不能为空") Long> ossIds;
}
