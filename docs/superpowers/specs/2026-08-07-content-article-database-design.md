# 文章管理数据库设计

## 目标

在本地 RuoYi-Vue-Plus PostgreSQL 数据库中新增文章管理表结构，并初始化文章分类、文章标签字典。文章按租户隔离，标签关系由后端维护，前端通过分类 ID 和标签 ID 渲染字典名称。

## 范围

- 新增文章表 `content_article`。
- 新增文章标签关联表 `content_article_tag`。
- 在现有 `sys_dict_type`、`sys_dict_data` 中初始化文章分类和文章标签。
- 封面复用 `sys_oss`，正文图片上传 OSS 后把 URL 写入富文本 HTML。
- 不新增分类表、标签表、发布状态字典或富文本图片关联表。
- 本期不增加业务二级索引、数据库外键、标签数量检查约束或标签格式检查约束；两张新表只保留各自主键。

## 文章表

表名：`content_article`

| 字段 | PostgreSQL 类型 | 可空 | 默认值 | 说明 |
|---|---|---:|---|---|
| `article_id` | `bigint` | 否 | 雪花 ID | 文章主键 |
| `tenant_id` | `varchar(20)` | 否 | `'000000'` | 租户编号 |
| `title` | `varchar(200)` | 否 | 无 | 文章标题 |
| `summary` | `varchar(500)` | 是 | `''` | 文章简介 |
| `content` | `text` | 否 | `''` | Quill 富文本 HTML |
| `category_dict_code` | `bigint` | 否 | 无 | 分类 ID，对应 `sys_dict_data.dict_code` |
| `tag_ids` | `varchar(200)` | 否 | `''` | 逗号分隔的标签 ID，可为空，最多 10 个 |
| `cover_oss_id` | `bigint` | 是 | 无 | 封面附件 ID，对应 `sys_oss.oss_id` |
| `status` | `char(1)` | 否 | `'0'` | `0` 草稿，`1` 已发布 |
| `publish_by` | `bigint` | 是 | 无 | 发布人用户 ID |
| `publish_time` | `timestamp` | 是 | 无 | 发布时间 |
| `create_dept` | `bigint` | 是 | 无 | 创建部门，供数据权限使用 |
| `create_by` | `bigint` | 是 | 无 | 创建人 |
| `create_time` | `timestamp` | 是 | 无 | 创建时间 |
| `update_by` | `bigint` | 是 | 无 | 修改人 |
| `update_time` | `timestamp` | 是 | 无 | 修改时间 |
| `del_flag` | `char(1)` | 否 | `'0'` | `0` 正常，`1` 删除 |

文章表不保存分类名称、标签名称或封面 URL。分类和标签名称由前端加载字典后渲染；封面 URL 由 OSS 数据解析。

## 文章标签关联表

表名：`content_article_tag`

| 字段 | PostgreSQL 类型 | 可空 | 默认值 | 说明 |
|---|---|---:|---|---|
| `article_tag_id` | `bigint` | 否 | 雪花 ID | 关联记录主键 |
| `article_id` | `bigint` | 否 | 无 | 文章 ID |
| `tag_dict_code` | `bigint` | 否 | 无 | 标签 ID，对应 `sys_dict_data.dict_code` |
| `create_by` | `bigint` | 是 | 无 | 创建人 |
| `create_time` | `timestamp` | 是 | 无 | 创建时间 |

关联表不保存 `tenant_id`、`del_flag`、`update_by` 或 `update_time`。它需要加入 `tenant.excludes`。所有关联操作必须先通过文章表完成租户和数据权限校验。

## 字典类型和初始数据

字典写入默认租户 `000000`。脚本重复执行时不得产生重复的字典类型或字典数据。

### 文章分类

- 字典名称：文章分类
- 字典类型：`content_article_category`

| 排序 | `dict_label` | `dict_value` |
|---:|---|---|
| 1 | 公司新闻 | `company_news` |

### 文章标签

- 字典名称：文章标签
- 字典类型：`content_article_tag`

| 排序 | `dict_label` | `dict_value` |
|---:|---|---|
| 1 | 数字化转型 | `digital_transformation` |
| 2 | 企业战略 | `enterprise_strategy` |
| 3 | 云计算 | `cloud_computing` |
| 4 | 技术选型 | `technology_selection` |
| 5 | 版本更新 | `version_update` |
| 6 | Alba | `alba` |
| 7 | 开源 | `open_source` |
| 8 | 项目管理 | `project_management` |
| 9 | 微服务 | `microservices` |
| 10 | 架构设计 | `architecture_design` |

文章保存 `dict_code`，不保存 `dict_value`。`dict_value` 仅作为字典自身的稳定业务键值。

## 写入和读取

前端请求和响应使用 `tagIds` 数组。标签为可选项：无标签时前端传空数组 `[]`，数据库保存空字符串 `''`，关联表不保存记录。后端保存文章时对标签 ID 去重并限制最多 10 个，验证分类和非空标签属于当前租户及对应字典类型，然后在同一事务内：

1. 把标签 ID 连接为逗号字符串写入 `content_article.tag_ids`。
2. 新增或更新文章。
3. 物理删除原有标签关联。
4. 为每个标签生成 `article_tag_id` 并批量插入关联表。

文章详情和列表直接拆分主表 `tag_ids` 返回，避免额外查询。关联表用于标签引用检查、按标签反查文章和数据一致性修复。

## 删除和校验

- 文章使用 MyBatis-Plus 逻辑删除：`0` 正常、`1` 删除。
- 删除文章时逻辑删除文章并物理删除标签关联。
- 分类被正常文章引用时禁止删除。
- 标签存在关联记录时禁止删除。
- 批量删除字典时，任意字典项被引用则整批拒绝。
- 标签允许为空；非空时最多 10 个。标签去重和字典归属都在代码层校验，不建立数据库检查约束。
- 本期不支持恢复已删除文章。

## 富文本

`content text` 保存 Quill 生成的完整 HTML，包括标签、行内样式、`ql-*` 样式类和 `<img src="...">`。图片二进制存入 OSS，正文只保存图片 URL。

当前项目的 XSS 过滤器会清理 POST/PUT 中的 HTML。实现时需要将文章写接口加入 XSS 排除路径，并在业务层使用 HTML 白名单保留 Quill 所需标签、属性、样式和图片地址，禁止脚本、事件属性和危险协议。展示端需要加载 Quill CSS，并在 `ql-editor` 容器中渲染。

## 脚本执行

数据库脚本放入 `script/sql/postgres/`，包含两张表、字段注释、两个字典类型和十一条字典数据。执行前先使用事务和遇错即停模式验证脚本；执行后检查表结构、主键、默认值和字典数量。脚本不得创建业务二级索引。
