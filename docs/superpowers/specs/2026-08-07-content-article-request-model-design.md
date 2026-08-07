# 文章请求模型精简设计

## 背景

当前 `ContentArticleBo` 同时用于列表查询、新增和修改，并继承框架通用的 `BaseEntity`。SpringDoc 因此把 `createDept`、`createBy`、`createTime`、`updateBy`、`updateTime` 和 `params` 展示为文章请求字段；同一个 BO 上的编辑校验还导致新增接口的 OpenAPI schema 把 `articleId` 标成必填。

审计字段实际由 MyBatis Plus 的 `InjectionMetaObjectHandler` 根据当前登录用户和服务器时间自动填充，文章查询也没有使用 `BaseEntity.params`。此外，现有新增映射会复制客户端传入的 `articleId`，非空时可能绕过全局 `ASSIGN_ID` 雪花 ID 生成策略。

## 目标

- 仅调整新开发的 `ruoyi-content` 文章模块，不修改框架公共 `BaseEntity`、MyBatis 自动填充器、系统模块或代码生成器。
- 保持所有文章接口地址、HTTP 方法、正常业务字段和响应结构不变。
- 新增和修改继续共用一个 `ContentArticleBo`，避免产生过多 BO。
- 列表和导出改用一个明确的 `ContentArticleQuery` 查询对象。
- 新增接口不展示、不绑定、不使用 `articleId`。
- 所有请求模型均不暴露部门、人员、创建时间、更新时间和通用 `params`。
- 实体层继续使用 MyBatis Plus 生成雪花 ID并自动填充审计字段。

## 非目标

- 不重构框架现有模块中继承 `BaseEntity` 的 BO。
- 不改变租户隔离、发布状态策略、HTML 清洗、OSS 封面校验、标签关系、逻辑删除或事务边界。
- 不改变新增接口当前的响应结构，也不新增“返回文章 ID”的行为。
- 不修改前端页面；当前前端工程尚无文章模块实现。

## 模型设计

### `ContentArticleBo`

`ContentArticleBo` 不再继承 `BaseEntity`，只包含以下字段：

| 字段 | 新增 | 修改 | 说明 |
| --- | --- | --- | --- |
| `articleId` | 不接收 | 必填 | 仅用于修改定位文章 |
| `title` | 必填 | 必填 | 最多 200 字符并执行 XSS 校验 |
| `summary` | 可选 | 可选 | 最多 500 字符并执行 XSS 校验 |
| `content` | 必填 | 必填 | 富文本 HTML，服务层继续清洗 |
| `categoryDictCode` | 必填 | 必填 | 当前租户的分类字典编码 |
| `tagIds` | 可选 | 可选 | 最多 10 个，允许 `null` 或空数组 |
| `coverOssId` | 可选 | 可选 | 当前租户的 OSS 附件 ID |
| `status` | 必填 | 必填 | 只允许 `0` 或 `1` |

在 `ContentArticleBo` 内定义两个 Jackson 视图标记：

```java
public interface AddView {
}

public interface EditView extends AddView {
}
```

所有公共业务字段属于 `AddView`，`EditView` 通过继承同时包含公共业务字段；`articleId` 只属于 `EditView`。Controller 的 POST 请求使用 `AddView`，PUT 请求使用 `EditView`。

这样保留一个写入 BO，同时实现以下约束：

- POST 的 OpenAPI schema 不出现 `articleId`。
- POST JSON 即使包含 `articleId`，Jackson 也不会把它绑定到 BO。
- PUT 可以绑定 `articleId`，并继续通过 `EditGroup` 校验必填。
- 因为 BO 不再继承 `BaseEntity`，审计字段和 `params` 不属于请求类型。

### `ContentArticleQuery`

`ContentArticleQuery` 是普通查询条件对象，不继承 `BaseEntity`，只包含：

- `title`
- `categoryDictCode`
- `status`

文章列表和导出接口共用该对象。分页字段仍由现有 `PageQuery` 提供；当前未使用的日期范围和 `params` 不加入查询模型。如果后续增加日期筛选，应新增命名明确的查询字段，而不是恢复通用 `params`。

### Entity 与 VO

- `ContentArticle` 保持继承 `TenantEntity`，继续拥有租户字段和审计字段。
- `ContentArticleVo` 保持现状，仍可向前端返回 `createTime`、`updateTime` 等只读结果字段。
- 请求模型不接收审计字段，不影响响应模型展示这些字段。

## Controller 与 Service 数据流

### 新增

```text
POST /content/article
  -> AddView 反序列化 ContentArticleBo
  -> AddGroup 参数校验
  -> Service 仅复制文章业务字段
  -> 新实体 articleId 保持 null
  -> MyBatis Plus ASSIGN_ID 生成雪花 ID
  -> MetaObjectHandler 填充部门、创建人、创建时间、更新人、更新时间
```

服务层的公共实体转换不再复制 `articleId`。新增路径因此不依赖前端行为，即便将来 Controller 绑定规则发生变化，也不能通过 BO 指定新增 ID。

### 修改

```text
PUT /content/article
  -> EditView 反序列化 ContentArticleBo
  -> EditGroup 校验 articleId 和业务字段
  -> 查询现有文章并校验租户权限
  -> Service 显式把 articleId 设置到更新实体
  -> MetaObjectHandler 覆盖更新人和更新时间
```

### 查询与导出

```text
GET /content/article/list 或 POST /content/article/export
  -> ContentArticleQuery
  -> 仅按 title、categoryDictCode、status 构造查询条件
```

## 错误处理与兼容性

- 现有字段校验消息、字典校验、OSS 校验和文章不存在提示保持不变。
- 新增请求缺少必填业务字段时仍返回现有参数校验错误。
- 修改请求缺少 `articleId` 时仍返回“文章ID不能为空”。
- 已按接口文档提交正常业务字段的前端无需修改请求结构。
- 错误地向新增接口提交 `articleId`、审计字段或 `params` 时，这些值不会进入文章实体，也不会影响雪花 ID 和审计字段。
- OpenAPI 文档必须分别准确表达 POST、PUT 和查询接口的字段集合。

## 测试与验收

### 单元测试

- 使用 Jackson `AddView` 反序列化包含 `articleId` 的新增 JSON，断言 BO 的 `articleId` 为 `null`。
- 使用 `EditView` 反序列化修改 JSON，断言 `articleId` 正常绑定。
- 验证新增和修改的原有字段校验规则保持有效。
- 捕获新增时传给 Mapper 的 `ContentArticle`，断言插入前 `articleId` 为 `null`。
- 验证修改时 Mapper 收到正确的 `articleId`。
- 更新 Controller 和 Service 契约测试，确认方法参数使用新的查询类型和正确的 Jackson 视图。

### OpenAPI 运行时验收

- `POST /content/article` 请求 schema 不包含 `articleId`、`createDept`、`createBy`、`createTime`、`updateBy`、`updateTime`、`params`。
- `PUT /content/article` 请求 schema 包含且要求 `articleId`，但不包含任何审计字段或 `params`。
- 列表与导出只展示明确的文章筛选字段；分页字段仍正常展示。
- Swagger UI 可以正常加载，OpenAPI JSON 可以正常生成。

### 回归验收

- 执行 `ruoyi-content` 测试并显式关闭项目默认的跳过测试配置。
- 执行 `ruoyi-admin` 及依赖模块的 Maven 构建。
- 临时端口启动打包产物，核对 Swagger schema 和文章接口路径。

## 文件范围

计划新增或修改的文件限定为：

- `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/bo/ContentArticleBo.java`
- `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/bo/ContentArticleQuery.java`
- `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/controller/ContentArticleController.java`
- `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/IContentArticleService.java`
- `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/impl/ContentArticleServiceImpl.java`
- 对应的文章模块测试文件
- `docs/content-article-frontend-api.md`

框架公共代码和其他业务模块不在本次修改范围内。
