# MinIO Anonymous Read Initialization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure Docker Compose creates the configured MinIO bucket and applies anonymous read-only access on every local infrastructure startup.

**Architecture:** Add an idempotent one-shot `minio-init` Compose service that waits for MinIO, creates the exact configured bucket if needed, and applies `mc anonymous set download`. Keep local defaults in Compose, provide an optional `.env.example`, and leave policy ownership in infrastructure rather than Java application startup.

**Tech Stack:** Docker Compose, MinIO `mc`, PowerShell verification, Git

## Global Constraints

- Default MinIO username is `ruoyi`.
- Default MinIO password is `ruoyi123`.
- Default bucket name is `ruoyi`.
- `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`, and `MINIO_BUCKET` can override the defaults.
- Anonymous access is read-only (`download`); anonymous write, overwrite, delete, and administration remain forbidden.
- Initialization is idempotent and never deletes or recreates an existing application bucket.
- Do not modify or commit the user's content module, SQL cleanup, or other existing worktree changes.

---

## File Structure

- Modify `.gitignore`: keep local Docker credentials out of Git.
- Create `script/docker/.env.example`: document the three supported environment variables and defaults.
- Modify `script/docker/docker-compose.yml`: parameterize MinIO credentials and add the one-shot initialization service.

### Task 1: Define the Local Environment Contract

**Files:**
- Modify: `.gitignore`
- Create: `script/docker/.env.example`
- Test: ephemeral PowerShell assertions; no persistent test file

**Interfaces:**
- Consumes: Docker Compose automatic `.env` interpolation from `script/docker`.
- Produces: `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`, and `MINIO_BUCKET` configuration contract used by Task 2.

- [ ] **Step 1: Run the failing configuration-contract test**

Run from the repository root:

```powershell
$example = 'script/docker/.env.example'
if (-not (Test-Path $example)) { throw "$example is missing" }
git check-ignore --no-index --quiet -- 'script/docker/.env'
if ($LASTEXITCODE -ne 0) { throw 'script/docker/.env is not ignored' }
```

Expected: FAIL with `script/docker/.env.example is missing`.

- [ ] **Step 2: Add the ignored local file rule**

Append this exact block to `.gitignore`:

```gitignore
######################################################################
# Local Docker Compose environment

script/docker/.env
```

- [ ] **Step 3: Add the committed environment template**

Create `script/docker/.env.example` with:

```dotenv
# Optional local overrides. docker-compose.yml uses these defaults when this file is absent.
MINIO_ROOT_USER=ruoyi
MINIO_ROOT_PASSWORD=ruoyi123
MINIO_BUCKET=ruoyi
```

- [ ] **Step 4: Re-run the configuration-contract test**

Run the Step 1 PowerShell assertions again.

Expected: PASS with exit code 0 and no output.

- [ ] **Step 5: Confirm the template is tracked but the real file is ignored**

Run:

```powershell
git check-ignore --no-index -v -- 'script/docker/.env'
git check-ignore --no-index --quiet -- 'script/docker/.env.example'
if ($LASTEXITCODE -eq 0) { throw '.env.example must remain trackable' }
```

Expected: the first command identifies the new `.gitignore` rule; the second assertion exits successfully because `.env.example` is not ignored.

- [ ] **Step 6: Commit only the environment contract files**

```powershell
git add -- '.gitignore' 'script/docker/.env.example'
git commit --only -m 'chore: document local MinIO environment' -- '.gitignore' 'script/docker/.env.example'
```

Expected: the commit contains exactly the two listed files; unrelated staged changes remain untouched.

### Task 2: Add the Idempotent MinIO Initializer

**Files:**
- Modify: `script/docker/docker-compose.yml`
- Test: `docker compose config` assertions plus live one-shot service execution

**Interfaces:**
- Consumes: the three environment variables defined in Task 1.
- Produces: Compose service `minio-init`, which exits 0 after ensuring `local/$MINIO_BUCKET` exists with anonymous `download` policy.

- [ ] **Step 1: Reproduce the missing service with a failing Compose test**

Run from `script/docker`:

```powershell
$config = docker compose config --format json | ConvertFrom-Json
if (-not $config.services.'minio-init') { throw 'minio-init service is missing' }
```

Expected: FAIL with `minio-init service is missing`.

- [ ] **Step 2: Parameterize the MinIO service credentials**

Replace the two fixed credential entries under `services.minio.environment` with:

```yaml
      MINIO_ROOT_USER: ${MINIO_ROOT_USER:-ruoyi}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD:-ruoyi123}
```

Keep the existing image, ports, volumes, compression settings, command, and restart policy unchanged.

- [ ] **Step 3: Add the one-shot initialization service**

Add this service immediately after `minio` and before the top-level `volumes` block:

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

The doubled dollar signs are required so Compose passes the variables to the container shell instead of interpolating them on the host.

- [ ] **Step 4: Validate rendered Compose defaults**

Run from `script/docker` with the three override variables absent:

```powershell
Remove-Item Env:MINIO_ROOT_USER,Env:MINIO_ROOT_PASSWORD,Env:MINIO_BUCKET -ErrorAction SilentlyContinue
$config = docker compose config --format json | ConvertFrom-Json
$init = $config.services.'minio-init'
if (-not $init) { throw 'minio-init service is missing' }
if ($config.services.minio.environment.MINIO_ROOT_USER -ne 'ruoyi') { throw 'default MinIO user is wrong' }
if ($config.services.minio.environment.MINIO_ROOT_PASSWORD -ne 'ruoyi123') { throw 'default MinIO password is wrong' }
if ($init.environment.MINIO_BUCKET -ne 'ruoyi') { throw 'default bucket is wrong' }
if (($init.command -join "`n") -notmatch 'anonymous set download') { throw 'download-only policy command is missing' }
```

Expected: PASS with exit code 0 and no output.

- [ ] **Step 5: Validate environment overrides in the rendered model**

Run:

```powershell
$env:MINIO_ROOT_USER = 'override-user'
$env:MINIO_ROOT_PASSWORD = 'override-password'
$env:MINIO_BUCKET = 'override-bucket'
$config = docker compose config --format json | ConvertFrom-Json
if ($config.services.minio.environment.MINIO_ROOT_USER -ne 'override-user') { throw 'user override failed' }
if ($config.services.minio.environment.MINIO_ROOT_PASSWORD -ne 'override-password') { throw 'password override failed' }
if ($config.services.'minio-init'.environment.MINIO_BUCKET -ne 'override-bucket') { throw 'bucket override failed' }
Remove-Item Env:MINIO_ROOT_USER,Env:MINIO_ROOT_PASSWORD,Env:MINIO_BUCKET
```

Expected: PASS with exit code 0 and no output.

- [ ] **Step 6: Commit only the Compose implementation**

```powershell
git add -- 'script/docker/docker-compose.yml'
git commit --only -m 'feat: initialize MinIO bucket access' -- 'script/docker/docker-compose.yml'
```

Expected: the commit contains only `script/docker/docker-compose.yml`.

### Task 3: Apply and Verify the Runtime Policy

**Files:**
- No repository file changes
- Test: current MinIO bucket, original uploaded object, temporary override bucket

**Interfaces:**
- Consumes: `minio-init` service from Task 2 and the running `minio` container.
- Produces: live `ruoyi` bucket with anonymous download access and verification evidence.

- [ ] **Step 1: Re-run the failing HTTP reproduction before applying the policy**

Run:

```powershell
$status = curl.exe -sS -o NUL -w '%{http_code}' 'http://127.0.0.1:9000/ruoyi/2026/08/07/68eaeba8fa52479a85d4afc08b59a43c.jpg'
if ($status -ne '403') { throw "expected pre-fix 403, got $status" }
```

Expected: PASS, confirming the pre-fix response is 403.

- [ ] **Step 2: Start the initializer through the normal Compose path**

Run from `script/docker`:

```powershell
docker compose up -d --force-recreate minio-init
if ($LASTEXITCODE -ne 0) { throw 'failed to start minio-init' }
docker wait minio-init | Out-Null
$exitCode = docker inspect minio-init --format '{{.State.ExitCode}}'
if ($exitCode -ne '0') { docker logs minio-init; throw "minio-init exited with $exitCode" }
```

Expected: `minio-init` exits with status 0 and reports `download` access.

- [ ] **Step 3: Verify the bucket policy and original object**

Run:

```powershell
$mcHost = 'http://ruoyi:ruoyi123@127.0.0.1:9000'
$policy = docker exec -e MC_HOST_local=$mcHost minio mc anonymous get local/ruoyi
if ($policy -notmatch 'download') { throw "unexpected policy: $policy" }
docker exec -e MC_HOST_local=$mcHost minio mc stat 'local/ruoyi/2026/08/07/68eaeba8fa52479a85d4afc08b59a43c.jpg'
if ($LASTEXITCODE -ne 0) { throw 'original object is missing' }
```

Expected: policy is `download`; the original JPEG still exists.

- [ ] **Step 4: Verify idempotency**

Repeat Step 2 exactly once more, then repeat the two assertions in Step 3.

Expected: the initializer again exits 0, the policy remains `download`, and the original object remains present.

- [ ] **Step 5: Verify the bucket-name override with a disposable empty bucket**

Run from `script/docker`:

```powershell
$testBucket = 'ruoyi-policy-test-' + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
docker compose run --rm -e MINIO_BUCKET=$testBucket minio-init
if ($LASTEXITCODE -ne 0) { throw 'override bucket initialization failed' }
$mcHost = 'http://ruoyi:ruoyi123@127.0.0.1:9000'
$policy = docker exec -e MC_HOST_local=$mcHost minio mc anonymous get "local/$testBucket"
if ($policy -notmatch 'download') { throw "override bucket policy is wrong: $policy" }
docker exec -e MC_HOST_local=$mcHost minio mc rb "local/$testBucket"
if ($LASTEXITCODE -ne 0) { throw 'failed to remove empty verification bucket' }
```

Expected: the temporary bucket receives `download` policy and is then removed while empty.

- [ ] **Step 6: Verify the original browser URL now succeeds**

Run:

```powershell
$headers = curl.exe -sS -D - -o NUL 'http://127.0.0.1:9000/ruoyi/2026/08/07/68eaeba8fa52479a85d4afc08b59a43c.jpg'
if ($headers -notmatch 'HTTP/1.1 200 OK') { throw "missing 200 response: $headers" }
if ($headers -notmatch '(?im)^Content-Type: image/jpeg') { throw "wrong content type: $headers" }
```

Expected: HTTP 200 with `Content-Type: image/jpeg`.

- [ ] **Step 7: Run final source and Compose checks**

Run from the repository root:

```powershell
git diff --check HEAD~2..HEAD -- '.gitignore' 'script/docker/.env.example' 'script/docker/docker-compose.yml'
Push-Location 'script/docker'
docker compose config --quiet
$composeExit = $LASTEXITCODE
Pop-Location
if ($composeExit -ne 0) { throw 'Compose validation failed' }
git status --short
```

Expected: no whitespace errors in this task's files, Compose validation exits 0, and the status output contains only the user's pre-existing unrelated worktree changes.
