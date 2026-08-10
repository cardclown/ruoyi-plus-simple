package org.dromara.content.service.support;

import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 在指定租户上下文中执行文章查询，并保证线程变量在回调结束后清理。
 */
@Component
public class ContentArticleTenantScope {

    public <T> T execute(String tenantId, Supplier<T> action) {
        return TenantHelper.dynamic(tenantId, action);
    }
}
