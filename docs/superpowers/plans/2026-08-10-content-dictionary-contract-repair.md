# Content Dictionary Contract Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the article dictionary contract and wire `ruoyi-content` into the backend Maven reactor and application.

**Architecture:** Extend the existing shared `DictDataDTO` with the four fields already produced by `SysDictDataVo` and consumed by the article module. Keep the existing `BeanUtil.copyToList()` conversion, then make `ruoyi-content` a managed reactor module and a runtime dependency of `ruoyi-admin`.

**Tech Stack:** Java 17 bytecode on JDK 21, Spring Boot 3.5, Lombok, Hutool BeanUtil, JUnit 5, Maven 3.9.

## Global Constraints

- Backend only; do not modify `plus-ui`.
- Work only on `company-website-backend`; do not apply this repair to `5.X` without a separate request.
- Preserve the current unrelated OSS working-tree changes.
- Do not change article database tables, dictionary semantics, or the existing `DictService` method signature.
- Use the existing current checkout because the user explicitly declined workspace isolation.

---

### Task 1: Restore the shared dictionary DTO contract

**Files:**
- Modify: `ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/domain/dto/DictDataDTO.java`
- Modify: `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/service/support/ContentArticleDictionaryServiceTest.java`

**Interfaces:**
- Consumes: `DictService#getDictData(String): List<DictDataDTO>` and `SysDictDataVo` properties copied by name.
- Produces: Lombok accessors for `Long dictCode`, `Integer dictSort`, `String cssClass`, and `String listClass`.

- [ ] **Step 1: Strengthen the existing consumer-visible test**

In `returnsStableOptionContractForFixedDictionaryType`, add the missing sort assertion:

```java
assertThat(option.getDictSort()).isEqualTo(2);
```

This test catches removal of any shared DTO field or failure to forward the dictionary sorting/display contract.

- [ ] **Step 2: Run the focused test and verify the broken contract**

Run from the repository root:

```powershell
& 'D:\IntelliJ IDEA 2026.2.0.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' `
  -f ruoyi-modules/ruoyi-content/pom.xml `
  '-DskipTests=false' `
  '-Dtest=ContentArticleDictionaryServiceTest' test
```

Expected: compilation fails in `ContentArticleDictionaryService` and its test because `DictDataDTO` does not provide the four required accessors.

- [ ] **Step 3: Add the minimal shared DTO fields**

Add the following properties to `DictDataDTO`, preserving the order used by `SysDictDataVo`:

```java
/** 字典编码 */
private Long dictCode;

/** 字典排序 */
private Integer dictSort;

/** 样式属性（其他样式扩展） */
private String cssClass;

/** 表格回显样式 */
private String listClass;
```

Do not add manual accessors; the existing Lombok `@Data` annotation owns them.

### Task 2: Wire the article module into the backend build

**Files:**
- Modify: `pom.xml`
- Modify: `ruoyi-modules/pom.xml`
- Modify: `ruoyi-admin/pom.xml`

**Interfaces:**
- Consumes: Maven artifact `org.dromara:ruoyi-content:${revision}`.
- Produces: a reactor module built by `-pl ruoyi-modules/ruoyi-content -am` and included on the `ruoyi-admin` runtime classpath.

- [ ] **Step 1: Manage the module version in the root POM**

After the existing `ruoyi-generator` dependency-management entry, add:

```xml
<dependency>
    <groupId>org.dromara</groupId>
    <artifactId>ruoyi-content</artifactId>
    <version>${revision}</version>
</dependency>
```

- [ ] **Step 2: Add the module to the business-module reactor**

Add this module next to `ruoyi-system` in `ruoyi-modules/pom.xml`:

```xml
<module>ruoyi-content</module>
```

- [ ] **Step 3: Add the article module to the backend application**

After the `ruoyi-system` dependency in `ruoyi-admin/pom.xml`, add:

```xml
<dependency>
    <groupId>org.dromara</groupId>
    <artifactId>ruoyi-content</artifactId>
</dependency>
```

- [ ] **Step 4: Run the focused test through the repaired reactor**

```powershell
& 'D:\IntelliJ IDEA 2026.2.0.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' `
  -pl ruoyi-modules/ruoyi-content -am `
  '-DskipTests=false' `
  '-Dtest=ContentArticleDictionaryServiceTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: `ContentArticleDictionaryServiceTest` runs five tests with zero failures and the reactor includes `ruoyi-content`.

### Task 3: Verify and commit the repair

**Files:**
- Verify the five implementation/test files from Tasks 1 and 2.

**Interfaces:**
- Consumes: repaired DTO and Maven module wiring.
- Produces: fresh build evidence and one path-limited repair commit.

- [ ] **Step 1: Run all article-module tests**

```powershell
& 'D:\IntelliJ IDEA 2026.2.0.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' `
  -pl ruoyi-modules/ruoyi-content -am '-DskipTests=false' test
```

Expected: all `ruoyi-content` tests execute with zero failures and zero errors.

- [ ] **Step 2: Build the backend application with dependencies**

```powershell
& 'D:\IntelliJ IDEA 2026.2.0.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' `
  -pl ruoyi-admin -am '-DskipTests=false' package
```

Expected: the reactor includes `ruoyi-content` and `ruoyi-admin`, ending with `BUILD SUCCESS`.

- [ ] **Step 3: Review only the repair diff**

```powershell
git diff --check -- pom.xml ruoyi-admin/pom.xml ruoyi-modules/pom.xml `
  ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/domain/dto/DictDataDTO.java `
  ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/service/support/ContentArticleDictionaryServiceTest.java
git status --short
```

Confirm unrelated OSS changes remain untouched.

- [ ] **Step 4: Commit only the repair files**

```powershell
git commit --only -m "fix: complete content dictionary module contract" -- `
  pom.xml `
  ruoyi-admin/pom.xml `
  ruoyi-modules/pom.xml `
  ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/domain/dto/DictDataDTO.java `
  ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/service/support/ContentArticleDictionaryServiceTest.java
```

Do not push or cherry-pick to another branch unless explicitly requested.
