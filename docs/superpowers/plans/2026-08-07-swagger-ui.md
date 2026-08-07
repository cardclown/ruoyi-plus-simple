# Swagger UI 集成实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保留现有 `/v3/api-docs` 的前提下，为本地 RuoYi-Vue-Plus 后端增加可访问的原生 Swagger UI 页面。

**Architecture:** 复用现有 `ruoyi-common-doc` 文档模块，只把 SpringDoc 的 API starter 替换为同版本 UI starter。UI starter 传递包含 API starter，因此无需修改 Controller、SpringDoc 配置或业务接口。

**Tech Stack:** Java 21、Spring Boot 3.5、SpringDoc OpenAPI 2.8.17、Maven、PowerShell

## Global Constraints

- 必须继续使用 SpringDoc `2.8.17`。
- 不引入 Knife4j。
- 保留 `/v3/api-docs` 及现有文章接口文档。
- 不修改接口鉴权、API 加密和文章业务逻辑。
- 不处理工作区中与本任务无关的已有改动。

---

### Task 1: 替换 SpringDoc 依赖并验证 Swagger UI

**Files:**
- Modify: `pom.xml:119-126`
- Modify: `ruoyi-common/ruoyi-common-doc/pom.xml:23-28`
- Reference: `ruoyi-admin/src/main/resources/application.yml:181-207`

**Interfaces:**
- Consumes: Maven 属性 `springdoc.version=2.8.17` 和现有 `springdoc.api-docs.enabled=true` 配置。
- Produces: `GET /swagger-ui/index.html` 返回 Swagger UI HTML；`GET /v3/api-docs` 继续返回 OpenAPI JSON。

- [ ] **Step 1: 运行修改前的失败验证**

在当前运行的 `8080` 服务上执行：

```powershell
$response = Invoke-WebRequest `
  -Uri 'http://127.0.0.1:8080/swagger-ui/index.html' `
  -SkipHttpErrorCheck

if ($response.Content -notmatch '"code":404') {
    throw '预期 Swagger UI 当前返回业务 404'
}

throw 'RED: Swagger UI 尚未集成'
```

Expected: 命令以非零状态结束，并明确输出 `RED: Swagger UI 尚未集成`。这证明验证能够捕获当前缺失的 UI 功能。

- [ ] **Step 2: 验证当前依赖树中不存在 UI starter**

Run:

```powershell
$mvn = 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd'
$output = & $mvn -pl ruoyi-common/ruoyi-common-doc -am dependency:tree `
  '-Dincludes=org.springdoc:springdoc-openapi-starter-webmvc-ui' 2>&1
$outputText = $output -join "`n"

if ($outputText -notmatch 'springdoc-openapi-starter-webmvc-ui') {
    throw 'RED: 依赖树中不存在 SpringDoc Swagger UI'
}
```

Expected: FAIL，输出 `RED: 依赖树中不存在 SpringDoc Swagger UI`。

- [ ] **Step 3: 在根 POM 中替换受版本管理的依赖**

在 `pom.xml` 中将：

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-api</artifactId>
    <version>${springdoc.version}</version>
</dependency>
```

替换为：

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>${springdoc.version}</version>
</dependency>
```

- [ ] **Step 4: 在公共文档模块中替换实际依赖**

在 `ruoyi-common/ruoyi-common-doc/pom.xml` 中将：

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-api</artifactId>
</dependency>
```

替换为：

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
</dependency>
```

- [ ] **Step 5: 验证依赖树已经包含 UI starter**

Run:

```powershell
$mvn = 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd'
$output = & $mvn -pl ruoyi-common/ruoyi-common-doc -am dependency:tree `
  '-Dincludes=org.springdoc:springdoc-openapi-starter-webmvc-ui' 2>&1
$outputText = $output -join "`n"

if ($LASTEXITCODE -ne 0) {
    throw 'Maven dependency:tree 执行失败'
}

if ($outputText -notmatch 'springdoc-openapi-starter-webmvc-ui:jar:2.8.17') {
    throw '未解析到 SpringDoc Swagger UI 2.8.17'
}
```

Expected: PASS，并匹配 `springdoc-openapi-starter-webmvc-ui:jar:2.8.17`。

- [ ] **Step 6: 运行相关模块测试并打包应用**

Run:

```powershell
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' -pl ruoyi-admin -am test
```

Expected: Maven `BUILD SUCCESS`，测试失败数为 `0`。

Run:

```powershell
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' -pl ruoyi-admin -am package -DskipTests
```

Expected: Maven `BUILD SUCCESS`，生成 `ruoyi-admin/target/ruoyi-admin.jar`。

- [ ] **Step 7: 在独立端口启动新构建并验证 HTTP 契约**

使用独立端口 `18081`，避免停止用户当前在 IntelliJ 中运行的 `8080` 服务：

```powershell
$java = 'C:\Users\dj\.jdks\ms-21.0.12\bin\java.exe'
$jar = 'C:\Users\dj\ruoyi-plus\simple\RuoYi-Vue-Plus\ruoyi-admin\target\ruoyi-admin.jar'
$stdoutLog = Join-Path $env:TEMP 'ruoyi-swagger-ui-verification.out.log'
$stderrLog = Join-Path $env:TEMP 'ruoyi-swagger-ui-verification.err.log'

$process = Start-Process `
  -FilePath $java `
  -ArgumentList '-jar', $jar, '--server.port=18081' `
  -RedirectStandardOutput $stdoutLog `
  -RedirectStandardError $stderrLog `
  -WindowStyle Hidden `
  -PassThru

try {
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try {
            $index = Invoke-WebRequest `
              -Uri 'http://127.0.0.1:18081/swagger-ui/index.html' `
              -SkipHttpErrorCheck `
              -TimeoutSec 2
            if ($index.StatusCode -eq 200) {
                $ready = $true
                break
            }
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }

    if (-not $ready) {
        throw "Swagger UI 未在规定时间内启动，日志：$stdoutLog、$stderrLog"
    }

    if ($index.Headers['Content-Type'] -notmatch 'text/html') {
        throw 'Swagger UI 未返回 HTML'
    }

    $openApi = Invoke-RestMethod -Uri 'http://127.0.0.1:18081/v3/api-docs'
    if (-not $openApi.paths.'/content/article') {
        throw 'OpenAPI 文档缺少 /content/article'
    }
    if (-not $openApi.paths.'/content/article/category-options') {
        throw 'OpenAPI 文档缺少文章分类接口'
    }
    if (-not $openApi.paths.'/content/article/tag-options') {
        throw 'OpenAPI 文档缺少文章标签接口'
    }
} finally {
    if ($process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id
    }
}
```

Expected: Swagger UI 返回 `200 text/html`，`/v3/api-docs` 保持可用，并包含文章管理、分类和标签路径。只停止本步骤创建的 `18081` 验证进程。

- [ ] **Step 8: 检查改动范围并提交**

Run:

```powershell
git diff --check -- pom.xml ruoyi-common/ruoyi-common-doc/pom.xml
git diff -- pom.xml ruoyi-common/ruoyi-common-doc/pom.xml
```

Expected: 无空白错误，差异仅包含两个 SpringDoc artifactId 替换。

Commit:

```powershell
git add -- pom.xml ruoyi-common/ruoyi-common-doc/pom.xml
git commit -m "build: add SpringDoc Swagger UI"
```
