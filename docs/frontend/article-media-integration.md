# 文章图片/视频前端对接

> 以下 ID（`articleId`、字典 `dictCode`、`ossId`）均为雪花 ID，前端按字符串保存，不要转为 JavaScript `Number`。普通响应为 `{ code, msg, data }`，分页响应为 `{ code, msg, rows, total }`。

## 鉴权与权限

除 `/content/article/public/**` 外，下列接口均需已登录。请求同时带上登录时对应的两个请求头；先前 Swagger 生成的失败 curl 没有 `Authorization` 和 `clientid`，因此会返回 401。

```bash
curl -H "Authorization: Bearer <token>" \
     -H "clientid: <登录使用的客户端ID>" \
     'https://<host>/content/article/list?pageNum=1&pageSize=10'
```

`clientid` 必须与 token 中的客户端一致。给操作员角色分配所需的 `content:article:*` 权限（见下表），以及媒体操作需要的 `system:oss:upload`、`system:oss:query`、`system:oss:download`；若在 UI 中提供 OSS 删除，再分配 `system:oss:remove`。

| 功能 | 方法与路径 | 权限 |
| --- | --- | --- |
| 分类/标签选项 | `GET /content/article/category-options`、`/tag-options` | `content:article:list`、`query`、`add`、`edit` 任一即可 |
| 管理列表/详情 | `GET /content/article/list`、`GET /content/article/{articleId}` | `content:article:list`、`content:article:query` |
| 新增/编辑/改状态 | `POST /content/article`、`PUT /content/article`、`POST /content/article/changeStatus` | `content:article:add`、`content:article:edit` |
| 逻辑/物理删除 | `DELETE /content/article/{articleIds}`、`DELETE /content/article/physical/{articleIds}` | `content:article:remove` |
| 管理端上传/查 URL/下载 | `POST /resource/oss/upload`、`GET /resource/oss/listByIds/{ossIds}`、`GET /resource/oss/download/{ossId}` | `system:oss:upload`、`query`、`download` |

## 媒体上传与回显

通用上传接口为 `POST /resource/oss/upload`（`multipart/form-data`）：`file` 必填；`fileType` 可选，文章图片传 `IMAGE`、视频传 `VIDEO`。文章媒体务必传入 `fileType`，否则后端不会按文章媒体技术类型分类。成功返回的确切字段为 `data.url`、`data.fileName`、`data.ossId`：保存文章的是 `ossId`，URL 仅用于即时预览。

```bash
curl -X POST 'https://<host>/resource/oss/upload' \
  -H 'Authorization: Bearer <token>' -H 'clientid: <clientId>' \
  -F 'file=@./cover.png;type=image/png' -F 'fileType=IMAGE'
```

```json
{"code":200,"msg":"操作成功","data":{"url":"https://<oss-url>/...","fileName":"cover.png","ossId":"208..."}}
```

- 图片：最多 10 张/篇，单张不超过 50 MB；仅 `jpg/jpeg`、`png`、`gif`、`webp`，且扩展名、MIME、文件签名必须匹配。
- 视频：最多 5 个/篇，单个不超过 2 GB；仅 `mp4`、`mov`、`avi`、`webm`、`mkv`、`wmv`、`flv`，同样校验扩展名、MIME、文件签名。
- 超量、重复 ID、同一 ID 同时作图片和视频、类型不匹配、附件不存在/已绑定其他文章都会被后端拒绝；失败应展示后端 `msg`。上传组件设置进度回调和足够长的超时，不要把 `Content-Type` 手动写成无 boundary 的 multipart 值。

文章接口只持久化 `attachmentOssIds` 和 `videoOssIds`，管理端和匿名文章列表/详情返回也只有这些 ID 数组，URL 不应持久化。

- 已登录管理端需要展示或下载时，继续使用有 `system:oss:query` / `system:oss:download` 权限的 `GET /resource/oss/listByIds/{ossIds}` 与 `GET /resource/oss/download/{ossId}`。
- 匿名官网不得调用通用 OSS 查询。统一请求 `GET /content/article/public/{articleId}/media`；该接口无需登录，只在固定租户内接受已发布、未删除的文章，并且只返回当前仍与该文章有关联的 OSS 元数据。草稿、已删除、其他租户或不存在的文章统一返回“文章不存在”，任意 OSS ID 不能作为请求参数。

匿名媒体解析响应为 `{ code, msg, data }`，`data` 是文章全部图片和视频的展示顺序列表。每项确切字段为 `ossId`、`url`、`originalName`、`fileSuffix`、`fileSize`、`contentType`、`fileType`；`fileType` 为 `IMAGE` 或 `VIDEO`。响应不含对象存储内部键、服务商、扩展 JSON 或业务绑定字段。每次展示都应重新解析以获得当前 URL：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": [
    {
      "ossId": "208...",
      "url": "https://<current-url>/...",
      "originalName": "cover.png",
      "fileSuffix": ".png",
      "fileSize": 123456,
      "contentType": "image/png",
      "fileType": "IMAGE"
    }
  ]
}
```

## 文章接口

管理端：`GET /content/article/list?pageNum=1&pageSize=10&title=&categoryDictCode=&status=`，详情为 `GET /content/article/{articleId}`。列表和详情均包含 `content`；即使列表 UI 不渲染正文，响应不会刻意置空。

匿名官网：`GET /content/article/public/list?pageNum=1&pageSize=10&title=&categoryDictCode=`、`GET /content/article/public/{articleId}` 与 `GET /content/article/public/{articleId}/media`，仅返回已发布文章或其关联媒体；传入的 `status` 不参与公开列表过滤。公开接口不需鉴权。

新增 `POST /content/article`、修改 `PUT /content/article`；修改额外必传 `articleId`。`title`（≤200）、`content`、`categoryDictCode`、`status`（`"0"` 草稿/`"1"` 发布）必填；`summary` ≤500。最终媒体 JSON 契约如下：

```json
{
  "articleId":"208...",
  "title":"示例文章",
  "summary":"简介",
  "content":"<p>正文</p>",
  "categoryDictCode":"208...",
  "tagIds":["208..."],
  "attachmentOssIds":["208...","208..."],
  "videoOssIds":["208..."],
  "status":"0"
}
```

`tag-options` 返回当前租户的全部标签，UI 仍须最多选/提交 10 个，后端也会以“文章标签最多选择10个”拒绝超限。分类使用 `GET /content/article/category-options`；两类选项的元素字段为 `dictCode`、`dictSort`、`dictLabel`、`dictValue`、`cssClass`、`listClass`、`isDefault`。

状态切换使用 `POST /content/article/changeStatus`：

```json
{"articleId":"208...","status":"1"}
```

删除 `DELETE /content/article/{articleIds}`（多个 ID 逗号分隔）仅逻辑删除，媒体不会随之物理删除。清理已逻辑删除的文章才调用 `DELETE /content/article/physical/{articleIds}`：事务内会删除附件关联、标签关联和文章记录，并把该文章精确拥有的 OSS 元数据标为待删除；只有业务事务提交后才删除对象存储文件和 `sys_oss` 记录。若对象存储暂时失败，文章删除仍已提交，待删除媒体不会再被绑定或查询，后台会分批重试；事务回滚则不会删除对象。未逻辑删除或无数据权限仍会失败。

## 部署提示

视频上传的反向代理和应用服务器须允许至少 2 GB 的单文件及请求余量，并避免大请求整包缓冲；仓库 Undertow、Spring multipart 和 Docker Nginx 的请求上限均对齐为 `2200 MB`（单文件仍为 `2 GB`），Nginx 另设置 `proxy_read_timeout 86400s`、`proxy_send_timeout 86400s` 和 `proxy_request_buffering off`。非 Docker/生产代理应对齐这些关键配置；前端上传超时也应与之匹配。
