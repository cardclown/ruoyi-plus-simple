# PostgreSQL 生产初始化顺序

生产服务器不使用 Docker。基础脚本保留本地开发环境默认值，生产端口、域名和中安建设租户通过独立脚本覆盖，避免本地开发环境被生产地址污染。

按以下顺序执行：

1. `postgres_ry_vue_5.X.sql`：创建 RuoYi 基础表和默认租户。
2. `content_article.sql`：创建文章表和默认文章字典。
3. `content_article_attachment_migration.sql`：安装附件关系表，并兼容迁移旧封面字段。
4. `content_article_menu.sql`：安装文章菜单和按钮权限。
5. `zajs_tenant_seed.sql`：安装中安建设租户、套餐、管理员、角色及其授权。
6. `production_environment.sql`：把 Java 内部 MinIO 地址与浏览器外部访问域名分开。
7. `sys_oss_object_key_migration.sql`：仅在迁移已有 OSS 记录时执行；全新空库无需执行。

所有增量脚本均应使用 `psql -v ON_ERROR_STOP=1` 执行。直接修改 `sys_oss_config` 后需要重启 Java 服务，让 OSS 客户端缓存重新加载。

`zajs_tenant_seed.sql` 只包含可重建系统配置，不包含文章正文、OSS 文件记录、登录信息、操作日志或已删除的历史租户。
