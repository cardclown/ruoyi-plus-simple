# 文章请求模型精简实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使用一个写入 BO 和一个查询对象精简文章接口，让新增请求不接收文章 ID、审计字段或 `params`，同时保持现有接口地址和业务行为不变。

**Architecture:** `ContentArticleBo` 继续由新增和修改共用，但移除 `BaseEntity` 继承，并使用 Jackson `AddView/EditView` 区分 POST 与 PUT 的可绑定字段；列表和导出改用只含三个显式筛选字段的 `ContentArticleQuery`。Service 的新增映射永不复制客户端 ID，Entity 继续通过 MyBatis Plus `ASSIGN_ID` 和 `MetaObjectHandler` 维护雪花 ID 及审计字段。

**Tech Stack:** Java 17、Spring Boot 3.5、Spring MVC、Jackson `@JsonView`、Jakarta Validation、MyBatis Plus、SpringDoc OpenAPI 3、JUnit 5、AssertJ、Mockito、Maven。

## Global Constraints

- 仅修改新开发的 `ruoyi-content` 文章模块及其接口文档；不修改框架公共 `BaseEntity`、自动填充器、系统模块或代码生成器。
- `POST /content/article`、`PUT /content/article`、列表、导出、详情和删除的地址、HTTP 方法及响应结构保持不变。
- 新增和修改只保留一个 `ContentArticleBo`；查询只新增一个 `ContentArticleQuery`，不继续拆分 Add/Edit BO。
- POST 不展示、不绑定、不使用 `articleId`；PUT 正常绑定并要求 `articleId`。
- `createDept`、`createBy`、`createTime`、`updateBy`、`updateTime` 和 `params` 不得属于文章请求模型。
- `ContentArticle` 继续继承 `TenantEntity`，雪花 ID、租户隔离和审计字段自动填充机制保持不变。
- 标签校验、状态校验、HTML 清洗、OSS 校验、发布策略、逻辑删除和事务边界保持不变。
- 项目默认 `<skipTests>true</skipTests>`；所有测试命令必须显式传入 `-DskipTests=false`。
- 当前工作区含用户未提交业务代码和 40 个已暂存 SQL 删除；每次暂存和提交只能使用精确文件路径，不得执行全量 `git add .`。
- 本计划只创建本地提交，不推送远端；发布动作由用户另行确认。

---

## 文件结构

- 修改 `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/bo/ContentArticleBo.java`：唯一的新增/修改请求 BO和 Jackson 视图定义。
- 创建 `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/bo/ContentArticleQuery.java`：列表和导出的明确查询条件。
- 创建 `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/domain/bo/ContentArticleRequestModelContractTest.java`：请求模型继承、字段集合和 JsonView 绑定契约。
- 修改 `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/controller/ContentArticleController.java`：POST/PUT 视图以及查询类型。
- 修改 `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/IContentArticleService.java`：查询方法签名。
- 修改 `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/impl/ContentArticleServiceImpl.java`：查询类型和新增 ID 防护。
- 修改 `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/controller/ContentArticleControllerContractTest.java`：Controller 参数与 JsonView 契约。
- 修改 `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/service/impl/ContentArticleServiceImplTest.java`：新增不复制 ID和修改仍使用 ID的回归测试。
- 修改 `docs/content-article-frontend-api.md`：明确新增、修改、查询允许字段和后台维护字段。

---

### Task 1: 建立精简请求模型与 JsonView 绑定契约

**Files:**
- Create: `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/domain/bo/ContentArticleRequestModelContractTest.java`
- Modify: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/bo/ContentArticleBo.java`
- Create: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/bo/ContentArticleQuery.java`

**Interfaces:**
- Consumes: 现有 `ContentArticleBo` 业务字段和 `AddGroup/EditGroup` 校验规则。
- Produces: `ContentArticleBo.AddView`、`ContentArticleBo.EditView`、`ContentArticleQuery`，供 Controller 和 Service 使用。

- [ ] **Step 1: 写请求模型失败测试**

创建 `ContentArticleRequestModelContractTest`，用反射避免尚未创建的视图和查询类导致测试代码无法编译：

```java
package org.dromara.content.domain.bo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.beans.Introspector;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ContentArticleRequestModelContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void writeModelDoesNotInheritFrameworkRequestFields() throws Exception {
        assertThat(BaseEntity.class.isAssignableFrom(ContentArticleBo.class)).isFalse();
        assertThat(propertyNames(ContentArticleBo.class))
            .doesNotContain("createDept", "createBy", "createTime", "updateBy", "updateTime", "params");
    }

    @Test
    void addViewDoesNotBindArticleId() throws Exception {
        Class<?> addView = Class.forName(ContentArticleBo.class.getName() + "$AddView");

        ContentArticleBo bo = objectMapper.readerWithView(addView)
            .forType(ContentArticleBo.class)
            .readValue("{\"articleId\":999,\"title\":\"标题\",\"content\":\"<p>正文</p>\",\"categoryDictCode\":11,\"status\":\"0\"}");

        assertThat(bo.getArticleId()).isNull();
        assertThat(bo.getTitle()).isEqualTo("标题");
    }

    @Test
    void editViewBindsArticleIdAndCommonFields() throws Exception {
        Class<?> editView = Class.forName(ContentArticleBo.class.getName() + "$EditView");

        ContentArticleBo bo = objectMapper.readerWithView(editView)
            .forType(ContentArticleBo.class)
            .readValue("{\"articleId\":999,\"title\":\"标题\",\"content\":\"<p>正文</p>\",\"categoryDictCode\":11,\"status\":\"1\"}");

        assertThat(bo.getArticleId()).isEqualTo(999L);
        assertThat(bo.getTitle()).isEqualTo("标题");
    }

    @Test
    void queryModelOnlyContainsExplicitFilters() throws Exception {
        Class<?> queryType = Class.forName("org.dromara.content.domain.bo.ContentArticleQuery");

        assertThat(propertyNames(queryType))
            .containsExactlyInAnyOrder("title", "categoryDictCode", "status");
    }

    private static Set<String> propertyNames(Class<?> type) throws Exception {
        return Arrays.stream(Introspector.getBeanInfo(type, Object.class).getPropertyDescriptors())
            .map(descriptor -> descriptor.getName())
            .collect(Collectors.toSet());
    }
}
```

- [ ] **Step 2: 运行测试并确认 RED**

PowerShell：

```powershell
$mvn = 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd'
& $mvn -pl ruoyi-modules/ruoyi-content -am -DskipTests=false -Dtest=ContentArticleRequestModelContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL；至少包含“`BaseEntity` 仍可赋值给 `ContentArticleBo`”或找不到 `ContentArticleBo$AddView` / `ContentArticleQuery`，证明当前模型仍暴露框架字段。

- [ ] **Step 3: 最小化修改 `ContentArticleBo`**

用下面的完整类定义替换当前 `ContentArticleBo`；移除 `extends BaseEntity`、`@EqualsAndHashCode` 和对应 import，保留现有业务字段与校验：

```java
package org.dromara.content.domain.bo;

import com.fasterxml.jackson.annotation.JsonView;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.core.xss.Xss;
import org.dromara.content.domain.ContentArticle;

import java.util.List;

@Data
@AutoMapper(target = ContentArticle.class, reverseConvertGenerate = false)
public class ContentArticleBo {

    public interface AddView {
    }

    public interface EditView extends AddView {
    }

    @JsonView(EditView.class)
    @NotNull(message = "文章ID不能为空", groups = EditGroup.class)
    private Long articleId;

    @JsonView(AddView.class)
    @NotBlank(message = "文章标题不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 200, message = "文章标题长度不能超过{max}个字符", groups = {AddGroup.class, EditGroup.class})
    @Xss(message = "文章标题不能包含脚本内容", groups = {AddGroup.class, EditGroup.class})
    private String title;

    @JsonView(AddView.class)
    @Size(max = 500, message = "文章简介长度不能超过{max}个字符", groups = {AddGroup.class, EditGroup.class})
    @Xss(message = "文章简介不能包含脚本内容", groups = {AddGroup.class, EditGroup.class})
    private String summary;

    @JsonView(AddView.class)
    @NotBlank(message = "文章正文不能为空", groups = {AddGroup.class, EditGroup.class})
    private String content;

    @JsonView(AddView.class)
    @NotNull(message = "文章分类不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long categoryDictCode;

    @JsonView(AddView.class)
    @Size(max = 10, message = "文章标签最多选择{max}个", groups = {AddGroup.class, EditGroup.class})
    private List<@NotNull(message = "文章标签ID不能为空") Long> tagIds;

    @JsonView(AddView.class)
    private Long coverOssId;

    @JsonView(AddView.class)
    @NotBlank(message = "发布状态不能为空", groups = {AddGroup.class, EditGroup.class})
    @Pattern(regexp = "^[01]$", message = "发布状态只能为0或1", groups = {AddGroup.class, EditGroup.class})
    private String status;
}
```

- [ ] **Step 4: 创建明确查询对象**

创建 `ContentArticleQuery.java`：

```java
package org.dromara.content.domain.bo;

import lombok.Data;

/**
 * 文章列表与导出查询条件。
 */
@Data
public class ContentArticleQuery {

    /** 文章标题，模糊匹配 */
    private String title;

    /** 分类字典编码 */
    private Long categoryDictCode;

    /** 发布状态（0草稿 1已发布） */
    private String status;
}
```

- [ ] **Step 5: 运行请求模型测试并确认 GREEN**

Run: 使用 Step 2 的 Maven 命令。

Expected: `ContentArticleRequestModelContractTest` 4 个测试全部 PASS；输出中不得出现 `Tests are skipped.` 对该测试模块生效。

- [ ] **Step 6: 运行原 BO 校验回归测试**

```powershell
& $mvn -pl ruoyi-modules/ruoyi-content -am -DskipTests=false -Dtest=ContentArticleBoValidationTest,ContentArticleRequestModelContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 原标签数量、状态和必填字段校验测试全部 PASS。

- [ ] **Step 7: 创建本地检查点提交**

```powershell
git add -- `
  ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/bo/ContentArticleBo.java `
  ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/bo/ContentArticleQuery.java `
  ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/domain/bo/ContentArticleRequestModelContractTest.java
git commit --only -m "refactor: separate article write and query models" -- `
  ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/bo/ContentArticleBo.java `
  ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/bo/ContentArticleQuery.java `
  ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/domain/bo/ContentArticleRequestModelContractTest.java
```

提交后检查 `git diff --cached --name-status -- script/sql/update` 仍为 40 个删除项。

---

### Task 2: 接入 Controller/Service 并阻止新增使用客户端 ID

**Files:**
- Modify: `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/controller/ContentArticleControllerContractTest.java`
- Modify: `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/service/impl/ContentArticleServiceImplTest.java`
- Modify: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/controller/ContentArticleController.java`
- Modify: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/IContentArticleService.java`
- Modify: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/impl/ContentArticleServiceImpl.java`

**Interfaces:**
- Consumes: Task 1 的 `ContentArticleBo.AddView`、`ContentArticleBo.EditView` 和 `ContentArticleQuery`。
- Produces: `queryPageList(ContentArticleQuery, PageQuery)`、`queryList(ContentArticleQuery)`；新增/修改继续使用 `ContentArticleBo`。

- [ ] **Step 1: 写 Controller 失败契约测试**

在 `ContentArticleControllerContractTest` 增加：

```java
import com.fasterxml.jackson.annotation.JsonView;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.content.domain.bo.ContentArticleBo;
import org.dromara.content.domain.bo.ContentArticleQuery;

@Test
void usesAddViewForCreateRequest() throws Exception {
    Method method = ContentArticleController.class.getDeclaredMethod("add", ContentArticleBo.class);
    JsonView jsonView = method.getParameters()[0].getAnnotation(JsonView.class);

    assertThat(jsonView).isNotNull();
    assertThat(jsonView.value()).containsExactly(ContentArticleBo.AddView.class);
}

@Test
void usesEditViewForUpdateRequest() throws Exception {
    Method method = ContentArticleController.class.getDeclaredMethod("edit", ContentArticleBo.class);
    JsonView jsonView = method.getParameters()[0].getAnnotation(JsonView.class);

    assertThat(jsonView).isNotNull();
    assertThat(jsonView.value()).containsExactly(ContentArticleBo.EditView.class);
}

@Test
void usesExplicitQueryModelForList() throws Exception {
    Method method = ContentArticleController.class.getDeclaredMethod(
        "list", ContentArticleQuery.class, PageQuery.class);

    assertThat(method).isNotNull();
}
```

- [ ] **Step 2: 写 Service 新增 ID 失败测试**

在 `ContentArticleServiceImplTest` 增加：

```java
import java.util.concurrent.atomic.AtomicReference;

@Test
void insertNeverCopiesClientSuppliedArticleId() {
    ContentArticleBo bo = articleBo();
    bo.setArticleId(999L);
    AtomicReference<Long> idSeenBeforeInsert = new AtomicReference<>();
    when(dictionaryService.validateAndNormalize(11L, null)).thenReturn(List.of());
    when(articleMapper.insert(any(ContentArticle.class))).thenAnswer(invocation -> {
        ContentArticle article = invocation.getArgument(0);
        idSeenBeforeInsert.set(article.getArticleId());
        article.setArticleId(100L);
        return 1;
    });

    assertThat(service.insertByBo(bo)).isTrue();

    assertThat(idSeenBeforeInsert.get()).isNull();
    assertThat(bo.getArticleId()).isEqualTo(100L);
}
```

已有 `updateCanClearAllTagsAndRelations` 继续证明修改路径使用 `bo.articleId` 查询并更新文章。

- [ ] **Step 3: 运行 Controller/Service 测试并确认 RED**

```powershell
& $mvn -pl ruoyi-modules/ruoyi-content -am -DskipTests=false -Dtest=ContentArticleControllerContractTest,ContentArticleServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL；Controller 参数缺少 `@JsonView`、列表仍接收 `ContentArticleBo`，且新增 Mapper 收到 `999L`。

- [ ] **Step 4: 修改 Controller 参数类型和视图**

在 `ContentArticleController` 引入 `JsonView` 和 `ContentArticleQuery`：

```java
import com.fasterxml.jackson.annotation.JsonView;
import org.dromara.content.domain.bo.ContentArticleQuery;
```

修改四个方法签名：

```java
public TableDataInfo<ContentArticleVo> list(ContentArticleQuery query, PageQuery pageQuery) {
    return contentArticleService.queryPageList(query, pageQuery);
}

public void export(ContentArticleQuery query, HttpServletResponse response) {
    List<ContentArticleVo> list = contentArticleService.queryList(query);
    ExcelUtil.exportExcel(list, "文章", ContentArticleVo.class, response);
}

public R<Void> add(@JsonView(ContentArticleBo.AddView.class)
                   @Validated(AddGroup.class)
                   @RequestBody ContentArticleBo bo) {
    return toAjax(contentArticleService.insertByBo(bo));
}

public R<Void> edit(@JsonView(ContentArticleBo.EditView.class)
                    @Validated(EditGroup.class)
                    @RequestBody ContentArticleBo bo) {
    return toAjax(contentArticleService.updateByBo(bo));
}
```

- [ ] **Step 5: 修改 Service 查询签名**

在 `IContentArticleService` 和 `ContentArticleServiceImpl` 中把查询参数改为：

```java
TableDataInfo<ContentArticleVo> queryPageList(ContentArticleQuery query, PageQuery pageQuery);

List<ContentArticleVo> queryList(ContentArticleQuery query);
```

`buildQueryWrapper` 同步改为：

```java
private LambdaQueryWrapper<ContentArticle> buildQueryWrapper(ContentArticleQuery query) {
    LambdaQueryWrapper<ContentArticle> wrapper = Wrappers.lambdaQuery();
    wrapper.select(
        ContentArticle::getArticleId,
        ContentArticle::getTitle,
        ContentArticle::getSummary,
        ContentArticle::getCategoryDictCode,
        ContentArticle::getTagIds,
        ContentArticle::getCoverOssId,
        ContentArticle::getStatus,
        ContentArticle::getPublishBy,
        ContentArticle::getPublishTime,
        ContentArticle::getCreateDept,
        ContentArticle::getCreateBy,
        ContentArticle::getCreateTime,
        ContentArticle::getUpdateBy,
        ContentArticle::getUpdateTime
    );
    wrapper.orderByDesc(ContentArticle::getArticleId);
    wrapper.like(StringUtils.isNotBlank(query.getTitle()), ContentArticle::getTitle, query.getTitle());
    wrapper.eq(query.getCategoryDictCode() != null,
        ContentArticle::getCategoryDictCode, query.getCategoryDictCode());
    wrapper.eq(StringUtils.isNotBlank(query.getStatus()), ContentArticle::getStatus, query.getStatus());
    return wrapper;
}
```

- [ ] **Step 6: 移除新增路径的 ID 复制**

`ContentArticleServiceImpl.toEntity(ContentArticleBo bo)` 中删除：

```java
article.setArticleId(bo.getArticleId());
```

保留 `updateByBo` 中的显式设置：

```java
ContentArticle update = prepareArticle(bo);
update.setArticleId(bo.getArticleId());
```

结果是新增实体在 Mapper 调用前 ID 必为 `null`，修改实体才设置请求 ID。

- [ ] **Step 7: 运行 Controller/Service 测试并确认 GREEN**

Run: 使用 Step 3 的 Maven 命令。

Expected: `ContentArticleControllerContractTest` 和 `ContentArticleServiceImplTest` 全部 PASS；新增测试观察到 Mapper 调用前 ID 为 `null`。

- [ ] **Step 8: 运行文章模块全部测试**

```powershell
& $mvn -pl ruoyi-modules/ruoyi-content -am -DskipTests=false test
```

Expected: `ruoyi-content` 的 `dev` 标签测试全部执行且 0 failure、0 error；不能只看到 `BUILD SUCCESS` 而没有测试统计。

- [ ] **Step 9: 创建本地检查点提交**

```powershell
git add -- `
  ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/controller/ContentArticleController.java `
  ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/IContentArticleService.java `
  ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/impl/ContentArticleServiceImpl.java `
  ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/controller/ContentArticleControllerContractTest.java `
  ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/service/impl/ContentArticleServiceImplTest.java
git commit --only -m "refactor: harden article request boundaries" -- `
  ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/controller/ContentArticleController.java `
  ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/IContentArticleService.java `
  ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/impl/ContentArticleServiceImpl.java `
  ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/controller/ContentArticleControllerContractTest.java `
  ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/service/impl/ContentArticleServiceImplTest.java
```

再次确认原 SQL 暂存删除数量仍为 40。

---

### Task 3: 更新前端接口契约文档

**Files:**
- Modify: `docs/content-article-frontend-api.md`

**Interfaces:**
- Consumes: Task 1/2 确定的 POST、PUT 和查询字段集合。
- Produces: 与真实 OpenAPI 和后端绑定规则一致的前端接入说明。

- [ ] **Step 1: 写文档一致性失败检查**

运行以下 PowerShell 检查，当前文档尚未完整声明禁止字段，应失败：

```powershell
$doc = Get-Content -LiteralPath 'docs/content-article-frontend-api.md' -Raw
$required = @(
  '新增接口不要提交 `articleId`',
  '`createDept`、`createBy`、`createTime`、`updateBy`、`updateTime`',
  '文章接口不使用通用 `params`'
)
$missing = @($required | Where-Object { -not $doc.Contains($_) })
if ($missing.Count -gt 0) { throw ('缺少接口约定：' + ($missing -join '；')) }
```

Expected: FAIL，并列出缺少的接口约定。

- [ ] **Step 2: 更新接口文档**

在“通用约定”增加：

```markdown
- 新增接口不要提交 `articleId`；该字段只属于修改请求，新增 ID由后端雪花算法生成。
- `createDept`、`createBy`、`createTime`、`updateBy`、`updateTime` 均由后端维护，任何写接口都不要提交。
- 文章接口不使用通用 `params`；列表和导出只支持文档中明确列出的筛选字段。
```

在“新增文章”请求 Demo 前增加下面的明确白名单：

```markdown
新增请求只允许提交以下业务字段：

- `title`
- `summary`
- `content`
- `categoryDictCode`
- `tagIds`
- `coverOssId`
- `status`

`articleId`、审计字段和 `params` 不属于新增请求，即使客户端额外提交也不会用于保存。
```

“修改文章”继续说明必须额外传 `articleId`，其余允许字段与新增一致。

- [ ] **Step 3: 重跑文档一致性检查**

Run: 使用 Step 1 的 PowerShell 命令。

Expected: PASS，无异常输出。

- [ ] **Step 4: 创建本地检查点提交**

```powershell
git add -- docs/content-article-frontend-api.md
git commit --only -m "docs: clarify article request fields" -- docs/content-article-frontend-api.md
```

提交必须只包含该接口文档。

---

### Task 4: 构建与 OpenAPI 运行时验收

**Files:**
- Verify only: `ruoyi-admin/target/ruoyi-admin.jar`
- Verify only: `http://127.0.0.1:18081/v3/api-docs`
- Verify only: `http://127.0.0.1:18081/swagger-ui/index.html`

**Interfaces:**
- Consumes: Task 1-3 的请求模型、Controller、Service 和文档。
- Produces: 编译、单元测试、OpenAPI schema 和工作区保护证据。

- [ ] **Step 1: 运行文章模块完整测试并读取测试统计**

```powershell
$mvn = 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd'
& $mvn -pl ruoyi-modules/ruoyi-content -am -DskipTests=false test
```

Expected: Maven exit code 0；`ruoyi-content` 测试 0 failure、0 error；测试没有被跳过。

- [ ] **Step 2: 打包后台**

```powershell
& $mvn -pl ruoyi-admin -am package -DskipTests
```

Expected: 26 个 reactor 模块均为 `SUCCESS`，并生成 `ruoyi-admin/target/ruoyi-admin.jar`。

- [ ] **Step 3: 用独立端口启动验收实例**

先确认 18081 未被占用，再运行：

```powershell
& 'C:\Users\dj\.jdks\ms-21.0.12\bin\java.exe' `
  -jar 'C:\Users\dj\ruoyi-plus\simple\RuoYi-Vue-Plus\ruoyi-admin\target\ruoyi-admin.jar' `
  --server.port=18081
```

验收实例必须由受控进程启动，不能停止或替换用户当前 8080 端口服务。

- [ ] **Step 4: 验证 Swagger UI 与 POST/PUT schema**

另开 PowerShell 执行：

```powershell
$ui = Invoke-WebRequest -Uri 'http://127.0.0.1:18081/swagger-ui/index.html' -UseBasicParsing
if ($ui.StatusCode -ne 200 -or $ui.Headers.'Content-Type' -notmatch 'text/html') {
  throw 'Swagger UI 验证失败'
}

$doc = Invoke-RestMethod -Uri 'http://127.0.0.1:18081/v3/api-docs'

function Get-Schema([object]$schema, [object]$components) {
  if ($schema.'$ref') {
    $name = ($schema.'$ref' -split '/')[-1]
    return $components.schemas.$name
  }
  return $schema
}

$postRaw = $doc.paths.'/content/article'.post.requestBody.content.'application/json'.schema
$putRaw = $doc.paths.'/content/article'.put.requestBody.content.'application/json'.schema
$postSchema = Get-Schema $postRaw $doc.components
$putSchema = Get-Schema $putRaw $doc.components
$postProperties = @($postSchema.properties.PSObject.Properties.Name)
$putProperties = @($putSchema.properties.PSObject.Properties.Name)
$forbidden = @('createDept', 'createBy', 'createTime', 'updateBy', 'updateTime', 'params')

if ('articleId' -in $postProperties) { throw 'POST schema 仍暴露 articleId' }
if (@($forbidden | Where-Object { $_ -in $postProperties }).Count -gt 0) {
  throw 'POST schema 仍暴露审计字段或 params'
}
if ('articleId' -notin $putProperties) { throw 'PUT schema 缺少 articleId' }
if ('articleId' -notin @($putSchema.required)) { throw 'PUT schema 未要求 articleId' }
if (@($forbidden | Where-Object { $_ -in $putProperties }).Count -gt 0) {
  throw 'PUT schema 仍暴露审计字段或 params'
}

Write-Output 'OPENAPI_ARTICLE_REQUEST_MODELS=PASS'
```

Expected: Swagger UI 返回 200，脚本输出 `OPENAPI_ARTICLE_REQUEST_MODELS=PASS`。

- [ ] **Step 5: 验证查询参数集合**

继续执行：

```powershell
$listParams = @($doc.paths.'/content/article/list'.get.parameters | ForEach-Object { $_.name })
$forbiddenQuery = @('articleId', 'content', 'tagIds', 'coverOssId', 'createDept', 'createBy', 'createTime', 'updateBy', 'updateTime', 'params')
$unexpected = @($forbiddenQuery | Where-Object { $_ -in $listParams })
if ($unexpected.Count -gt 0) { throw ('列表仍暴露无关参数：' + ($unexpected -join ',')) }
foreach ($requiredFilter in @('title', 'categoryDictCode', 'status', 'pageNum', 'pageSize')) {
  if ($requiredFilter -notin $listParams) { throw ('列表缺少参数：' + $requiredFilter) }
}
Write-Output 'OPENAPI_ARTICLE_QUERY=PASS'
```

Expected: 输出 `OPENAPI_ARTICLE_QUERY=PASS`。

- [ ] **Step 6: 清理临时实例并复核工作区**

只终止命令行同时匹配本仓库 JAR 和 `--server.port=18081` 的 Java 进程。然后检查：

```powershell
git status --short --branch
git diff --cached --name-status -- script/sql/update
```

Expected:

- 18081 不再监听。
- 原工作区未关联业务改动仍存在。
- `script/sql/update` 仍有且仅有原来的 40 个暂存删除。
- 本次本地提交没有包含框架公共代码、其他业务模块或未授权文件。

---

## 最终验收清单

- [ ] POST schema 只有文章业务字段，没有 `articleId`、审计字段或 `params`。
- [ ] PUT schema 只有文章业务字段和必填 `articleId`，没有审计字段或 `params`。
- [ ] 列表和导出使用 `ContentArticleQuery`，没有无关字段。
- [ ] 新增 Mapper 调用前 ID 为 `null`，由 MyBatis Plus 生成。
- [ ] 修改仍使用请求中的 `articleId`。
- [ ] MyBatis Plus 自动填充部门、人员和时间的框架代码未修改。
- [ ] 标签、状态、清洗、OSS、发布和删除测试全部通过。
- [ ] 接口文档与实际 OpenAPI 一致。
- [ ] 原有工作区修改和 40 个 SQL 暂存删除未受影响。
- [ ] 未执行远端推送。
