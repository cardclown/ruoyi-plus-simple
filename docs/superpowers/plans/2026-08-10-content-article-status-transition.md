# 文章发布与撤回接口实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 新增只接收文章 ID 和状态的发布/撤回接口，并禁止已发布文章绕过撤回流程直接普通编辑。

**架构：** 使用独立 `ContentArticleStatusBo` 承载 HTTP 请求，Controller 复用文章修改权限，Service 负责状态机和发布审计字段，Mapper 通过显式 SQL 只更新状态及审计列。普通修改在准备新内容和重建标签前拒绝已发布文章。

**技术栈：** Java 17、Spring Boot 3、Spring MVC、Jakarta Validation、Sa-Token、MyBatis-Plus、MyBatis XML、JUnit 5、AssertJ、Mockito。

## 全局约束

- 设计依据：`docs/superpowers/specs/2026-08-10-content-article-status-transition-design.md`。
- HTTP 契约固定为 `POST /content/article/changeStatus`，JSON 请求只声明 `articleId` 和 `status`。
- 状态只允许字符串 `0` 和 `1`；`0 -> 0` 直接成功，`0 -> 1` 发布，`1 -> 0` 撤回，`1 -> 1` 拒绝。
- 普通修改允许 `0 -> 0` 和 `0 -> 1`；数据库当前为 `1` 时一律拒绝普通修改。
- 状态更新不得修改标题、正文、分类、封面、标签和删除标志，不得调用标签删除或新增方法。
- 权限固定复用 `content:article:edit`，不新增菜单 SQL、表结构或依赖。
- 所有 Git 提交信息使用中文，实现分支固定为 `company-website-backend`，不向 `5.X` 提交。
- 暂存区必须继续保留恰好 40 个 `script/sql/update` 删除，禁止 `git add .`、禁止将这 40 个删除带入功能提交。
- `ContentArticleMapper.java`、`ContentArticleMapper.xml` 和当前 Mapper 契约测试属于已有未跟踪业务工作；实施前为它们创建精确基线快照，修改后仍保持未暂存、未提交。
- 新建且完全属于本功能的 `ContentArticleStatusBo` 及其测试可以精确提交；已跟踪 Controller、Service 和测试只使用精确路径提交。
- 当前根 POM 未注册 `ruoyi-content` 模块，所有测试使用 `mvn.cmd -f ruoyi-modules/ruoyi-content/pom.xml` 直接执行。

---

## 文件结构与职责

- 新建 `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/bo/ContentArticleStatusBo.java`：仅声明状态接口的 ID 和状态契约。
- 修改 `ContentArticleController.java`：暴露 `POST /changeStatus`，只做 HTTP 绑定、校验、权限、日志和 Service 调用。
- 修改 `IContentArticleService.java`：新增 `Boolean changeStatus(Long articleId, String status)`。
- 修改 `ContentArticleServiceImpl.java`：实现状态机，复用 `ContentArticlePublishPolicy`，并限制已发布文章的普通修改。
- 修改 `ContentArticleMapper.java` 和 `ContentArticleMapper.xml`：使用带 `@DataPermission` 的局部更新 SQL，避免 `FieldStrategy.ALWAYS` 把无关列置空。
- 新建/修改契约与单元测试：分别锁定 BO 校验、Mapper SQL 边界、Service 状态机、普通修改限制和 Controller 路由契约。

---

### Task 1：新增独立状态请求对象

**Files:**
- Create: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/bo/ContentArticleStatusBo.java`
- Create: `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/domain/bo/ContentArticleStatusBoValidationTest.java`

**Interfaces:**
- Consumes: HTTP JSON 字段 `articleId` 和 `status`。
- Produces: `ContentArticleStatusBo#getArticleId(): Long`，`ContentArticleStatusBo#getStatus(): String`。

- [ ] **Step 1：编写失败的 BO 校验测试**

```java
package org.dromara.content.domain.bo;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ContentArticleStatusBoValidationTest {
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsIdAndPublishedStatus() {
        ContentArticleStatusBo bo = statusBo(100L, "1");
        assertThat(validator.validate(bo)).isEmpty();
    }

    @Test
    void rejectsMissingArticleId() {
        assertThat(validator.validate(statusBo(null, "1")))
            .anyMatch(v -> "articleId".equals(v.getPropertyPath().toString()));
    }

    @Test
    void rejectsMissingStatus() {
        assertThat(validator.validate(statusBo(100L, null)))
            .anyMatch(v -> "status".equals(v.getPropertyPath().toString()));
    }

    @Test
    void rejectsUnknownStatus() {
        assertThat(validator.validate(statusBo(100L, "2")))
            .anyMatch(v -> "status".equals(v.getPropertyPath().toString()));
    }

    private static ContentArticleStatusBo statusBo(Long articleId, String status) {
        ContentArticleStatusBo bo = new ContentArticleStatusBo();
        bo.setArticleId(articleId);
        bo.setStatus(status);
        return bo;
    }
}
```

- [ ] **Step 2：运行测试并确认因类不存在而失败**

Run:

```powershell
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' `
  -f ruoyi-modules/ruoyi-content/pom.xml `
  '-DskipTests=false' '-Dtest=ContentArticleStatusBoValidationTest' test
```

Expected: FAIL，编译错误指向 `ContentArticleStatusBo` 不存在。

- [ ] **Step 3：实现最小状态 BO**

```java
package org.dromara.content.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 文章发布状态修改请求。
 */
@Data
public class ContentArticleStatusBo {
    @NotNull(message = "文章ID不能为空")
    private Long articleId;

    @NotBlank(message = "发布状态不能为空")
    @Pattern(regexp = "^[01]$", message = "发布状态只能为0或1")
    private String status;
}
```

- [ ] **Step 4：运行 BO 测试并确认通过**

Run:

```powershell
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' `
  -f ruoyi-modules/ruoyi-content/pom.xml `
  '-DskipTests=false' '-Dtest=ContentArticleStatusBoValidationTest' test
```

Expected: 4 tests，0 failures，`BUILD SUCCESS`。

- [ ] **Step 5：精确提交新建 BO 和测试**

```powershell
git add -- `
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/bo/ContentArticleStatusBo.java' `
  'ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/domain/bo/ContentArticleStatusBoValidationTest.java'
git commit --only -m '功能：新增文章状态请求对象' -- `
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/bo/ContentArticleStatusBo.java' `
  'ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/domain/bo/ContentArticleStatusBoValidationTest.java'
```

---

### Task 2：新增数据权限下的局部状态更新

**Files:**
- Modify, keep untracked: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/mapper/ContentArticleMapper.java`
- Modify, keep untracked: `ruoyi-modules/ruoyi-content/src/main/resources/mapper/content/ContentArticleMapper.xml`
- Modify, keep untracked: `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/mapper/ContentArticleMapperContractTest.java`
- Create, keep untracked: `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/mapper/ContentArticleMapperStatusSqlContractTest.java`

**Interfaces:**
- Consumes: 只填充 `articleId/status/publishBy/publishTime/updateBy/updateTime` 的 `ContentArticle`。
- Produces: `int ContentArticleMapper.updateArticleStatus(ContentArticle article)`。

- [ ] **Step 1：为三个已有未跟踪文件创建基线快照**

Create the ignored task workspace and copy the exact current files before editing:

```powershell
$snapshot = '.superpowers\sdd\2026-08-10-content-article-status\baseline'
New-Item -ItemType Directory -Force -Path $snapshot | Out-Null
Copy-Item -LiteralPath 'ruoyi-modules\ruoyi-content\src\main\java\org\dromara\content\mapper\ContentArticleMapper.java' -Destination (Join-Path $snapshot 'ContentArticleMapper.java')
Copy-Item -LiteralPath 'ruoyi-modules\ruoyi-content\src\main\resources\mapper\content\ContentArticleMapper.xml' -Destination (Join-Path $snapshot 'ContentArticleMapper.xml')
Copy-Item -LiteralPath 'ruoyi-modules\ruoyi-content\src\test\java\org\dromara\content\mapper\ContentArticleMapperContractTest.java' -Destination (Join-Path $snapshot 'ContentArticleMapperContractTest.java')
```

- [ ] **Step 2：先扩展 Mapper 权限契约测试**

在 `allArticleReadAndWriteEntryPointsApplyCreatorDataPermission` 的方法名数组中增加：

```java
"updateArticleStatus",
```

新建 SQL 契约测试：

```java
package org.dromara.content.mapper;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ContentArticleMapperStatusSqlContractTest {
    @Test
    void statusUpdateOnlyChangesStatusAndAuditColumns() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/mapper/content/ContentArticleMapper.xml")) {
            assertThat(input).isNotNull();
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            int start = xml.indexOf("<update id=\"updateArticleStatus\">");
            int end = xml.indexOf("</update>", start);
            assertThat(start).isGreaterThanOrEqualTo(0);
            String statement = xml.substring(start, end);
            assertThat(statement).contains(
                "status = #{article.status}",
                "publish_by = #{article.publishBy",
                "publish_time = #{article.publishTime",
                "update_by = #{article.updateBy",
                "update_time = #{article.updateTime"
            );
            assertThat(statement).doesNotContain(
                "title =", "content =", "category_dict_code =",
                "tag_ids =", "cover_oss_id =", "del_flag ="
            );
        }
    }
}
```

- [ ] **Step 3：运行 Mapper 契约测试并确认因方法/SQL 不存在而失败**

```powershell
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' `
  -f ruoyi-modules/ruoyi-content/pom.xml `
  '-DskipTests=false' '-Dtest=ContentArticleMapperContractTest,ContentArticleMapperStatusSqlContractTest' test
```

Expected: FAIL，一条失败指向 `updateArticleStatus` 方法不存在，另一条失败指向 XML 中缺少对应 `<update>`。

- [ ] **Step 4：实现 Mapper 方法和显式局部 SQL**

在 `ContentArticleMapper` 中新增：

```java
/**
 * 仅更新文章状态与发布/更新审计字段。
 *
 * @param article 包含文章 ID、目标状态和审计字段的部分实体
 * @return 受影响的记录数
 */
@DataPermission({
    @DataColumn(key = "deptName", value = "create_dept"),
    @DataColumn(key = "userName", value = "create_by")
})
int updateArticleStatus(@Param("article") ContentArticle article);
```

在 `ContentArticleMapper.xml` 中新增：

```xml
<update id="updateArticleStatus">
    update content_article
    set status = #{article.status},
        publish_by = #{article.publishBy,jdbcType=BIGINT},
        publish_time = #{article.publishTime,jdbcType=TIMESTAMP},
        update_by = #{article.updateBy,jdbcType=BIGINT},
        update_time = #{article.updateTime,jdbcType=TIMESTAMP}
    where article_id = #{article.articleId}
      and del_flag = '0'
</update>
```

- [ ] **Step 5：运行 Mapper 契约测试并确认通过**

Run:

```powershell
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' `
  -f ruoyi-modules/ruoyi-content/pom.xml `
  '-DskipTests=false' '-Dtest=ContentArticleMapperContractTest,ContentArticleMapperStatusSqlContractTest' test
```

Expected: 2 tests，0 failures，`BUILD SUCCESS`。

- [ ] **Step 6：检查未跟踪文件保护，本任务不提交**

```powershell
git status --short -- `
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/mapper/ContentArticleMapper.java' `
  'ruoyi-modules/ruoyi-content/src/main/resources/mapper/content/ContentArticleMapper.xml' `
  'ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/mapper/ContentArticleMapperContractTest.java' `
  'ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/mapper/ContentArticleMapperStatusSqlContractTest.java'
```

Expected: 四个文件都是 `??`，且 `git diff --cached --name-only` 不包含它们。

---

### Task 3：实现状态机和发布/撤回服务

**Files:**
- Modify: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/IContentArticleService.java`
- Modify: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/impl/ContentArticleServiceImpl.java`
- Modify: `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/service/impl/ContentArticleServiceImplTest.java`

**Interfaces:**
- Consumes: `ContentArticleMapper.selectArticleById(Long)`、`ContentArticleMapper.updateArticleStatus(ContentArticle)`、`ContentArticlePublishPolicy`、`ContentArticleOperationContext`。
- Produces: `Boolean IContentArticleService.changeStatus(Long articleId, String status)`。

- [ ] **Step 1：编写状态机失败测试**

在 `ContentArticleServiceImplTest` 增加以下测试，并引入 `verifyNoInteractions`：

```java
@Test
void draftToDraftIsSuccessfulWithoutWriting() {
    when(articleMapper.selectArticleById(100L)).thenReturn(persistedArticle());

    assertThat(service.changeStatus(100L, "0")).isTrue();

    verify(articleMapper, never()).updateArticleStatus(any(ContentArticle.class));
    verifyNoInteractions(tagMapper);
}

@Test
void draftToPublishedWritesCurrentPublisherAndTime() {
    when(articleMapper.selectArticleById(100L)).thenReturn(persistedArticle());
    when(articleMapper.updateArticleStatus(any(ContentArticle.class))).thenReturn(1);

    assertThat(service.changeStatus(100L, "1")).isTrue();

    ArgumentCaptor<ContentArticle> captor = ArgumentCaptor.forClass(ContentArticle.class);
    verify(articleMapper).updateArticleStatus(captor.capture());
    assertThat(captor.getValue().getArticleId()).isEqualTo(100L);
    assertThat(captor.getValue().getStatus()).isEqualTo("1");
    assertThat(captor.getValue().getPublishBy()).isEqualTo(9L);
    assertThat(captor.getValue().getPublishTime()).isEqualTo(new Date(1_000L));
    assertThat(captor.getValue().getUpdateBy()).isEqualTo(9L);
    assertThat(captor.getValue().getUpdateTime()).isEqualTo(new Date(1_000L));
    verifyNoInteractions(tagMapper);
}

@Test
void publishedToDraftClearsPublisherAndTime() {
    ContentArticle persisted = persistedArticle();
    persisted.setStatus("1");
    persisted.setPublishBy(8L);
    persisted.setPublishTime(new Date(500L));
    when(articleMapper.selectArticleById(100L)).thenReturn(persisted);
    when(articleMapper.updateArticleStatus(any(ContentArticle.class))).thenReturn(1);

    assertThat(service.changeStatus(100L, "0")).isTrue();

    ArgumentCaptor<ContentArticle> captor = ArgumentCaptor.forClass(ContentArticle.class);
    verify(articleMapper).updateArticleStatus(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo("0");
    assertThat(captor.getValue().getPublishBy()).isNull();
    assertThat(captor.getValue().getPublishTime()).isNull();
    verifyNoInteractions(tagMapper);
}

@Test
void publishedToPublishedRequiresWithdrawalFirst() {
    ContentArticle persisted = persistedArticle();
    persisted.setStatus("1");
    when(articleMapper.selectArticleById(100L)).thenReturn(persisted);

    assertThatThrownBy(() -> service.changeStatus(100L, "1"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("文章已发布，请先撤回后再发布");

    verify(articleMapper, never()).updateArticleStatus(any(ContentArticle.class));
    verifyNoInteractions(tagMapper);
}

@Test
void changeStatusRejectsUnknownStatusBeforeReadingArticle() {
    assertThatThrownBy(() -> service.changeStatus(100L, "2"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("发布状态只能为0或1");

    verify(articleMapper, never()).selectArticleById(any());
}

@Test
void changeStatusRejectsMissingOrUnauthorizedArticle() {
    when(articleMapper.selectArticleById(100L)).thenReturn(null);

    assertThatThrownBy(() -> service.changeStatus(100L, "1"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("文章不存在或无权操作");
}

@Test
void changeStatusFailsWhenAffectedRowsDoNotMatch() {
    when(articleMapper.selectArticleById(100L)).thenReturn(persistedArticle());
    when(articleMapper.updateArticleStatus(any(ContentArticle.class))).thenReturn(0);

    assertThatThrownBy(() -> service.changeStatus(100L, "1"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("文章状态修改失败");
}
```

在 `allWriteMethodsDeclareRollbackForException` 中增加：

```java
assertTransactional("changeStatus", Long.class, String.class);
```

- [ ] **Step 2：运行 Service 测试并确认因方法缺失而失败**

```powershell
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' `
  -f ruoyi-modules/ruoyi-content/pom.xml `
  '-DskipTests=false' '-Dtest=ContentArticleServiceImplTest' test
```

Expected: FAIL，编译错误指向 `changeStatus` 和 `updateArticleStatus` 契约尚未实现。

- [ ] **Step 3：在 Service 接口和实现中增加状态修改**

`IContentArticleService` 新增：

```java
/**
 * 修改文章发布状态。
 *
 * @param articleId 文章 ID
 * @param status 目标状态：0 草稿，1 已发布
 * @return 状态修改结果
 */
Boolean changeStatus(Long articleId, String status);
```

`ContentArticleServiceImpl` 新增：

```java
@Override
@Transactional(rollbackFor = Exception.class)
public Boolean changeStatus(Long articleId, String status) {
    validateStatus(status);
    ContentArticle persisted = articleMapper.selectArticleById(articleId);
    if (persisted == null) {
        throw new ServiceException("文章不存在或无权操作");
    }
    if ("1".equals(persisted.getStatus()) && "1".equals(status)) {
        throw new ServiceException("文章已发布，请先撤回后再发布");
    }
    if ("0".equals(persisted.getStatus()) && "0".equals(status)) {
        return true;
    }

    Long currentUserId = operationContext.currentUserId();
    Date now = operationContext.now();
    ContentArticle update = new ContentArticle();
    update.setArticleId(articleId);
    update.setStatus(status);
    publishPolicy.applyForUpdate(update, persisted, currentUserId, now);
    update.setUpdateBy(currentUserId);
    update.setUpdateTime(now);
    if (articleMapper.updateArticleStatus(update) != 1) {
        throw new ServiceException("文章状态修改失败");
    }
    return true;
}
```

- [ ] **Step 4：运行 Service 测试并确认通过**

Run:

```powershell
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' `
  -f ruoyi-modules/ruoyi-content/pom.xml `
  '-DskipTests=false' '-Dtest=ContentArticleServiceImplTest' test
```

Expected: 新增 7 个状态测试和既有 Service 测试全部通过。

- [ ] **Step 5：精确提交已跟踪的 Service 文件**

```powershell
git commit --only -m '功能：实现文章发布与撤回' -- `
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/IContentArticleService.java' `
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/impl/ContentArticleServiceImpl.java' `
  'ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/service/impl/ContentArticleServiceImplTest.java'
```

---

### Task 4：禁止直接修改已发布文章

**Files:**
- Modify: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/impl/ContentArticleServiceImpl.java`
- Modify: `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/service/impl/ContentArticleServiceImplTest.java`

**Interfaces:**
- Consumes: `ContentArticleServiceImpl.updateByBo(ContentArticleBo)` 已读取的持久化文章。
- Produces: 已发布文章普通修改的稳定拒绝契约。

- [ ] **Step 1：编写普通修改限制的失败测试**

```java
@Test
void updateRejectsPublishedArticleEvenWhenRequestWithdrawsIt() {
    ContentArticle persisted = persistedArticle();
    persisted.setStatus("1");
    ContentArticleBo bo = articleBo();
    bo.setArticleId(100L);
    bo.setStatus("0");
    when(articleMapper.selectArticleById(100L)).thenReturn(persisted);

    assertThatThrownBy(() -> service.updateByBo(bo))
        .isInstanceOf(ServiceException.class)
        .hasMessage("已发布文章请先撤回为草稿后再修改");

    verify(articleMapper, never()).updateArticleById(any(ContentArticle.class));
    verify(tagMapper, never()).deleteByArticleId(any());
}

@Test
void updateRejectsPublishedArticleThatRemainsPublished() {
    ContentArticle persisted = persistedArticle();
    persisted.setStatus("1");
    ContentArticleBo bo = articleBo();
    bo.setArticleId(100L);
    bo.setStatus("1");
    when(articleMapper.selectArticleById(100L)).thenReturn(persisted);

    assertThatThrownBy(() -> service.updateByBo(bo))
        .isInstanceOf(ServiceException.class)
        .hasMessage("已发布文章请先撤回为草稿后再修改");
}

@Test
void updateAllowsDraftToBeSavedAndPublished() {
    ContentArticleBo bo = articleBo();
    bo.setArticleId(100L);
    bo.setStatus("1");
    when(articleMapper.selectArticleById(100L)).thenReturn(persistedArticle());
    when(dictionaryService.validateAndNormalize(11L, null)).thenReturn(List.of());
    when(articleMapper.updateArticleById(any(ContentArticle.class))).thenReturn(1);

    assertThat(service.updateByBo(bo)).isTrue();

    ArgumentCaptor<ContentArticle> captor = ArgumentCaptor.forClass(ContentArticle.class);
    verify(articleMapper).updateArticleById(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo("1");
    assertThat(captor.getValue().getPublishBy()).isEqualTo(9L);
    assertThat(captor.getValue().getPublishTime()).isEqualTo(new Date(1_000L));
}
```

- [ ] **Step 2：运行 Service 测试并确认已发布限制用例失败**

Run:

```powershell
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' `
  -f ruoyi-modules/ruoyi-content/pom.xml `
  '-DskipTests=false' '-Dtest=ContentArticleServiceImplTest' test
```

Expected: 已发布文章的两个拒绝用例 FAIL；草稿保存并发布用例保持 PASS。

- [ ] **Step 3：在任何准备或写入前拒绝已发布文章**

在 `updateByBo` 的空值检查后立即新增：

```java
if ("1".equals(persisted.getStatus())) {
    throw new ServiceException("已发布文章请先撤回为草稿后再修改");
}
```

- [ ] **Step 4：运行 Service 测试并确认全部通过**

Run:

```powershell
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' `
  -f ruoyi-modules/ruoyi-content/pom.xml `
  '-DskipTests=false' '-Dtest=ContentArticleServiceImplTest' test
```

Expected: 全部 Service 测试通过，0 failures。

- [ ] **Step 5：精确提交普通修改限制**

```powershell
git commit --only -m '功能：限制已发布文章直接修改' -- `
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/impl/ContentArticleServiceImpl.java' `
  'ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/service/impl/ContentArticleServiceImplTest.java'
```

---

### Task 5：暴露 POST 状态修改接口

**Files:**
- Modify: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/controller/ContentArticleController.java`
- Modify: `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/controller/ContentArticleControllerContractTest.java`

**Interfaces:**
- Consumes: `ContentArticleStatusBo`、`IContentArticleService.changeStatus(Long, String)`。
- Produces: `POST /content/article/changeStatus`，权限 `content:article:edit`。

- [ ] **Step 1：编写控制器契约失败测试**

在 `ContentArticleControllerContractTest` 增加必要 import 和测试：

```java
@Test
void exposesValidatedPostStatusChangeEndpoint() throws Exception {
    Method method = ContentArticleController.class.getDeclaredMethod(
        "changeStatus", ContentArticleStatusBo.class);

    PostMapping mapping = method.getAnnotation(PostMapping.class);
    assertThat(mapping).isNotNull();
    assertThat(mapping.value()).containsExactly("/changeStatus");

    SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
    assertThat(permission).isNotNull();
    assertThat(permission.value()).containsExactly("content:article:edit");

    Log log = method.getAnnotation(Log.class);
    assertThat(log).isNotNull();
    assertThat(log.businessType()).isEqualTo(BusinessType.UPDATE);
    assertThat(method.getAnnotation(RepeatSubmit.class)).isNotNull();

    assertThat(method.getParameters()[0].getAnnotation(RequestBody.class)).isNotNull();
    Validated validated = method.getParameters()[0].getAnnotation(Validated.class);
    assertThat(validated).isNotNull();
    assertThat(validated.value()).isEmpty();
}
```

Required imports:

```java
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.content.domain.bo.ContentArticleStatusBo;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
```

- [ ] **Step 2：运行控制器契约测试并确认因端点缺失而失败**

```powershell
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' `
  -f ruoyi-modules/ruoyi-content/pom.xml `
  '-DskipTests=false' '-Dtest=ContentArticleControllerContractTest' test
```

Expected: FAIL，反射查找 `changeStatus(ContentArticleStatusBo)` 时抛出 `NoSuchMethodException`。

- [ ] **Step 3：实现 Controller 端点**

在 `ContentArticleController` 中引入 `ContentArticleStatusBo`，并在普通修改方法之后增加：

```java
/**
 * 发布或撤回文章，请求仅接收文章 ID 和目标状态。
 *
 * @param bo 文章状态修改请求
 * @return 状态修改结果
 */
@SaCheckPermission("content:article:edit")
@Log(title = "文章", businessType = BusinessType.UPDATE)
@RepeatSubmit
@PostMapping("/changeStatus")
public R<Void> changeStatus(@Validated @RequestBody ContentArticleStatusBo bo) {
    return toAjax(contentArticleService.changeStatus(bo.getArticleId(), bo.getStatus()));
}
```

- [ ] **Step 4：运行控制器契约测试并确认通过**

Run:

```powershell
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' `
  -f ruoyi-modules/ruoyi-content/pom.xml `
  '-DskipTests=false' '-Dtest=ContentArticleControllerContractTest' test
```

Expected: 全部 Controller 契约测试通过。

- [ ] **Step 5：精确提交 Controller 和契约测试**

```powershell
git commit --only -m '功能：新增文章状态修改接口' -- `
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/controller/ContentArticleController.java' `
  'ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/controller/ContentArticleControllerContractTest.java'
```

---

### Task 6：全量验证、审查和工作区保护

**Files:**
- Verify: `ruoyi-modules/ruoyi-content/src/main/java/**/*.java`
- Verify: `ruoyi-modules/ruoyi-content/src/main/resources/mapper/content/ContentArticleMapper.xml`
- Verify: `ruoyi-modules/ruoyi-content/src/test/java/**/*.java`
- Verify: `script/sql/update/**`
- Remove after review: `.superpowers/sdd/2026-08-10-content-article-status/`

**Interfaces:**
- Consumes: Tasks 1–5 的所有产品代码、测试和精确提交。
- Produces: 可交付的文章发布/撤回接口和完整验证证据。

- [ ] **Step 1：运行文章模块全量测试**

```powershell
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' `
  -f ruoyi-modules/ruoyi-content/pom.xml `
  '-DskipTests=false' test
```

Expected: 新增测试与原有 50 个测试全部通过，`BUILD SUCCESS`。

- [ ] **Step 2：检查格式、提交和暂存区保护**

```powershell
git diff --check
$staged = @(git diff --cached --name-status)
$sqlDeletes = @($staged | Where-Object { $_ -match '^D\s+script/sql/update/' })
$otherStaged = @($staged | Where-Object { $_ -notmatch 'script/sql/update/' })
"STAGED_TOTAL=$($staged.Count)"
"STAGED_SQL_DELETE=$($sqlDeletes.Count)"
"STAGED_OTHER=$($otherStaged.Count)"
git log -8 --oneline
```

Expected: 没有本任务引入的空白错误；暂存项恰好 40 个且全部为 `script/sql/update` 删除；其他暂存项为 0；本功能提交信息全部使用中文。

- [ ] **Step 3：比较未跟踪 Mapper 文件与基线**

```powershell
$snapshot = '.superpowers\sdd\2026-08-10-content-article-status\baseline'
git diff --no-index --word-diff=plain -- (Join-Path $snapshot 'ContentArticleMapper.java') 'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/mapper/ContentArticleMapper.java'
git diff --no-index --word-diff=plain -- (Join-Path $snapshot 'ContentArticleMapper.xml') 'ruoyi-modules/ruoyi-content/src/main/resources/mapper/content/ContentArticleMapper.xml'
git diff --no-index --word-diff=plain -- (Join-Path $snapshot 'ContentArticleMapperContractTest.java') 'ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/mapper/ContentArticleMapperContractTest.java'
```

Expected: 只包含 Task 2 规定的状态更新方法、XML SQL 和 Mapper 权限契约变化。

- [ ] **Step 4：发起最终只读代码审查**

使用 `superpowers:requesting-code-review`，审查必须确认：

- HTTP 方法、路径、权限和请求字段符合设计；
- 四种状态组合与普通编辑限制全部正确；
- 状态更新不会因 `FieldStrategy.ALWAYS` 清空标签或封面；
- 数据权限、租户隔离、发布人/时间和更新人/时间均正确；
- 状态接口不调用标签 Mapper；
- 现有未跟踪业务文件和 40 个 SQL 删除没有进入功能提交。

- [ ] **Step 5：审查通过后清理本任务快照**

先解析并确认目标位于 `.superpowers/sdd` 下，再删除仅本任务的 `.superpowers/sdd/2026-08-10-content-article-status/` 目录。如果递归删除被安全策略拦截，通过 `apply_patch` 逐文件删除快照，不得扩大范围。
