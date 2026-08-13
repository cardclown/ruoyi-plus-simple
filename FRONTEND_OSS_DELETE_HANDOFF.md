# OSS 附件删除接口

## 接口

```http
POST /resource/oss/delete
Content-Type: application/json
```

## 入参

```json
{
  "ossIds": [
    "2087340692151115778",
    "2087340692151115779"
  ]
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ossIds` | `string[]` | 是 | 待删除的 OSS ID，必须按字符串传递 |

前端不需要传 `refType`、`refId` 或附件是否为临时文件，后端根据 `ossId` 自动判断。

## 返回

```json
{
  "code": 200,
  "msg": "删除请求处理完成",
  "data": [
    {
      "ossId": "2087340692151115778",
      "success": true,
      "message": "删除成功"
    },
    {
      "ossId": "2087340692151115779",
      "success": false,
      "message": "附件不存在"
    }
  ]
}
```

返回字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `ossId` | `string` | 本次处理的 OSS ID |
| `success` | `boolean` | `true` 表示删除请求处理成功，前端可以移除附件 |
| `message` | `string` | 后端返回的处理结果，前端直接展示即可 |

批量删除支持部分成功，一个附件失败不会影响其他附件。

前端根据每项结果处理：

```ts
if (item.success) {
  // 从文件列表和表单的 OSS ID 数组中移除附件
} else {
  // 保留附件，并展示后端返回的 item.message
}
```

附件不存在、无权删除、业务类型不支持或删除失败等具体原因，均由后端写入 `message`，前端不需要维护状态枚举和提示文案。

## 前端调用示例

```ts
export function deleteOss(ossIds: string[]) {
  return request({
    url: '/resource/oss/delete',
    method: 'post',
    data: { ossIds }
  });
}
```
