package org.dromara.content.service.support;

import cn.dev33.satoken.stp.StpUtil;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 隔离登录态和系统时间：生产环境读取当前登录用户与当前时间，测试可替换此依赖以获得确定的上下文。
 */
@Component
public class ContentArticleOperationContext {

    /**
     * 获取生产环境中的当前登录用户 ID。
     *
     * @return 当前操作人的用户 ID
     */
    public Long currentUserId() {
        return LoginHelper.getUserId();
    }

    /**
     * 获取本次操作使用的当前系统时间。
     *
     * @return 新创建的当前时间对象
     */
    public Date now() {
        return new Date();
    }

    /**
     * 判断当前用户是否拥有文章编辑权限。
     *
     * @return 具有文章编辑权限时返回 {@code true}
     */
    public boolean canEditArticle() {
        return StpUtil.hasPermission("content:article:edit");
    }
}
