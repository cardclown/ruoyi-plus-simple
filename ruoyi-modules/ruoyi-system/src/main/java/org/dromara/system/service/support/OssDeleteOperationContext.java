package org.dromara.system.service.support;

import cn.dev33.satoken.stp.StpUtil;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Component;

/**
 * 隔离公共附件删除所需的登录用户和权限上下文，便于服务测试且避免业务层信任前端权限参数。
 */
@Component
public class OssDeleteOperationContext {

    /**
     * 获取当前登录用户 ID。
     *
     * @return 当前用户 ID；登录信息异常时可能为空
     */
    public Long currentUserId() {
        return LoginHelper.getUserId();
    }

    /**
     * 判断当前用户是否拥有系统 OSS 管理删除权限。
     *
     * @return 具有系统 OSS 删除权限时返回 {@code true}
     */
    public boolean canManageOss() {
        return StpUtil.hasPermission("system:oss:remove");
    }
}
