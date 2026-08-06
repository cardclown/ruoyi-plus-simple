# Image Captcha Restoration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the original RuoYi-Vue-Plus 5.X image captcha flow for password login and registration while keeping SMS, email, and social authentication removed.

**Architecture:** Recover the captcha implementation from parent commit `8ba8d9516^`, then prune SMS/email endpoints from the recovered controller before compiling. The frontend remains unchanged and continues to reach the backend through Vite's `/dev-api` proxy.

**Tech Stack:** Java 21, Spring Boot 3, Hutool Captcha, Redis/Redisson, Maven, Vue 3, Vite

## Global Constraints

- Use RuoYi-Vue-Plus 5.6.2 source from `8ba8d9516^`; do not rewrite the captcha algorithm.
- Final backend exposes image captcha only; SMS, email, social, and mini-program authentication remain removed.
- Captcha is enabled by default, uses math mode, expires after 2 minutes, and is single-use.
- Password login and registration both validate `code` plus `uuid` when captcha is enabled.
- Frontend files and the `/dev-api -> http://localhost:8080` proxy remain unchanged.
- Preserve PostgreSQL, Redis, MinIO, and all existing pruning work.

## File Structure

- `ruoyi-admin/.../controller/CaptchaController.java`: image captcha HTTP endpoint and Redis persistence.
- `ruoyi-admin/.../domain/vo/CaptchaVo.java`: response contract for `captchaEnabled`, `uuid`, and `img`.
- `ruoyi-common/ruoyi-common-web/.../CaptchaConfig.java`: registers captcha configuration properties.
- `ruoyi-common/ruoyi-common-web/.../CaptchaProperties.java`: binds `captcha.*` settings.
- `ruoyi-common/ruoyi-common-web/.../WaveAndCircleCaptcha.java`: original 5.X image rendering implementation.
- `ruoyi-common/ruoyi-common-core/.../LoginBody.java`: carries `code` and `uuid` for login and registration bodies.
- `ruoyi-common/ruoyi-common-core/.../CaptchaException.java`: wrong-code error.
- `ruoyi-common/ruoyi-common-core/.../CaptchaExpireException.java`: missing/expired-code error.
- `ruoyi-admin/.../PasswordAuthStrategy.java`: validates captcha before password authentication.
- `ruoyi-admin/.../SysRegisterService.java`: validates captcha before registration.
- Maven, auto-configuration, YAML, constants, and i18n files: wire the recovered implementation into the current application.

---

### Task 1: Restore the original image generation endpoint

**Files:**
- Create: `ruoyi-admin/src/main/java/org/dromara/web/controller/CaptchaController.java`
- Create: `ruoyi-admin/src/main/java/org/dromara/web/domain/vo/CaptchaVo.java`
- Create: `ruoyi-common/ruoyi-common-web/src/main/java/org/dromara/common/web/config/CaptchaConfig.java`
- Create: `ruoyi-common/ruoyi-common-web/src/main/java/org/dromara/common/web/config/properties/CaptchaProperties.java`
- Create: `ruoyi-common/ruoyi-common-web/src/main/java/org/dromara/common/web/core/WaveAndCircleCaptcha.java`
- Modify: `ruoyi-common/ruoyi-common-web/pom.xml`
- Modify: `ruoyi-common/ruoyi-common-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/constant/Constants.java`
- Modify: `ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/constant/GlobalConstants.java`
- Modify: `ruoyi-admin/src/main/resources/application.yml`

**Interfaces:**
- Consumes: `RedisUtils`, `captcha.*` configuration, `GlobalConstants.CAPTCHA_CODE_KEY`.
- Produces: `GET /auth/code -> R<CaptchaVo>` and Redis key `global:captcha_codes:<uuid>`.

- [ ] **Step 1: Record the failing endpoint**

Run:

```powershell
curl.exe -sS http://localhost:8080/auth/code
```

Expected current result:

```json
{"code":404,"msg":"请求地址不存在","data":null}
```

- [ ] **Step 2: Recover the exact 5.X source**

Read each original file from `8ba8d9516^` and recreate it with `apply_patch`. For example:

```powershell
git show "8ba8d9516^:ruoyi-admin/src/main/java/org/dromara/web/controller/CaptchaController.java"
git show "8ba8d9516^:ruoyi-common/ruoyi-common-web/src/main/java/org/dromara/common/web/core/WaveAndCircleCaptcha.java"
```

Recover `CaptchaController` verbatim first, then remove only `smsCode`, `emailCode`, and `emailCodeImpl`, together with their mail/SMS imports, `MailProperties`, and the corresponding constructor dependency. The final controller retains these exact methods:

```java
@GetMapping("/auth/code")
public R<CaptchaVo> getCode()

@RateLimiter(time = 60, count = 10, limitType = LimitType.IP)
public CaptchaVo getCodeImpl()
```

- [ ] **Step 3: Restore configuration and dependency wiring**

Restore the original `hutool-captcha` dependency and auto-configuration entry:

```xml
<dependency>
    <groupId>cn.hutool</groupId>
    <artifactId>hutool-captcha</artifactId>
</dependency>
```

```text
org.dromara.common.web.config.CaptchaConfig
```

Restore the original YAML block:

```yaml
captcha:
  enable: true
  type: math
  numberLength: 1
  charLength: 4
```

Restore `Constants.CAPTCHA_EXPIRATION = 2` and `GlobalConstants.CAPTCHA_CODE_KEY = GLOBAL_REDIS_KEY + "captcha_codes:"` from the parent commit.

- [ ] **Step 4: Compile the recovered generation layer**

Run:

```powershell
$env:JAVA_HOME = 'C:\Users\dj\.jdks\ms-21.0.12'
$env:Path = "$env:JAVA_HOME\bin;" + [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
& 'D:\IntelliJ IDEA 2026.2.0.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' -Pdev -pl ruoyi-admin -am -DskipTests package
```

Expected: reactor build ends with `BUILD SUCCESS` and no mail/SMS dependency is reintroduced.

- [ ] **Step 5: Commit the generation layer**

```powershell
git add ruoyi-admin/src/main/java/org/dromara/web/controller/CaptchaController.java ruoyi-admin/src/main/java/org/dromara/web/domain/vo/CaptchaVo.java ruoyi-admin/src/main/resources/application.yml ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/constant/Constants.java ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/constant/GlobalConstants.java ruoyi-common/ruoyi-common-web
git commit -m "feat: 恢复图片验证码生成接口"
```

### Task 2: Restore login and registration validation

**Files:**
- Modify: `ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/domain/model/LoginBody.java`
- Create: `ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/exception/user/CaptchaException.java`
- Create: `ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/exception/user/CaptchaExpireException.java`
- Modify: `ruoyi-admin/src/main/java/org/dromara/web/service/impl/PasswordAuthStrategy.java`
- Modify: `ruoyi-admin/src/main/java/org/dromara/web/service/SysRegisterService.java`
- Modify: `ruoyi-admin/src/main/resources/i18n/messages.properties`
- Modify: `ruoyi-admin/src/main/resources/i18n/messages_en_US.properties`
- Modify: `ruoyi-admin/src/main/resources/i18n/messages_zh_CN.properties`

**Interfaces:**
- Consumes: `LoginBody.getCode()`, `LoginBody.getUuid()`, Redis key produced by Task 1.
- Produces: one-time captcha validation before password login and registration.

- [ ] **Step 1: Confirm the validation contract is currently absent**

Run:

```powershell
rg -n "getCode\(|getUuid\(|validateCaptcha|CaptchaExpireException" ruoyi-admin/src/main/java ruoyi-common/ruoyi-common-core/src/main/java
```

Expected current result: no password-login or registration captcha validation matches.

- [ ] **Step 2: Restore request fields and exceptions from the parent commit**

Restore these exact fields to `LoginBody`:

```java
private String code;
private String uuid;
```

Restore `CaptchaException` with message key `user.jcaptcha.error` and `CaptchaExpireException` with message key `user.jcaptcha.expire`. Restore both message keys in all three message bundles.

- [ ] **Step 3: Restore the original one-time validation flow**

Recover the original `validateCaptcha` implementations in `PasswordAuthStrategy` and `SysRegisterService` from `8ba8d9516^`. Both implementations must keep this order:

```java
String captcha = RedisUtils.getCacheObject(verifyKey);
RedisUtils.deleteObject(verifyKey);
if (captcha == null) {
    throw new CaptchaExpireException();
}
if (!StringUtils.equalsIgnoreCase(code, captcha)) {
    throw new CaptchaException();
}
```

Keep the `CaptchaProperties.getEnable()` guard so disabled captcha does not block login or registration.

- [ ] **Step 4: Compile the complete authentication flow**

Run the same Maven reactor command from Task 1.

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit validation restoration**

```powershell
git add ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/domain/model/LoginBody.java ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/exception/user ruoyi-admin/src/main/java/org/dromara/web/service/impl/PasswordAuthStrategy.java ruoyi-admin/src/main/java/org/dromara/web/service/SysRegisterService.java ruoyi-admin/src/main/resources/i18n
git commit -m "feat: 恢复登录图片验证码校验"
```

### Task 3: Verify backend and frontend proxy behavior

**Files:**
- Verify only: `ruoyi-admin/target/ruoyi-admin.jar`
- Verify only: `../plus-ui/vite.config.ts`
- Verify only: `../plus-ui/src/api/login.ts`

**Interfaces:**
- Consumes: packaged backend from Tasks 1 and 2, Redis container `redis`, frontend port 80.
- Produces: evidence that image captcha generation, Redis persistence, and Vite proxying work together.

- [ ] **Step 1: Run a final clean package**

Run:

```powershell
& 'D:\IntelliJ IDEA 2026.2.0.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' -Pdev -pl ruoyi-admin -am -DskipTests clean package
```

Expected: `BUILD SUCCESS` and `ruoyi-admin/target/ruoyi-admin.jar` exists.

- [ ] **Step 2: Start the packaged backend on an isolated verification port**

Start `ruoyi-admin.jar` on port 18080 with `Start-Process -WindowStyle Hidden`, redirect logs to a temporary directory, and poll `http://localhost:18080/auth/code` for at most 60 seconds. Do not stop the IntelliJ-managed backend on port 8080.

- [ ] **Step 3: Verify the response contract and Redis persistence**

Run:

```powershell
$result = Invoke-RestMethod 'http://localhost:18080/auth/code'
if ($result.code -ne 200) { throw "captcha endpoint failed: $($result | ConvertTo-Json -Compress)" }
if ([string]::IsNullOrWhiteSpace($result.data.uuid)) { throw 'uuid is missing' }
if ($result.data.img -notmatch '^data:image|^iVBOR') { throw 'captcha image is missing' }
docker exec redis redis-cli -a ruoyi123 EXISTS ("global:captcha_codes:" + $result.data.uuid)
docker exec redis redis-cli -a ruoyi123 TTL ("global:captcha_codes:" + $result.data.uuid)
```

Expected: Redis `EXISTS` returns `1`; TTL is greater than `0` and no greater than `120`.

- [ ] **Step 4: Stop only the isolated verification process**

Resolve its PID from the exact command line containing `ruoyi-admin.jar --server.port=18080`, stop only that PID, and confirm port 18080 is no longer listening.

- [ ] **Step 5: Verify the frontend wiring remains unchanged**

Run:

```powershell
rg -n "VITE_APP_BASE_API = '/dev-api'|target: 'http://localhost:8080'|url: '/auth/code'" ..\plus-ui\.env.development ..\plus-ui\vite.config.ts ..\plus-ui\src\api\login.ts
git -C ..\plus-ui status --short
```

Expected: all three mappings are present and the frontend worktree has no source changes from this task.

- [ ] **Step 6: Verify final scope and repository state**

Run:

```powershell
rg -n "resource/sms/code|resource/email/code|MailProperties|SmsFactory" ruoyi-admin/src/main/java/org/dromara/web/controller/CaptchaController.java
git status --short
```

Expected: no SMS/email matches; repository status contains no uncommitted implementation files.
