package org.dromara.system.domain.bo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 公共 OSS 批量删除请求。
 *
 * <p>前端只提交 OSS ID；业务类型、业务记录 ID 和临时状态必须由后端读取真实元数据判断。</p>
 */
@Data
public class SysOssDeleteBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 待删除的 OSS ID，单次最多 100 个。 */
    @NotEmpty(message = "OSS ID不能为空")
    @Size(max = 100, message = "单次最多删除100个OSS文件")
    private List<@NotNull(message = "OSS ID不能为空") Long> ossIds;
}
