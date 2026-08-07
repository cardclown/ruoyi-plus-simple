# Swagger UI 集成设计

## 目标

在保留现有 SpringDoc OpenAPI JSON 接口的基础上，为本地 RuoYi-Vue-Plus 后端增加原生 Swagger UI 页面，方便开发人员查看和调试包括文章管理在内的后端接口。

## 当前状态

- 项目使用 SpringDoc `2.8.17`。
- `ruoyi-common-doc` 当前依赖 `springdoc-openapi-starter-webmvc-api`。
- `GET /v3/api-docs` 可正常返回 OpenAPI 文档，并且已经包含 `/content/article` 相关接口。
- `GET /swagger-ui/index.html` 当前返回业务 404，因为项目没有引入 Swagger UI 静态资源。

## 方案

采用 SpringDoc 原生 Swagger UI，不引入 Knife4j：

1. 在根 `pom.xml` 的依赖版本管理中，将 `springdoc-openapi-starter-webmvc-api` 替换为 `springdoc-openapi-starter-webmvc-ui`。
2. 在 `ruoyi-common/ruoyi-common-doc/pom.xml` 中同步替换依赖。
3. 保持 SpringDoc 版本 `2.8.17` 和现有 `application.yml` 配置不变。
4. 不修改业务 Controller，也不改变 `/v3/api-docs` 的现有契约。

`springdoc-openapi-starter-webmvc-ui` 会传递引入 API 生成能力，因此替换后仍保留 `/v3/api-docs`，同时增加 Swagger UI 静态页面。

## 访问地址

服务重启后：

- OpenAPI JSON：`http://127.0.0.1:8080/v3/api-docs`
- Swagger UI：`http://127.0.0.1:8080/swagger-ui/index.html`

Swagger UI 中的登录鉴权仍遵循现有 Sa-Token 规则。调试受保护接口时，需要在请求中携带 `Authorization: Bearer <token>` 和 `clientid`。

## 验收标准

1. 改动前访问 `/swagger-ui/index.html` 返回业务 404，证明测试能够捕获缺失功能。
2. Maven 能成功解析并构建 `ruoyi-common-doc`、`ruoyi-admin` 及其依赖模块。
3. 服务使用新依赖重启后，`/swagger-ui/index.html` 返回 HTML 页面而不是业务 404。
4. `/v3/api-docs` 继续返回有效 OpenAPI JSON。
5. OpenAPI 文档继续包含文章管理接口路径。

## 非目标

- 不引入 Knife4j。
- 不调整文章接口业务逻辑。
- 不修改接口鉴权和 API 加密规则。
- 不为现有 Controller 额外补充 Swagger 注解。
