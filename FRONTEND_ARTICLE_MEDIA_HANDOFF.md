# 前端开发交付单：文章图片、视频与附件

> 这份文档可以直接交给前端开发。请按“必须完成清单”和“开发顺序”实施，不需要再从后端代码推断接口。

## 一、前端必须完成的功能

- [ ] 新增、编辑页面加载文章分类和全部可选标签。
- [ ] 分类提交 `dictCode`，不能提交 `dictValue`，更不能固定传 `0`。
- [ ] 标签接口返回多少就展示多少，但最多选择和提交 10 个。
- [ ] 图片和视频使用两个独立上传区域。
- [ ] 图片上传传 `fileType=IMAGE`，视频上传传 `fileType=VIDEO`。
- [ ] 图片最多 10 张、单张最多 50 MiB；视频最多 5 个、单个最多 2 GiB。
- [ ] 文章只提交 `attachmentOssIds` 和 `videoOssIds`，不要提交 `coverOssId`、URL 或文件对象。
- [ ] 编辑时提交两个 ID 数组的完整快照；省略或传 `null` 会被当作空列表处理。
- [ ] 管理端编辑回显时合并两个数组，只调用一次 `listByIds` 获取当前 URL。
- [ ] 匿名官网使用文章专属媒体接口获取当前 URL，禁止调用通用 OSS 查询接口。
- [ ] 文章列表响应包含 `content`，是否展示由前端决定。
- [ ] 上传过程中显示进度、支持取消，并在所有上传完成前禁用文章提交。
- [ ] 普通删除走逻辑删除；只有回收站“彻底删除”才调用物理删除接口。

## 二、最重要的接口契约

### 1. ID 一律使用字符串

`articleId`、`ossId`、`dictCode` 都是雪花 ID。前端必须按 `string` 保存，禁止转换为 JavaScript `Number`。

```ts
export type Id = string;

export interface ApiResult<T> {
  code: number;
  msg: string;
  data: T;
}

export interface PageResult<T> {
  code: number;
  msg: string;
  rows: T[];
  total: number;
}
```

### 2. 文章表单类型

```ts
export interface ArticleForm {
  /** 新增不传，修改必传。 */
  articleId?: Id;
  title: string;
  summary?: string;
  content: string;
  /** 必须使用分类选项的 dictCode。 */
  categoryDictCode: Id;
  /** 完整快照，最多 10 个。 */
  tagIds: Id[];
  /** 图片 OSS ID 完整快照，最多 10 个。 */
  attachmentOssIds: Id[];
  /** 视频 OSS ID 完整快照，最多 5 个。 */
  videoOssIds: Id[];
  /** "0"=草稿，"1"=已发布。 */
  status: '0' | '1';
}
```

最终提交示例：

```json
{
  "articleId": "208000000000000001",
  "title": "示例文章",
  "summary": "简介",
  "content": "<p>正文</p>",
  "categoryDictCode": "208000000000000010",
  "tagIds": ["208000000000000020"],
  "attachmentOssIds": ["208000000000000101", "208000000000000102"],
  "videoOssIds": ["208000000000000201"],
  "status": "0"
}
```

不要出现以下字段：

```json
{
  "coverOssId": "禁止提交",
  "videoOssId": "禁止提交",
  "attachmentUrls": "禁止持久化 URL"
}
```

## 三、鉴权与 401

除 `/content/article/public/**` 外，其余接口均需登录，并同时携带：

```http
Authorization: Bearer <token>
clientid: <登录时使用的客户端 ID>
```

此前 Swagger 生成的 curl 没有这两个请求头，所以返回 401。`clientid` 还必须与 token 对应的客户端一致。

管理端所需权限：

| 功能 | 权限 |
| --- | --- |
| 文章列表 | `content:article:list` |
| 文章详情 | `content:article:query` |
| 新增 | `content:article:add` |
| 编辑、状态切换 | `content:article:edit` |
| 逻辑删除、物理删除 | `content:article:remove` |
| 上传 | `system:oss:upload` |
| 管理端查询媒体 URL | `system:oss:query` |
| 管理端下载 | `system:oss:download` |

## 四、页面初始化：分类和标签

进入新增或编辑页面时并行调用：

```text
GET /content/article/category-options
GET /content/article/tag-options
```

选项字段：

```ts
export interface ArticleOption {
  dictCode: Id;
  dictSort: number;
  dictLabel: string;
  dictValue: string;
  cssClass?: string;
  listClass?: string;
  isDefault?: string;
}
```

注意事项：

- 分类下拉框的 `value` 必须绑定 `dictCode`。
- `categoryDictCode: 0` 会触发“文章分类不存在或不属于当前租户”。
- 标签接口返回当前租户的全部标签，不能在接口层只取 10 条。
- UI 在选择第 11 个标签时应立即提示；后端提交时仍会二次校验。

## 五、图片和视频上传

### 1. 唯一上传接口

```text
POST /resource/oss/upload
Content-Type: multipart/form-data
```

表单字段：

| 字段 | 必填 | 值 |
| --- | --- | --- |
| `file` | 是 | 浏览器文件对象 |
| `fileType` | 文章媒体必填 | 图片传 `IMAGE`，视频传 `VIDEO` |

`fileType` 是技术文件分类，不是业务 `bizType`。不要传 `article_attachment`、`article_video` 或其他 `bizType`。

上传响应：

```ts
export interface OssUploadResult {
  ossId: Id;
  fileName: string;
  /** 只用于刚上传后的即时预览，不写入文章表单。 */
  url: string;
}
```

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "ossId": "208000000000000101",
    "fileName": "example.png",
    "url": "https://current-preview-url/..."
  }
}
```

### 2. Axios 示例

```ts
export async function uploadArticleMedia(
  file: File,
  fileType: 'IMAGE' | 'VIDEO',
  onProgress?: (percent: number) => void,
  signal?: AbortSignal
) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('fileType', fileType);

  return request<ApiResult<OssUploadResult>>({
    url: '/resource/oss/upload',
    method: 'post',
    data: formData,
    // 不要手工设置 multipart boundary。
    timeout: 0,
    signal,
    onUploadProgress(event) {
      if (!event.total) return;
      onProgress?.(Math.round((event.loaded * 100) / event.total));
    }
  });
}
```

如果项目请求拦截器会阻止短时间内的重复提交，应对上传请求关闭该拦截，但不要关闭文章保存接口的重复提交保护。

### 3. 前端先校验，后端仍会二次校验

| 类型 | 数量 | 单文件大小 | 格式 |
| --- | ---: | ---: | --- |
| 图片 | 最多 10 张 | 50 MiB | JPG/JPEG、PNG、GIF、WebP |
| 视频 | 最多 5 个 | 2 GiB | MP4、MOV、AVI、WebM、MKV、WMV、FLV |

后端会同时检查扩展名、MIME 和文件签名，仅修改后缀无法绕过校验。

建议：

- 图片可并发 2～3 个上传。
- 大视频建议同时只上传 1 个，避免浏览器、带宽和代理临时空间被占满。
- 用户取消未完成上传时使用 `AbortController`。
- 文件已经上传成功但最终没有保存文章，会保持临时状态，并在超过 24 小时后由后端清理。

## 六、新增文章的调用顺序

1. 加载分类和全部标签。
2. 用户上传图片；每成功一个，把 `data.ossId` 加入 `attachmentOssIds`。
3. 用户上传视频；每成功一个，把 `data.ossId` 加入 `videoOssIds`。
4. 上传过程中禁用文章提交按钮。
5. 所有上传完成后，提交文章表单的完整 ID 数组。
6. 调用 `POST /content/article`。
7. 保存失败时直接展示后端 `msg`，不要把失败当成上传成功。

## 七、编辑文章的调用顺序

1. 调用 `GET /content/article/{articleId}`。
2. 保存响应中的 `attachmentOssIds` 和 `videoOssIds` 原始顺序。
3. 合并两个数组并去重。
4. 只调用一次：

```text
GET /resource/oss/listByIds/{ossIds}
```

多个 ID 使用逗号分隔，例如：

```text
GET /resource/oss/listByIds/101,102,201
```

5. 将返回数据按 `ossId` 建立 Map，再分别按照两个原始 ID 数组恢复图片和视频显示顺序。
6. 用户删除或排序后，更新本地两个 ID 数组。
7. 调用 `PUT /content/article`，必须提交两个数组的完整最终快照。

特别注意：

- `attachmentOssIds` 或 `videoOssIds` 省略、传 `null`、传 `[]`，都会表示该类型最终没有附件。
- 不能只提交“新增的 ID”或“被删除的 ID”。
- 同一个 OSS ID 不能重复，也不能同时放入图片和视频数组。
- 文件已经绑定其他文章时，后端会拒绝保存。

## 八、管理端文章接口

| 功能 | 方法 | 路径 |
| --- | --- | --- |
| 列表 | GET | `/content/article/list` |
| 详情 | GET | `/content/article/{articleId}` |
| 新增 | POST | `/content/article` |
| 修改 | PUT | `/content/article` |
| 修改状态 | POST | `/content/article/changeStatus` |
| 逻辑删除 | DELETE | `/content/article/{articleIds}` |
| 物理删除 | DELETE | `/content/article/physical/{articleIds}` |

列表查询示例：

```text
GET /content/article/list?pageNum=1&pageSize=10&title=&categoryDictCode=&status=
```

列表和详情都返回 `content`。列表页面可以不渲染正文，但不要假设响应中的正文为空。

状态切换：

```json
{
  "articleId": "208000000000000001",
  "status": "1"
}
```

## 九、匿名官网文章与媒体

公开接口无需登录：

```text
GET /content/article/public/list?pageNum=1&pageSize=10&title=&categoryDictCode=
GET /content/article/public/{articleId}
GET /content/article/public/{articleId}/media
```

公开列表和详情只返回已发布、未删除、属于固定租户的文章。

文章响应仍然只包含 `attachmentOssIds` 和 `videoOssIds`。需要展示时调用文章专属媒体接口：

```ts
export interface PublicArticleMedia {
  ossId: Id;
  /** 每次请求生成或刷新，不能永久保存。 */
  url: string;
  originalName: string;
  fileSuffix: string;
  fileSize: number;
  contentType: string;
  fileType: 'IMAGE' | 'VIDEO';
}
```

```text
GET /content/article/public/208000000000000001/media
```

该接口只返回与这篇已发布文章有关联的媒体，不能传任意 OSS ID。草稿、已删除、其他租户或不存在的文章统一按文章不存在处理。

公开页面不要调用：

```text
GET /resource/oss/listByIds/{ossIds}
GET /resource/oss/download/{ossId}
```

这两个接口属于管理端权限。匿名页面预览或下载使用 `/media` 返回的当前 URL。

## 十、预览规则

- 图片：直接使用当前 URL 展示。
- MP4、WebM：浏览器支持时使用 `<video controls>`。
- AVI、WMV、FLV、部分 MOV/MKV：浏览器可能不支持解码，应展示文件名、大小和“打开/下载”按钮。
- 不要根据后缀判断安全性；后端已经做真实格式校验，前端只负责选择展示方式。
- URL 可能是动态或签名地址，页面重新打开时必须重新解析。

## 十一、删除行为

### 逻辑删除

```text
DELETE /content/article/{articleIds}
```

用于普通删除。文章媒体和关联仍保留，可以支持回收站场景。

### 物理删除

```text
DELETE /content/article/physical/{articleIds}
```

只允许对已经逻辑删除且当前用户有数据权限的文章执行。

业务事务提交后，后端才删除 MinIO 对象和 OSS 元数据。对象存储暂时失败时，文章删除仍已完成，后台会保留待删状态并自动重试；待删媒体不能再次绑定或查询。前端收到错误时应展示后端消息，不要自行推断数据库状态。

## 十二、常见错误处理

| 后端现象/消息 | 前端检查 |
| --- | --- |
| 401“认证失败” | 是否同时携带 `Authorization` 和正确的 `clientid` |
| “文章分类不存在或不属于当前租户” | 是否把分类的 `dictCode` 提交成了字符串 ID，而不是 `dictValue` 或 `0` |
| “文章标签最多选择10个” | 标签选择是否超过 10 个 |
| “文章图片最多上传10张” | 图片 ID 数量是否超过 10 |
| “文章视频最多上传5个” | 视频 ID 数量是否超过 5 |
| 类型或格式错误 | `fileType` 是否传对，文件后缀/MIME/真实内容是否一致 |
| “附件已绑定其他文章” | 是否复用了另一篇文章的 OSS ID |
| 上传超时 | 前端是否仍使用默认短超时，代理是否配置 2 GiB 与长超时 |

所有业务错误优先展示后端返回的 `msg`，不要统一替换成“操作失败”。

## 十三、前端验收清单

- [ ] Swagger/浏览器请求包含 `Authorization` 和 `clientid`，不再返回 401。
- [ ] 分类提交真实 `dictCode`，新增文章不再报分类不存在。
- [ ] 标签列表展示全部选项，第 11 个选择被 UI 阻止。
- [ ] 图片和视频分别传 `IMAGE`、`VIDEO`。
- [ ] 50 MiB 图片边界、2 GiB 视频配置得到正确提示。
- [ ] 编辑页面只调用一次管理端 `listByIds`。
- [ ] 编辑提交完整图片/视频 ID 数组，删除和排序正确保存。
- [ ] 管理列表响应中的 `content` 可读取。
- [ ] 匿名详情只调用文章专属 `/media` 接口获取 URL。
- [ ] MP4/WebM 可预览，不支持的格式展示下载入口。
- [ ] 逻辑删除不清媒体，物理删除入口只出现在回收站。

## 十四、部署相关前端注意事项

后端已配置：

- 单文件上限 `2 GB`；
- 请求上限 `2200 MB`；
- Nginx `client_max_body_size 2200m`；
- `proxy_request_buffering off`；
- 上传读写超时 `86400s`。

非仓库自带的生产代理也必须配置同等限制。前端上传请求不要使用默认 30～60 秒超时，大视频建议允许用户主动取消。
