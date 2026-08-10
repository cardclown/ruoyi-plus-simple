# 公开文章列表与详情接口设计

## 背景

后台现有文章列表和详情接口分别受 `content:article:list`、`content:article:query` 权限控制，能够查看当前用户数据权限范围内的草稿和已发布文章，不能直接作为官网匿名接口开放。

官网只服务于“中安建设”这一单位。数据库中有效租户编号为 `140872`；同名租户 `420542` 的 `del_flag` 为 `1`，属于已删除旧记录，不得用于公开查询。

## 目标

- 新增两个无需登录和权限校验的 GET 接口：
  - `GET /content/article/public/list`
  - `GET /content/article/public/{articleId}`
- 公开接口只返回租户 `140872` 下未删除且已发布的文章。
- 草稿、已删除、其他租户或不存在的文章在详情接口中统一视为不存在。
- 调用方不能传入或覆盖租户编号。
- 保留一层不绑定具体单位的通用已发布文章查询能力，方便后续为其他单位增加独立门面。
- 现有后台管理列表和详情接口保持原路径、鉴权和数据权限行为不变。

## 非目标

- 不修改文章发布、撤回和普通编辑状态机。
- 不开放新增、修改、删除、导出、分类选项或标签选项接口。
- 不允许匿名调用方指定 `tenantId`、部门 ID 或其他组织信息。
- 不在本次改动中增加阅读量、缓存、推荐、上下篇或附件 URL 聚合。

## 方案比较

### 方案一：公开 Controller、单位门面、通用查询三层分离（采用）

公开 Controller 只负责 HTTP 契约；中安建设门面固定租户 `140872`；通用查询服务接收门面内部传入的租户编号并执行已发布文章查询。

优点是匿名边界、单位绑定和查询逻辑职责清晰，其他单位可以复用通用查询服务，同时外部请求无法篡改租户编号。

### 方案二：在公开 Controller 中直接写死租户并查询

代码量较少，但 HTTP 层同时承担单位绑定和数据访问规则，后续扩展其他单位时容易复制查询逻辑。

### 方案三：取消现有后台接口鉴权或允许前端传租户编号

实现最简单，但可能暴露草稿、后台审计字段或其他租户数据，不满足安全要求，因此不采用。

## HTTP 接口

### 公开文章列表

```text
GET /content/article/public/list
```

接口使用 `@SaIgnore`，保留现有分页参数，并复用 `ContentArticleQuery` 中的标题和分类筛选条件。请求中的 `status` 即使存在也不参与查询，服务端始终固定查询已发布状态 `1`。

返回 `TableDataInfo<ContentArticlePublicVo>`。列表按文章 ID 倒序排列，不查询正文，以避免列表请求加载大字段。

### 公开文章详情

```text
GET /content/article/public/{articleId}
```

接口使用 `@SaIgnore`，文章 ID 必填。只有同时满足以下条件的文章才返回详情：

- `tenant_id = '140872'`
- `status = '1'`
- `del_flag = '0'`

任一条件不满足时抛出 `ServiceException("文章不存在")`。

## 分层与职责

### `ContentArticlePublicController`

- 类级别标注 `@SaIgnore`。
- 映射 `/content/article/public`。
- 暴露列表和详情两个只读 GET 接口。
- 不接收租户编号或其他组织参数。
- 只依赖公开文章门面接口，不直接依赖 Mapper 或通用查询服务。

### `IContentArticlePublicFacade`

定义 Controller 可调用的两个方法，方法签名不包含租户编号：

```java
TableDataInfo<ContentArticlePublicVo> queryPage(ContentArticleQuery query, PageQuery pageQuery);

ContentArticlePublicVo queryById(Long articleId);
```

### `ZhongAnArticlePublicFacade`

- 实现 `IContentArticlePublicFacade`。
- 内部常量固定为 `140872`。
- 将固定租户编号和调用参数转交给通用查询服务。
- 不从请求、请求头或登录上下文读取租户编号。

### `ContentArticlePublishedQueryService`

提供不绑定具体单位的内部查询能力：

```java
TableDataInfo<ContentArticlePublicVo> queryPage(
    String tenantId, ContentArticleQuery query, PageQuery pageQuery);

ContentArticlePublicVo queryById(String tenantId, Long articleId);
```

- 校验内部传入的租户编号非空。
- 只使用标题和分类筛选条件，状态始终固定为 `1`。
- 在租户动态上下文中执行查询，并在查询条件中再次显式限定 `tenant_id`，形成双重租户边界。
- 将实体转换为公开响应对象。
- 详情未命中时统一抛出“文章不存在”。

### `ContentArticleMapper`

增加公开查询专用方法，方法不标注 `@DataPermission`，避免匿名请求尝试读取登录用户、角色和部门数据权限。

公开 Mapper 方法必须显式包含租户、已发布和未删除条件。后台管理 Mapper 方法继续保留原有 `@DataPermission`，两条查询链路互不替代。

## 公开响应对象

新增单一 `ContentArticlePublicVo`，只暴露官网展示需要的字段：

- `articleId`
- `title`
- `summary`
- `content`
- `categoryDictCode`
- `tagIds`
- `coverOssId`
- `publishTime`

不暴露 `tenantId`、`status`、`publishBy`、创建人、更新人和其他后台审计字段。列表查询不加载正文，因此列表中的 `content` 为空；详情查询返回正文。

## 数据流

列表请求的数据流如下：

```text
匿名请求
  -> ContentArticlePublicController
  -> IContentArticlePublicFacade
  -> ZhongAnArticlePublicFacade（固定 tenantId=140872）
  -> ContentArticlePublishedQueryService
  -> ContentArticleMapper（tenant_id + status=1 + del_flag=0）
  -> ContentArticlePublicVo
```

详情请求使用同一条调用链，只额外限定文章 ID，并在未命中时返回统一业务异常。

## 安全边界

- `@SaIgnore` 只应用于新增的公开 Controller，不应用于现有后台 Controller。
- 租户编号只存在于中安建设门面内部，HTTP 请求无法提供或覆盖。
- 匿名查询不使用用户级 `@DataPermission`，但必须保留租户、发布状态和逻辑删除过滤。
- 动态租户上下文必须通过 `TenantHelper.dynamic` 的回调形式使用，确保请求结束后清理线程变量。
- Mapper 同时显式限定租户编号，即使租户插件被关闭也不会跨租户读取。

## 错误处理

- 详情文章不存在、属于其他租户、处于草稿或已删除：`文章不存在`。
- 文章 ID 为空：沿用参数校验错误 `文章ID不能为空`。
- 内部通用查询收到空租户编号：抛出明确的内部参数异常，不执行数据库查询。

## 测试策略

- Controller 契约测试：验证两个 GET 路径、类级 `@SaIgnore`、参数模型和不存在的权限注解。
- 门面测试：验证列表和详情始终向通用查询服务传入精确租户编号 `140872`。
- 通用服务测试：验证列表状态固定为 `1`，请求状态不能覆盖；详情未命中时返回“文章不存在”；实体只映射公开字段。
- Mapper 契约或数据库行为测试：验证公开查询同时限定租户、发布状态和逻辑删除标志，并且公开方法没有 `@DataPermission`。
- 回归测试：验证原后台列表和详情接口仍保留原权限注解。
- 运行 `ruoyi-content` 模块完整测试，确保文章发布、撤回、编辑、标签和字典逻辑不受影响。

## 交付与版本控制

- 所有提交写入 `company-website-backend`，不提交到 `5.X` 基准分支。
- 提交信息使用中文。
- 当前工作区若存在其他并发修改，提交时必须使用精确路径，不能使用 `git add .`。
