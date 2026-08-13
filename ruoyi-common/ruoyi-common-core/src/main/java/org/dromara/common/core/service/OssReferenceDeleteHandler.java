package org.dromara.common.core.service;

/**
 * OSS 业务引用删除处理器。
 *
 * <p>公共 OSS 删除服务只从已持久化的附件元数据读取业务类型和业务 ID，再按业务类型选择处理器。
 * 处理器负责校验当前用户对业务数据的操作权限并删除业务关联，但不直接删除 OSS 对象；对象清理由
 * 公共服务在同一事务中统一调度。处理器应先锁定业务主记录和关联记录，公共服务随后锁定 OSS 行并
 * 复核归属，以维持“业务数据在前、OSS 在后”的统一锁顺序。</p>
 */
public interface OssReferenceDeleteHandler {

    /**
     * 返回该处理器支持的业务引用类型。
     *
     * @return 与 OSS 元数据 {@code refType} 完全一致的稳定编码
     */
    String supportedReferenceType();

    /**
     * 校验当前用户是否有权删除指定业务记录中的附件。
     *
     * <p>该方法用于附件已经进入待删除状态时的幂等请求，此时业务关联可能已经不存在，
     * 因此权限校验不能依赖附件关联记录仍然存在。</p>
     *
     * @param referenceId 后端从 OSS 元数据读取的业务记录 ID
     */
    void validateDeleteAccess(String referenceId);

    /**
     * 校验权限并删除一条业务附件关联。
     *
     * @param ossId OSS ID
     * @param referenceId 后端从 OSS 元数据读取的业务记录 ID
     */
    void deleteReference(Long ossId, String referenceId);
}
