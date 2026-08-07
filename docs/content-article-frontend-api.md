# 文章管理前端接口文档

## 1. 通用约定

- 文章接口前缀：`/content/article`
- 雪花 ID 均按字符串处理，包括 `articleId`、`dictCode`、`tagIds`、`coverOssId`，不要转换为 JavaScript `Number`。
- 时间格式：`yyyy-MM-dd HH:mm:ss`
- 文章状态：`0` 表示草稿，`1` 表示已发布。
- 新增接口不要提交 `articleId`；该字段只属于修改请求，新增 ID由后端雪花算法生成。
- `createDept`、`createBy`、`createTime`、`updateBy`、`updateTime` 均由后端维护，任何写接口都不要提交。
- 文章接口不使用通用 `params`；列表和导出只支持文档中明确列出的筛选字段。

普通接口响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {}
}
```

分页接口响应：

```json
{
  "code": 200,
  "msg": "查询成功",
  "rows": [],
  "total": 0
}
```

## 2. 接口清单

| 功能 | 方法 | 地址 | 权限标识 |
| --- | --- | --- | --- |
| 查询分类选项 | GET | `/content/article/category-options` | 文章查询、新增或修改权限 |
| 查询标签选项 | GET | `/content/article/tag-options` | 文章查询、新增或修改权限 |
| 文章分页列表 | GET | `/content/article/list` | `content:article:list` |
| 查询文章详情 | GET | `/content/article/{articleId}` | `content:article:query` |
| 新增文章 | POST | `/content/article` | `content:article:add` |
| 修改文章 | PUT | `/content/article` | `content:article:edit` |
| 删除文章 | DELETE | `/content/article/{articleIds}` | `content:article:remove` |
| 导出文章 | POST | `/content/article/export` | `content:article:export` |
| 上传正文图片或封面 | POST | `/resource/oss/upload` | `system:oss:upload` |
| 根据附件 ID 查询附件 | GET | `/resource/oss/listByIds/{ossIds}` | `system:oss:query` |

## 3. 查询文章分类

```http
GET /content/article/category-options
```

入参：无。

出参：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": [
    {
      "dictCode": "2085567048592797698",
      "dictSort": 1,
      "dictLabel": "公司新闻",
      "dictValue": "company_news",
      "cssClass": "",
      "listClass": "",
      "isDefault": "Y"
    }
  ]
}
```

前端使用 `dictCode` 作为下拉框值，使用 `dictLabel` 展示名称。

## 4. 查询文章标签

```http
GET /content/article/tag-options
```

入参：无。

出参：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": [
    {
      "dictCode": "2085567048592797699",
      "dictSort": 1,
      "dictLabel": "企业动态",
      "dictValue": "company_updates",
      "cssClass": "",
      "listClass": "primary",
      "isDefault": "N"
    }
  ]
}
```

前端使用 `dictCode` 作为多选框值，提交时组成 `tagIds`。标签非必填，最多选择 10 个。

## 5. 文章分页列表

```http
GET /content/article/list?pageNum=1&pageSize=10&title=新闻&categoryDictCode=2085567048592797698&status=1
```

Query 入参：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `pageNum` | number | 否 | 页码，默认 1 |
| `pageSize` | number | 否 | 每页条数 |
| `title` | string | 否 | 标题模糊查询 |
| `categoryDictCode` | string | 否 | 分类字典 ID |
| `status` | string | 否 | `0` 草稿、`1` 已发布 |
| `orderByColumn` | string | 否 | 排序字段 |
| `isAsc` | string | 否 | `asc` 或 `desc` |

出参：

```json
{
  "code": 200,
  "msg": "查询成功",
  "rows": [
    {
      "articleId": "2085567048592797800",
      "title": "公司新闻示例",
      "summary": "文章简介",
      "content": null,
      "categoryDictCode": "2085567048592797698",
      "tagIds": [
        "2085567048592797699",
        "2085567048592797700"
      ],
      "coverOssId": "2085567048592797701",
      "status": "1",
      "publishBy": "1",
      "publishTime": "2026-08-07 10:30:00",
      "createTime": "2026-08-07 10:20:00",
      "updateTime": "2026-08-07 10:30:00"
    }
  ],
  "total": 1
}
```

列表接口不返回正文内容，`content` 为 `null`。分类名称和标签名称由前端使用分类、标签选项中的 `dictCode` 映射展示。

## 6. 查询文章详情

```http
GET /content/article/2085567048592797800
```

Path 入参：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `articleId` | string | 是 | 文章 ID |

出参：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "articleId": "2085567048592797800",
    "title": "公司新闻示例",
    "summary": "文章简介",
    "content": "<h2>新闻标题</h2><p>正文内容</p><p><img src=\"https://example.com/article/image.png\"></p>",
    "categoryDictCode": "2085567048592797698",
    "tagIds": [
      "2085567048592797699",
      "2085567048592797700"
    ],
    "coverOssId": "2085567048592797701",
    "status": "1",
    "publishBy": "1",
    "publishTime": "2026-08-07 10:30:00",
    "createTime": "2026-08-07 10:20:00",
    "updateTime": "2026-08-07 10:30:00"
  }
}
```

详情接口返回完整 HTML 正文，可直接交给富文本编辑器回显。

## 7. 新增文章

```http
POST /content/article
Content-Type: application/json
```

Body 入参：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `title` | string | 是 | 标题，最多 200 字符 |
| `summary` | string | 否 | 简介，最多 500 字符 |
| `content` | string | 是 | 富文本 HTML |
| `categoryDictCode` | string | 是 | 分类字典 ID |
| `tagIds` | string[] | 否 | 标签字典 ID 列表，最多 10 个；可传 `null` 或 `[]` |
| `coverOssId` | string | 否 | 封面附件 ID，可传 `null` |
| `status` | string | 是 | `0` 草稿、`1` 已发布 |

新增请求只允许提交以下业务字段：

- `title`
- `summary`
- `content`
- `categoryDictCode`
- `tagIds`
- `coverOssId`
- `status`

`articleId`、审计字段和 `params` 不属于新增请求，即使客户端额外提交也不会用于保存。

请求 Demo：

```json
{
  "title": "公司新闻示例",
  "summary": "文章简介",
  "content": "<h2>新闻标题</h2><p>正文内容</p><p><img src=\"https://example.com/article/image.png\"></p>",
  "categoryDictCode": "2085567048592797698",
  "tagIds": [
    "2085567048592797699",
    "2085567048592797700"
  ],
  "coverOssId": "2085567048592797701",
  "status": "0"
}
```

出参：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": null
}
```

新增接口不返回新文章 ID。`publishBy`、`publishTime` 由后端维护，前端不要提交。

## 8. 修改文章

```http
PUT /content/article
Content-Type: application/json
```

必须额外传入 `articleId`，其余允许字段与新增一致。

请求 Demo：

```json
{
  "articleId": "2085567048592797800",
  "title": "修改后的公司新闻",
  "summary": "修改后的简介",
  "content": "<p>修改后的正文</p>",
  "categoryDictCode": "2085567048592797698",
  "tagIds": [],
  "coverOssId": null,
  "status": "1"
}
```

出参：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": null
}
```

- `tagIds: []` 或 `tagIds: null`：清空标签。
- `coverOssId: null`：清空封面。
- `summary: ""`：清空简介。
- 标签有重复项时，后端会自动去重并保持提交顺序。

## 9. 删除文章

单条删除：

```http
DELETE /content/article/2085567048592797800
```

批量删除：

```http
DELETE /content/article/2085567048592797800,2085567048592797801
```

Path 入参：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `articleIds` | string | 是 | 一个文章 ID，或多个 ID 使用英文逗号分隔 |

出参：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": null
}
```

该接口执行逻辑删除。

## 10. 导出文章

```http
POST /content/article/export?title=新闻&categoryDictCode=2085567048592797698&status=1
```

入参和列表筛选条件一致，常用字段为：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `title` | string | 否 | 标题模糊查询 |
| `categoryDictCode` | string | 否 | 分类字典 ID |
| `status` | string | 否 | `0` 草稿、`1` 已发布 |

出参：Excel 二进制文件，前端需要使用 `blob` 接收。

## 11. 上传正文图片或封面

```http
POST /resource/oss/upload
Content-Type: multipart/form-data
```

FormData 入参：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | File | 是 | 要上传的图片文件 |

出参：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "url": "https://example.com/upload/2026/08/image.png",
    "fileName": "image.png",
    "ossId": "2085567048592797701"
  }
}
```

- 正文图片：将返回的 `data.url` 插入富文本 `<img src="...">`。
- 封面图片：将返回的 `data.ossId` 保存为文章的 `coverOssId`。

上传 Demo：

```ts
const formData = new FormData()
formData.append('file', file)

const result = await request({
  url: '/resource/oss/upload',
  method: 'post',
  data: formData,
  headers: { 'Content-Type': 'multipart/form-data' }
})

// 富文本图片
const imageUrl = result.data.url

// 文章封面
form.coverOssId = result.data.ossId
```

## 12. 根据附件 ID 查询附件地址

当详情只返回 `coverOssId` 时，可查询封面 URL：

```http
GET /resource/oss/listByIds/2085567048592797701
```

多个附件 ID 使用英文逗号分隔。

出参示例：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": [
    {
      "ossId": "2085567048592797701",
      "fileName": "image.png",
      "originalName": "company-cover.png",
      "fileSuffix": ".png",
      "url": "https://example.com/upload/2026/08/image.png",
      "createTime": "2026-08-07 10:00:00"
    }
  ]
}
```

## 13. 页面初始化 Demo

新增页面进入时，并行查询分类和标签：

```ts
const [categoryRes, tagRes] = await Promise.all([
  request.get('/content/article/category-options'),
  request.get('/content/article/tag-options')
])

categoryOptions.value = categoryRes.data
tagOptions.value = tagRes.data
```

编辑页面进入时，再增加详情查询：

```ts
const articleId = route.params.articleId as string

const [detailRes, categoryRes, tagRes] = await Promise.all([
  request.get(`/content/article/${articleId}`),
  request.get('/content/article/category-options'),
  request.get('/content/article/tag-options')
])

form.value = detailRes.data
categoryOptions.value = categoryRes.data
tagOptions.value = tagRes.data
```
