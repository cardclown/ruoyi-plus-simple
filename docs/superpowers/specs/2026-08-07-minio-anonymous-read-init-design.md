# MinIO 匿名只读自动初始化设计

## 目标

让本地 Docker Compose 启动 MinIO 时，自动确保目标桶存在，并将桶配置为匿名只读。浏览器可以直接读取已上传对象，但匿名用户不能上传、覆盖或删除对象。

当前默认配置保持不变：MinIO 用户名为 `ruoyi`，密码为 `ruoyi123`，桶名为 `ruoyi`。部署环境可以通过环境变量覆盖这些值，无需修改 Compose 文件。

## 现状与根因

`sys_oss_config.access_policy` 将默认 MinIO 配置声明为公开，但实际 `ruoyi` 桶仍是私有策略。后台因此返回不带签名的对象 URL，而 MinIO 拒绝匿名 GET 请求并返回 403。

应用代码不会主动把数据库里的 `access_policy` 同步成 MinIO 桶策略。桶创建和访问策略应由本地基础设施启动流程负责。

## 方案

在 `script/docker/docker-compose.yml` 中新增一个一次性 `minio-init` 服务。该服务与 MinIO 使用同一镜像，并在 MinIO 启动后执行以下幂等流程：

1. 循环尝试连接 MinIO，直到服务可用。
2. 使用 `mc mb --ignore-existing` 创建目标桶；桶已存在时不报错。
3. 使用 `mc anonymous set download` 设置匿名只读策略。
4. 成功后退出，保持 MinIO 主服务独立运行。

不采用包装 MinIO 主进程的启动脚本，因为这会增加信号转发和进程退出处理的复杂度。不在 Java 后台中配置桶策略，因为对象存储初始化不应依赖业务应用是否成功启动。

## 配置

Compose 使用以下环境变量及默认值：

| 环境变量 | 默认值 | 用途 |
| --- | --- | --- |
| `MINIO_ROOT_USER` | `ruoyi` | MinIO 管理账号 |
| `MINIO_ROOT_PASSWORD` | `ruoyi123` | MinIO 管理密码 |
| `MINIO_BUCKET` | `ruoyi` | 自动创建并设置策略的桶 |

新增 `script/docker/.env.example`，提交默认配置模板。真实的 `script/docker/.env` 由部署者按需创建，并在仓库根 `.gitignore` 中忽略，防止真实凭证进入版本控制。

在 `script/docker` 目录运行 `docker compose up -d` 时，未创建 `.env` 也会使用上述默认值；创建 `.env` 后则使用其中的覆盖值。

## 安全边界

匿名策略使用 `download`，只允许读取对象。初始化流程不会配置匿名上传、覆盖、删除或管理权限。

管理凭证仅传给 MinIO 和一次性初始化服务。日志不得输出密码。初始化脚本只操作 `MINIO_BUCKET` 指定的精确桶名。

## 错误处理

初始化服务在 MinIO 尚未就绪时等待并重试，避免固定延迟带来的竞态。认证失败、非法桶名或策略设置失败时，服务以非零状态退出，使 `docker compose ps -a` 和日志能够明确暴露失败。

重复执行初始化流程不会删除对象或重建已有桶，只会重新确认匿名只读策略。

## 验证

实施遵循以下验证顺序：

1. 修改前确认目标图片匿名 GET 返回 403，作为故障复现。
2. 使用 `docker compose config` 验证 Compose 语法、默认值及服务依赖。
3. 启动 `minio-init`，确认服务以状态码 0 退出。
4. 使用 `mc anonymous get` 确认 `ruoyi` 桶策略为 `download`。
5. 再次运行初始化，确认幂等执行成功且原对象仍存在。
6. 使用临时覆盖桶名验证 `MINIO_BUCKET` 环境变量生效，验证后删除该空测试桶。
7. 请求原图片 URL，确认返回 HTTP 200 和 `image/jpeg`。

## 变更范围

只修改以下文件：

- `script/docker/docker-compose.yml`
- `script/docker/.env.example`
- `.gitignore`

同时对当前运行中的本地 MinIO `ruoyi` 桶应用匿名只读策略。不会修改内容模块、业务 SQL、数据库数据或其他正在进行的工作区改动。
