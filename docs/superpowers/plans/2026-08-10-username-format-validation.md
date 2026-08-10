# Username Format Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restrict user-management usernames to 2–20 ASCII letters or digits while allowing mixed case and numeric-only usernames.

**Architecture:** Jakarta Bean Validation on `SysUserBo.userName` is the single enforcement boundary for this backend-only change. A focused validator test proves accepted and rejected values without starting the Spring application.

**Tech Stack:** Java 21, Spring Boot 3, Jakarta Bean Validation, JUnit 5, AssertJ, Maven.

## Global Constraints

- Usernames contain only `A-Z`, `a-z`, and `0-9`.
- Username length is 2–20 characters.
- Numeric-only usernames are valid.
- User nicknames remain unchanged and may contain Chinese characters.
- Frontend files are outside this change.
- Registration, tenant initialization, and user import entry points are outside this change.
- Preserve all unrelated staged and working-tree changes.
- Deliver the same backend code commit to `company-website-backend` and the repository's mainline branch `5.X`.

---

### Task 1: Backend username validation

**Files:**
- Modify: `ruoyi-modules/ruoyi-system/pom.xml`
- Modify: `ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/domain/bo/SysUserBo.java:3-43`
- Create: `ruoyi-modules/ruoyi-system/src/test/java/org/dromara/system/domain/bo/SysUserBoValidationTest.java`

**Interfaces:**
- Consumes: `jakarta.validation.Validator` and the existing `SysUserBo.userName` property.
- Produces: Bean Validation constraints equivalent to `^[A-Za-z0-9]{2,20}$` for `SysUserBo.userName`.

- [ ] **Step 1: Add the system module test dependency and failing validation tests**

Add the following test dependency to `ruoyi-system/pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

Create `SysUserBoValidationTest.java`:

```java
package org.dromara.system.domain.bo;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SysUserBoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ZhangSan", "zhangsan01", "13800138000", "ABC123"})
    void acceptsAsciiLettersAndDigits(String userName) {
        assertThat(userNameViolations(userName)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"张三", "abc_123", "abc-123", "abc 123"})
    void rejectsCharactersOutsideAsciiLettersAndDigits(String userName) {
        assertThat(userNameViolations(userName)).isNotEmpty();
    }

    @Test
    void rejectsUsernameShorterThanTwoCharacters() {
        assertThat(userNameViolations("a")).isNotEmpty();
    }

    @Test
    void rejectsUsernameLongerThanTwentyCharacters() {
        assertThat(userNameViolations("Abcdefghij12345678901")).isNotEmpty();
    }

    private static List<String> userNameViolations(String userName) {
        SysUserBo user = new SysUserBo();
        user.setUserName(userName);
        user.setNickName("测试用户");
        user.setRoleIds(new Long[]{1L});
        return validator.validate(user).stream()
            .filter(violation -> "userName".equals(violation.getPropertyPath().toString()))
            .map(violation -> violation.getMessage())
            .toList();
    }
}
```

- [ ] **Step 2: Run the backend test and confirm the new behavior is missing**

Run from `C:\Users\dj\ruoyi-plus\simple\RuoYi-Vue-Plus`:

```powershell
& 'D:\IntelliJ IDEA 2026.2.0.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' `
  -pl ruoyi-modules/ruoyi-system -am `
  -DskipTests=false `
  -Dtest=SysUserBoValidationTest `
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because Chinese and symbol-containing usernames currently have no `userName` violation, and a 21-character username is still allowed by the current maximum of 30.

- [ ] **Step 3: Implement the minimal backend constraints**

Import `Pattern` and update the annotations on `userName`:

```java
import jakarta.validation.constraints.Pattern;

@Xss(message = "用户账号不能包含脚本字符")
@NotBlank(message = "用户账号不能为空")
@Size(min = 2, max = 20, message = "用户账号长度必须在{min}到{max}个字符之间")
@Pattern(regexp = "^[A-Za-z0-9]+$", message = "用户账号只能包含英文字母和数字")
private String userName;
```

- [ ] **Step 4: Run the backend test and confirm it passes**

Run the same Maven command from Step 2.

Expected: `SysUserBoValidationTest` passes with zero failures.

- [ ] **Step 5: Commit only the backend validation files**

```powershell
git commit --only -m "feat: validate user account format" -- `
  ruoyi-modules/ruoyi-system/pom.xml `
  ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/domain/bo/SysUserBo.java `
  ruoyi-modules/ruoyi-system/src/test/java/org/dromara/system/domain/bo/SysUserBoValidationTest.java
```

This path-limited commit must not include the repository's pre-existing staged SQL deletions or unrelated content-module changes.

### Task 2: Backend verification

**Files:**
- Verify only; no new files.

**Interfaces:**
- Consumes: the backend Bean Validation annotations.
- Produces: fresh evidence that the backend module builds and the current database remains compatible.

- [ ] **Step 1: Run backend module tests**

```powershell
& 'D:\IntelliJ IDEA 2026.2.0.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' `
  -pl ruoyi-modules/ruoyi-system -am -DskipTests=false test
```

Expected: Maven exits with `BUILD SUCCESS` and zero test failures.

- [ ] **Step 2: Recheck active username compatibility**

```powershell
docker exec postgres psql -U postgres -d ry_vue -P pager=off -c `
  "select count(*) filter (where user_name !~ '^[A-Za-z0-9]{2,20}$') as invalid_existing_names from sys_user where del_flag = '0';"
```

Expected: `invalid_existing_names` is `0`.

- [ ] **Step 3: Review diff and unrelated workspace state**

```powershell
git diff --check
git status --short
```

Confirm the username code changes are limited to the files listed above and that unrelated pre-existing changes remain untouched.

### Task 3: Apply the backend commit to the mainline branch

**Files:**
- Apply the exact backend validation commit from `company-website-backend` to `5.X`.

- [ ] **Step 1: Create a temporary clean worktree for `5.X`**

The current company-branch checkout contains unrelated staged and working-tree changes, so do not switch it in place.

- [ ] **Step 2: Cherry-pick the backend validation commit**

Cherry-pick only the backend code commit produced in Task 1. Resolve conflicts only within the three username-validation files if necessary.

- [ ] **Step 3: Run the same backend module tests on `5.X`**

Run the Task 2 Maven command from the temporary `5.X` worktree and require `BUILD SUCCESS`.

- [ ] **Step 4: Verify branch commits and remove the temporary worktree**

Confirm both branches contain the same backend patch, then remove the clean temporary worktree without changing the original company-branch checkout.
