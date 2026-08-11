# 文章功能前端对接说明

本文只说明本次文章功能改造后，前端需要使用的字段、接口、限制和处理规则。

## 一、文章字段

```ts
type Id = string;

interface ArticleForm {
  /** 新增不传，修改时必传。 */
  articleId?: Id;
  title: string;
  summary?: string;
  content: string;
  /** 文章分类选项的 dictCode。 */
  categoryDictCode: Id;
  /** 标签 ID 完整数组，最多 10 个。 */
  tagIds: Id[];
  /** 图片附件 OSS ID 完整数组，最多 10 个。 */
  attachmentOssIds: Id[];
  /** 视频附件 OSS ID 完整数组，最多 5 个。 */
  videoOssIds: Id[];
  /** 0=草稿，1=已发布。 */
  status: '0' | '1';
}
```

提交示例：

```json
{
  "articleId": "208000000000000001",
  "title": "示例文章",
  "summary": "文章摘要",
  "content": "<p>文章正文</p>",
  "categoryDictCode": "208000000000000010",
  "tagIds": ["208000000000000020"],
  "attachmentOssIds": ["208000000000000101"],
  "videoOssIds": ["208000000000000201"],
  "status": "0"
}
```

字段处理要求：

- 所有 ID 都按 `string` 处理，禁止转换成 JavaScript `Number`。
- 分类提交选项中的 `dictCode`，不要提交 `dictValue`，也不要固定传 `0`。
- 封面属于普通图片附件，不再使用 `coverOssId`。
- 图片使用 `attachmentOssIds`，视频使用 `videoOssIds`，不要混用。
- 文章只保存 OSS ID，不保存上传接口返回的 URL。
- 新增和修改都提交两个数组的完整最终结果，不能只提交新增或删除的 ID。
- 修改时省略数组、传 `null` 或传 `[]`，后端都会按清空该类附件处理。
- 同一个 OSS ID 不能重复，也不能同时出现在图片和视频数组中。
- 文章列表和详情都会返回 `content`；列表页可以不展示，但前端模型需要保留该字段。

## 二、前端调用接口

### 1. 页面选项

| 功能 | 方法 | 接口 |
| --- | --- | --- |
| 获取文章分类 | GET | `/content/article/category-options` |
| 获取全部可选标签 | GET | `/content/article/tag-options` |

进入新增或编辑页面时可以并行请求这两个接口。

标签接口返回多少条，前端就展示多少条；“最多 10 个”是选择和提交数量限制，不是接口返回数量限制。

### 2. 上传图片或视频

```text
POST /resource/oss/upload
Content-Type: multipart/form-data
```

表单参数：

| 参数 | 图片 | 视频 |
| --- | --- | --- |
| `file` | 图片文件 | 视频文件 |
| `fileType` | `IMAGE` | `VIDEO` |

不需要传 `bizType`。图片和视频共用同一个上传接口，通过 `fileType` 区分技术文件类型。

上传成功后使用：

```ts
interface OssUploadResult {
  ossId: Id;
  fileName: string;
  /** 仅供本次上传后即时预览，不存入文章表单。 */
  url: string;
}
```

处理方式：

- 图片上传成功后，把 `ossId` 加入 `attachmentOssIds`。
- 视频上传成功后，把 `ossId` 加入 `videoOssIds`。
- `url` 只用于当前页面即时预览，不提交到文章接口。
- 上传未完成时禁用文章保存按钮。
- 大文件上传不要设置 30～60 秒的短超时，并提供上传进度和取消操作。

### 3. 编辑页面回显附件

文章详情只返回附件 ID。编辑页面取得详情后：

1. 合并 `attachmentOssIds` 和 `videoOssIds`。
2. 去重后只调用一次：

```text
POST /resource/oss/listByIds
```

请求体：

```json
{
  "ossIds": ["101", "102", "201"]
}
```

3. 按 `ossId` 匹配返回的文件信息和当前 URL。
4. 分别按照原始图片、视频 ID 数组恢复显示顺序。
5. 用户删除或排序后，提交两个数组的完整最终结果。

### 4. 文章管理接口

| 功能 | 方法 | 接口 |
| --- | --- | --- |
| 分页列表 | GET | `/content/article/list` |
| 文章详情 | GET | `/content/article/{articleId}` |
| 新增文章 | POST | `/content/article` |
| 修改文章 | PUT | `/content/article` |
| 修改状态 | POST | `/content/article/changeStatus` |
| 逻辑删除 | POST | `/content/article/delete` |
| 物理删除 | POST | `/content/article/physicalDelete` |

状态修改参数：

```json
{
  "articleId": "208000000000000001",
  "status": "1"
}
```

普通删除使用逻辑删除接口；回收站中的彻底删除才使用物理删除接口。

两个删除接口都使用相同的批量请求体，单选时数组中只传一个 ID：

```json
{
  "articleIds": ["208000000000000001", "208000000000000002"]
}
```

### 5. 官网公开接口

| 功能 | 方法 | 接口 |
| --- | --- | --- |
| 公开文章列表 | GET | `/content/article/public/list` |
| 公开文章详情 | GET | `/content/article/public/{articleId}` |
| 批量获取文章媒体 URL | POST | `/content/article/public/media` |

请求体可以传一个或多个文章 ID：

```json
{
  "articleIds": ["208000000000000001", "208000000000000002"]
}
```

响应按照文章分组：

```json
{
  "code": 200,
  "data": [
    {
      "articleId": "208000000000000001",
      "media": [
        {
          "ossId": "208000000000000101",
          "url": "https://current-url/...",
          "originalName": "example.png",
          "fileSuffix": "png",
          "fileSize": 102400,
          "contentType": "image/png",
          "fileType": "IMAGE"
        }
      ]
    }
  ]
}
```

- 详情页传当前一个 `articleId`。
- 列表页需要展示媒体时，一次传入当前页全部 `articleId`，不要逐篇请求。
- 前端按响应中的 `articleId` 建立 Map，再匹配文章。
- 不要在官网调用管理端的 `listByIds`，也不要长期缓存 URL。

## 三、前端限制

| 内容 | 数量限制 | 单文件大小 | 支持格式 |
| --- | ---: | ---: | --- |
| 标签 | 最多选择 10 个 | - | - |
| 图片附件 | 最多 10 张 | 50 MiB | JPG/JPEG、PNG、GIF、WebP |
| 视频附件 | 最多 5 个 | 2 GiB | MP4、MOV、AVI、WebM、MKV、WMV、FLV |

前端应在选择文件和提交表单前校验，后端仍会再次校验：

- 选择第 11 个标签时阻止并提示。
- 选择第 11 张图片时阻止并提示。
- 选择第 6 个视频时阻止并提示。
- 文件大小或格式不符合时，不发起上传。
- AVI、WMV、FLV、部分 MOV/MKV 可能无法在浏览器直接播放，可以显示文件名和下载入口。

## 四、新增和修改流程

### 新增

1. 请求分类和标签选项。
2. 分别上传图片和视频。
3. 将上传成功的 `ossId` 分别写入两个数组。
4. 等待全部上传完成。
5. 调用 `POST /content/article` 提交完整文章字段。

### 修改

1. 请求文章详情。
2. 根据两个 OSS ID 数组批量获取文件信息并回显。
3. 用户新增附件时先上传，再加入对应数组。
4. 用户删除或排序附件时只修改本地数组。
5. 调用 `PUT /content/article` 提交两个数组的完整最终结果。

## 五、鉴权提醒

除 `/content/article/public/**` 外，其余接口都需要携带登录信息：

```http
Authorization: Bearer <token>
clientid: <登录客户端 ID>
```

Swagger 或前端请求缺少这两个请求头时会返回 401。
