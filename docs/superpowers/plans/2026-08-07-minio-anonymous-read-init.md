# MinIO 匿名只读自动初始化实施计划

> **给执行代理：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans`，按任务逐项实施本计划。所有执行步骤均使用复选框（`- [ ]`）跟踪。

**目标：** 每次启动本地 Docker Compose 基础设施时，自动创建指定的 MinIO 桶并为其配置匿名只读权限。

**架构：** 在 Compose 中新增一个幂等的一次性 `minio-init` 服务。该服务等待 MinIO 可用，按需创建精确指定的桶，并执行 `mc anonymous set download`。Compose 保留本地默认配置，同时通过 `.env.example` 支持可选覆盖；桶策略由基础设施启动流程负责，不依赖 Java 后台启动。

**技术栈：** Docker Compose、MinIO `mc`、PowerShell 验证、Git

## 全局约束

- MinIO 默认用户名为 `ruoyi`。
- MinIO 默认密码为 `ruoyi123`。
- 默认桶名为 `ruoyi`。
- 可通过 `MINIO_ROOT_USER`、`MINIO_ROOT_PASSWORD` 和 `MINIO_BUCKET` 覆盖默认值。
- 匿名权限只能是只读（`download`），不得允许匿名上传、覆盖、删除或管理。
- 初始化必须幂等，不得删除或重建已存在的业务桶。
- 不得修改或提交用户现有的内容模块、SQL 清理及其他工作区改动。

---

## 文件结构

- 修改 `.gitignore`：防止本地 Docker 凭证进入 Git。
- 新建 `script/docker/.env.example`：记录三个支持的环境变量及其默认值。
- 修改 `script/docker/docker-compose.yml`：参数化 MinIO 凭证并增加一次性初始化服务。

### 任务一：定义本地环境变量契约

**文件：**

- 修改：`.gitignore`
- 新建：`script/docker/.env.example`
- 测试：临时 PowerShell 断言，不创建持久测试文件

**接口：**

- 输入：Docker Compose 从 `script/docker` 自动读取 `.env` 并进行变量插值。
- 输出：任务二使用的 `MINIO_ROOT_USER`、`MINIO_ROOT_PASSWORD` 和 `MINIO_BUCKET` 配置契约。

- [ ] **步骤 1：运行失败的配置契约测试**

在仓库根目录执行：

```powershell
$example = 'script/docker/.env.example'
if (-not (Test-Path $example)) { throw "$example 不存在" }
git check-ignore --no-index --quiet -- 'script/docker/.env'
if ($LASTEXITCODE -ne 0) { throw 'script/docker/.env 尚未被忽略' }
```

预期：失败并提示 `script/docker/.env.example 不存在`。

- [ ] **步骤 2：添加本地配置忽略规则**

在 `.gitignore` 末尾追加：

```gitignore
######################################################################
# Local Docker Compose environment

script/docker/.env
```

- [ ] **步骤 3：添加可提交的环境变量模板**

创建 `script/docker/.env.example`：

```dotenv
# 可选的本地覆盖项。没有此文件时，docker-compose.yml 使用以下默认值。
MINIO_ROOT_USER=ruoyi
MINIO_ROOT_PASSWORD=ruoyi123
MINIO_BUCKET=ruoyi
```

- [ ] **步骤 4：重新运行配置契约测试**

再次运行步骤 1 的 PowerShell 断言。

预期：退出码为 0，无输出。

- [ ] **步骤 5：确认真实配置被忽略，模板仍可提交**

执行：

```powershell
git check-ignore --no-index -v -- 'script/docker/.env'
git check-ignore --no-index --quiet -- 'script/docker/.env.example'
if ($LASTEXITCODE -eq 0) { throw '.env.example 不应被忽略' }
```

预期：第一条命令显示新增的 `.gitignore` 规则；第二个断言成功，因为 `.env.example` 没有被忽略。

- [ ] **步骤 6：只提交环境变量契约文件**

```powershell
git add -- '.gitignore' 'script/docker/.env.example'
git commit --only -m 'chore: document local MinIO environment' -- '.gitignore' 'script/docker/.env.example'
```

预期：提交中恰好包含以上两个文件，其他已暂存改动保持不变。

### 任务二：添加幂等的 MinIO 初始化服务

**文件：**

- 修改：`script/docker/docker-compose.yml`
- 测试：`docker compose config` 断言及一次性服务实机执行

**接口：**

- 输入：任务一定义的三个环境变量。
- 输出：Compose 服务 `minio-init`；确保 `local/$MINIO_BUCKET` 存在并具有匿名 `download` 权限后，以状态码 0 退出。

- [ ] **步骤 1：用失败测试复现初始化服务缺失**

在 `script/docker` 目录执行：

```powershell
$config = docker compose config --format json | ConvertFrom-Json
if (-not $config.services.'minio-init') { throw '缺少 minio-init 服务' }
```

预期：失败并提示 `缺少 minio-init 服务`。

- [ ] **步骤 2：参数化 MinIO 服务凭证**

将 `services.minio.environment` 下两个固定凭证替换为：

```yaml
      MINIO_ROOT_USER: ${MINIO_ROOT_USER:-ruoyi}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD:-ruoyi123}
```

保持现有镜像、端口、卷、压缩配置、启动命令和重启策略不变。

- [ ] **步骤 3：添加一次性初始化服务**

在 `minio` 服务之后、顶层 `volumes` 之前添加：

```yaml
  minio-init:
    image: pgsty/minio:RELEASE.2026-04-17T00-00-00Z
    container_name: minio-init
    depends_on:
      - minio
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER:-ruoyi}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD:-ruoyi123}
      MINIO_BUCKET: ${MINIO_BUCKET:-ruoyi}
    entrypoint: ["/bin/sh", "-c"]
    command:
      - |
        set -eu
        mc alias set local http://minio:9000 "$$MINIO_ROOT_USER" "$$MINIO_ROOT_PASSWORD"
        until mc ready local >/dev/null 2>&1; do
          echo "Waiting for MinIO..."
          sleep 2
        done
        mc mb --ignore-existing "local/$$MINIO_BUCKET"
        mc anonymous set download "local/$$MINIO_BUCKET"
        mc anonymous get "local/$$MINIO_BUCKET"
    restart: "no"
```

双美元符号是必需的：它让 Compose 把变量传给容器内的 Shell，而不是在宿主机提前插值。

- [ ] **步骤 4：验证 Compose 渲染后的默认值**

确保三个覆盖变量不存在，然后在 `script/docker` 目录执行：

```powershell
Remove-Item Env:MINIO_ROOT_USER,Env:MINIO_ROOT_PASSWORD,Env:MINIO_BUCKET -ErrorAction SilentlyContinue
$config = docker compose config --format json | ConvertFrom-Json
$init = $config.services.'minio-init'
if (-not $init) { throw '缺少 minio-init 服务' }
if ($config.services.minio.environment.MINIO_ROOT_USER -ne 'ruoyi') { throw '默认 MinIO 用户名错误' }
if ($config.services.minio.environment.MINIO_ROOT_PASSWORD -ne 'ruoyi123') { throw '默认 MinIO 密码错误' }
if ($init.environment.MINIO_BUCKET -ne 'ruoyi') { throw '默认桶名错误' }
if (($init.command -join "`n") -notmatch 'anonymous set download') { throw '缺少匿名只读策略命令' }
```

预期：退出码为 0，无输出。

- [ ] **步骤 5：验证环境变量覆盖生效**

执行：

```powershell
$env:MINIO_ROOT_USER = 'override-user'
$env:MINIO_ROOT_PASSWORD = 'override-password'
$env:MINIO_BUCKET = 'override-bucket'
$config = docker compose config --format json | ConvertFrom-Json
if ($config.services.minio.environment.MINIO_ROOT_USER -ne 'override-user') { throw '用户名覆盖失败' }
if ($config.services.minio.environment.MINIO_ROOT_PASSWORD -ne 'override-password') { throw '密码覆盖失败' }
if ($config.services.'minio-init'.environment.MINIO_BUCKET -ne 'override-bucket') { throw '桶名覆盖失败' }
Remove-Item Env:MINIO_ROOT_USER,Env:MINIO_ROOT_PASSWORD,Env:MINIO_BUCKET
```

预期：退出码为 0，无输出。

- [ ] **步骤 6：只提交 Compose 实现**

```powershell
git add -- 'script/docker/docker-compose.yml'
git commit --only -m 'feat: initialize MinIO bucket access' -- 'script/docker/docker-compose.yml'
```

预期：提交中只包含 `script/docker/docker-compose.yml`。

### 任务三：应用并验证运行时策略

**文件：**

- 不修改仓库文件
- 测试：当前 MinIO 桶、原始上传对象及临时覆盖桶

**接口：**

- 输入：任务二的 `minio-init` 服务和当前运行的 `minio` 容器。
- 输出：本地 `ruoyi` 桶具有匿名下载权限，并提供完整验证证据。

- [ ] **步骤 1：应用策略前重新复现 HTTP 失败**

执行：

```powershell
$status = curl.exe -sS -o NUL -w '%{http_code}' 'http://127.0.0.1:9000/ruoyi/2026/08/07/68eaeba8fa52479a85d4afc08b59a43c.jpg'
if ($status -ne '403') { throw "预期修复前为 403，实际为 $status" }
```

预期：断言通过，证明修复前响应为 403。

- [ ] **步骤 2：通过正常 Compose 路径启动初始化服务**

在 `script/docker` 目录执行：

```powershell
docker compose up -d --force-recreate minio-init
if ($LASTEXITCODE -ne 0) { throw 'minio-init 启动失败' }
docker wait minio-init | Out-Null
$exitCode = docker inspect minio-init --format '{{.State.ExitCode}}'
if ($exitCode -ne '0') { docker logs minio-init; throw "minio-init 退出码为 $exitCode" }
```

预期：`minio-init` 以状态码 0 退出，并报告 `download` 权限。

- [ ] **步骤 3：验证桶策略和原始对象**

执行：

```powershell
$mcHost = 'http://ruoyi:ruoyi123@127.0.0.1:9000'
$policy = docker exec -e MC_HOST_local=$mcHost minio mc anonymous get local/ruoyi
if ($policy -notmatch 'download') { throw "桶策略异常：$policy" }
docker exec -e MC_HOST_local=$mcHost minio mc stat 'local/ruoyi/2026/08/07/68eaeba8fa52479a85d4afc08b59a43c.jpg'
if ($LASTEXITCODE -ne 0) { throw '原始对象不存在' }
```

预期：策略为 `download`，原始 JPEG 仍然存在。

- [ ] **步骤 4：验证幂等性**

再次完整执行步骤 2，然后重新执行步骤 3 的两个断言。

预期：初始化服务再次以状态码 0 退出；策略仍是 `download`，原始对象仍然存在。

- [ ] **步骤 5：使用一次性空桶验证桶名覆盖**

在 `script/docker` 目录执行：

```powershell
$testBucket = 'ruoyi-policy-test-' + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
docker compose run --rm -e MINIO_BUCKET=$testBucket minio-init
if ($LASTEXITCODE -ne 0) { throw '覆盖桶初始化失败' }
$mcHost = 'http://ruoyi:ruoyi123@127.0.0.1:9000'
$policy = docker exec -e MC_HOST_local=$mcHost minio mc anonymous get "local/$testBucket"
if ($policy -notmatch 'download') { throw "覆盖桶策略异常：$policy" }
docker exec -e MC_HOST_local=$mcHost minio mc rb "local/$testBucket"
if ($LASTEXITCODE -ne 0) { throw '空验证桶清理失败' }
```

预期：临时桶获得 `download` 策略，验证后在空桶状态下被删除。

- [ ] **步骤 6：验证原始浏览器 URL 已恢复**

执行：

```powershell
$headerLines = curl.exe -sS -D - -o NUL 'http://127.0.0.1:9000/ruoyi/2026/08/07/68eaeba8fa52479a85d4afc08b59a43c.jpg'
$headers = $headerLines -join "`n"
if ($headers -notmatch 'HTTP/1.1 200 OK') { throw "未返回 200：$headers" }
if ($headers -notmatch '(?im)^Content-Type: image/jpeg') { throw "Content-Type 错误：$headers" }
```

预期：返回 HTTP 200，且 `Content-Type` 为 `image/jpeg`。

- [ ] **步骤 7：运行最终源码和 Compose 检查**

在仓库根目录执行：

```powershell
git diff --check HEAD~3..HEAD -- '.gitignore' 'script/docker/.env.example' 'script/docker/docker-compose.yml'
Push-Location 'script/docker'
docker compose config --quiet
$composeExit = $LASTEXITCODE
Pop-Location
if ($composeExit -ne 0) { throw 'Compose 校验失败' }
git status --short
```

预期：本任务文件没有空白错误，Compose 校验退出码为 0，状态输出中只剩用户原有的无关工作区改动。
