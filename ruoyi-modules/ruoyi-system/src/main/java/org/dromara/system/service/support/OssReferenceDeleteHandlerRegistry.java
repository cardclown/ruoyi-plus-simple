package org.dromara.system.service.support;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.OssReferenceDeleteHandler;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OSS 业务引用删除处理器注册表。
 *
 * <p>每个 {@code refType} 只能注册一个处理器，重复注册会在应用启动时失败，避免删除请求被
 * 不确定的业务实现处理。</p>
 */
@Component
public class OssReferenceDeleteHandlerRegistry {

    private final Map<String, OssReferenceDeleteHandler> handlers;

    /**
     * 创建处理器注册表。
     *
     * @param candidates Spring 容器中的全部业务引用删除处理器
     */
    public OssReferenceDeleteHandlerRegistry(List<OssReferenceDeleteHandler> candidates) {
        Map<String, OssReferenceDeleteHandler> registered = new LinkedHashMap<>();
        for (OssReferenceDeleteHandler handler : candidates) {
            String referenceType = handler.supportedReferenceType();
            if (StringUtils.isBlank(referenceType)) {
                throw new IllegalStateException("OSS业务引用删除处理器类型不能为空");
            }
            if (registered.putIfAbsent(referenceType, handler) != null) {
                throw new IllegalStateException("OSS业务引用删除处理器重复: " + referenceType);
            }
        }
        this.handlers = Map.copyOf(registered);
    }

    /**
     * 获取指定业务类型的唯一删除处理器。
     *
     * @param referenceType OSS 元数据中的业务类型
     * @return 对应业务处理器
     * @throws ServiceException 尚未接入该业务类型时抛出
     */
    public OssReferenceDeleteHandler require(String referenceType) {
        OssReferenceDeleteHandler handler = handlers.get(referenceType);
        if (handler == null) {
            throw new ServiceException("附件所属业务暂不支持删除");
        }
        return handler;
    }
}
