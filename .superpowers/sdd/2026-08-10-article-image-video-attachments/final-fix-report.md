# Final blocking fix report — 2026-08-11

## Result

- Undertow now binds `server.undertow.max-http-post-size` to `2200MB`, aligned with Spring multipart request and Nginx limits and above 2 GiB.
- Added anonymous `GET /content/article/public/{articleId}/media`. It uses the fixed-tenant published/not-deleted query path, takes no OSS IDs, intersects current OSS results with ordered article relations, and returns only sanitized display metadata with refreshed private URLs. Public list/detail remain IDs-only.
- Business media removal now locks exact tenant-owned `sys_oss` rows and writes `PENDING` plus a unique token in the caller transaction. Rollback registers no deletion. After commit, a `REQUIRES_NEW` worker locks and rechecks the exact token, deletes the object, then deletes the exact metadata row. Failures retain pending metadata for a scheduled 100-row retry batch. Pending rows are not returned or bindable; direct OSS deletion and temporary cleanup remain separate.
- Updated `docs/frontend/article-media-integration.md` for the public resolver contract and post-commit physical deletion timing. No frontend files changed.

## Behavioral evidence

- RED: scoped content tests initially failed on the missing media API/VO; system tests failed on the missing coordinator/worker/retrier; the corrected Undertow binding test reported `1073741824B` when mutated back to `1GB`; the fresh-URL test failed before `resolveByIds` existed.
- Focused GREEN: system deletion/OSS tests 26/26; content resolver/attachment tests 36/36; Undertow binding 1/1.
- Final direct module suites: `ruoyi-system` 74/74 and `ruoyi-content` 140/140, with zero failures, errors, or skips.
- Repository check: `git diff --check` passed.

## Commands

```text
mvn -pl ruoyi-modules/ruoyi-system -DskipTests=false test
mvn -pl ruoyi-modules/ruoyi-content -DskipTests=false test
mvn -pl ruoyi-admin -Dtest=UndertowUploadLimitBindingTest -DskipTests=false test
git diff --check
```
