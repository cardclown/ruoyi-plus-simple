# RuoYi-Vue-Plus 5.X 剪枝与 PostgreSQL 初始化设计

## 目标

把当前官方 5.X 基线收敛为适合现有前端项目的单体后端，同时完整保留原生多租户体系，并使用 PostgreSQL、Redis 和 MinIO 作为本地基础设施。

## 保留范围

- 多租户、租户套餐和租户数据隔离。
- 用户、部门、岗位、角色、菜单和数据权限。
- 代码生成器。
- 密码登录及 Sa-Token 认证。
- Actuator 端点及其 Basic Auth 保护。
- Redis、OSS、MinIO、接口文档、日志、Excel、幂等、限流、加密、脱敏等基础能力。

## 删除范围

- Demo 示例模块。
- WarmFlow 工作流及示例、SQL 和菜单。
- SnailJob 调度客户端、业务模块、独立服务、SQL 和菜单。
- Spring Boot Admin 独立监控服务及客户端注册；Actuator 本身保留。
- 邮件、短信、第三方社交登录和微信小程序登录。
- 验证码登录链路。
- 与上述功能直接绑定的依赖、配置、控制器、领域对象、运行配置和 Nginx 路由。
- 5.X 本身不存在的 AI、MCP、MQTT、Elasticsearch 模块无需额外处理。

## 迁移方式

不直接应用 6.X stash。以 5.X 代码为基准，按模块边界逐项删除，并清理所有编译期引用。这样保留 5.X 原生 `ruoyi-common-tenant`、`SysTenant`、`SysTenantPackage`、MyBatis 租户拦截、缓存前缀和租户登录逻辑。

## PostgreSQL

- 以 `script/sql/postgres/postgres_ry_vue_5.X.sql` 为基础。
- 保留 `sys_tenant`、`sys_tenant_package` 及核心业务表的 `tenant_id`。
- 只删除已裁剪功能对应的表、菜单和演示数据。
- 应用开发配置改为 PostgreSQL 17 容器中的目标数据库。
- 先停止相关连接，再重建目标数据库并导入最终脚本；Redis 和 MinIO 数据不清空。

## 验证

1. 静态检查所有已删除模块不再被 Maven 和 Java 引用。
2. 使用 JDK 21 Maven Docker 镜像完成全量 `package`。
3. 导入 PostgreSQL 脚本时启用遇错即停。
4. 验证租户表、默认租户、租户套餐、用户/部门/岗位/角色/菜单和代码生成相关数据。
5. 验证 PostgreSQL、Redis 和 MinIO 健康状态。

## 回退

- 官方 5.X 基线可由 `origin/5.X` 恢复。
- 原 6.X 剪枝保存在 `stash@{0}`，迁移过程不修改或删除该 stash。
