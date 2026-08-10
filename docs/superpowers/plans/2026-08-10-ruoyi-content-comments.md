# ruoyi-content 注释补充 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 `ruoyi-content` 的 18 个 Java 文件补齐公共契约、字段语义和复杂业务规则注释，使维护者无需深入实现即可理解模块边界，同时保证运行行为完全不变。

**Architecture:** 采用分层注释：公共 API 使用完整 Javadoc，数据模型说明字段来源和请求边界，复杂流程仅在“为什么这样处理”的位置添加行内注释。已跟踪文件的注释按职责分批提交；当前未跟踪的业务文件只在工作区补注释并与基线快照比较，不因本任务被整体纳入提交。

**Tech Stack:** Java 17、Spring Boot 3.5、MyBatis-Plus、MapStruct Plus、Jackson JsonView、Jakarta Validation、JUnit 5、Maven 3.9。

## Global Constraints

- 设计依据：`docs/superpowers/specs/2026-08-10-ruoyi-content-comments-design.md`。
- 只允许修改注释和注释所需的空白排版；不得修改 Java 注解、导入、字段、常量、方法签名、方法体表达式、SQL、依赖或配置。
- 所有新增注释使用简洁中文；解释职责、约束和原因，不复述普通赋值、简单循环、getter/setter。
- 不新增测试用例：本任务不改变行为，使用现有回归测试和源代码差异证明无行为变化。
- 当前未跟踪的文章实体、VO、Mapper、support 类和 TypeHandler 不得为本任务单独 `git add` 或提交；它们由后续完整业务模块提交统一处理。
- 禁止使用 `git add .`、`git add -A`、`git reset`、`git checkout --`。
- 每次提交必须使用精确路径，并确认提交不包含 `script/sql/update`。
- `script/sql/update` 下原有 40 个暂存删除必须始终保留，且不得进入本任务提交。

---

### Task 1: 建立注释变更基线并补齐数据模型说明

**Files:**
- Modify: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/bo/ContentArticleBo.java:1-61`
- Modify: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/bo/ContentArticleQuery.java:1-19`
- Modify, currently untracked: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/ContentArticle.java:1-69`
- Modify, currently untracked: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/ContentArticleTag.java:1-27`
- Modify, currently untracked: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/vo/ContentArticleVo.java:1-63`
- Modify, currently untracked: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/vo/ContentArticleDictOptionVo.java:1-24`
- Modify, currently untracked: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/enums/ContentArticleDictType.java:1-17`
- Temporary baseline: `.superpowers/sdd/2026-08-10-ruoyi-content-comments/baseline/`

**Interfaces:**
- Consumes: `ContentArticleBo.AddView`、`ContentArticleBo.EditView`、`AddGroup`、`EditGroup`、`TenantEntity` 和 MyBatis-Plus `ASSIGN_ID` 既有行为。
- Produces: 数据模型字段来源、请求可见性、审计归属和字典编码语义的维护说明；不产生新的运行接口。

- [ ] **Step 1: 为全部 18 个生产文件创建只读基线快照**

在 PowerShell 中执行，快照只用于本任务差异审查，不提交：

```powershell
$repo = 'C:\Users\dj\ruoyi-plus\simple\RuoYi-Vue-Plus'
$source = Join-Path $repo 'ruoyi-modules\ruoyi-content\src\main\java'
$snapshot = Join-Path $repo '.superpowers\sdd\2026-08-10-ruoyi-content-comments\baseline'
New-Item -ItemType Directory -Force -Path $snapshot | Out-Null
git rev-parse HEAD | Set-Content -LiteralPath (Join-Path (Split-Path $snapshot) 'base-sha.txt')
Get-ChildItem -LiteralPath $source -Recurse -Filter '*.java' | ForEach-Object {
    $relative = $_.FullName.Substring($source.Length).TrimStart('\')
    $target = Join-Path $snapshot $relative
    New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
    Copy-Item -LiteralPath $_.FullName -Destination $target
}
```

验证：

```powershell
$sourceCount = @(Get-ChildItem -LiteralPath 'ruoyi-modules\ruoyi-content\src\main\java' -Recurse -Filter '*.java').Count
$snapshotCount = @(Get-ChildItem -LiteralPath '.superpowers\sdd\2026-08-10-ruoyi-content-comments\baseline' -Recurse -Filter '*.java').Count
"SOURCE_COUNT=$sourceCount"
"SNAPSHOT_COUNT=$snapshotCount"
```

Expected: `SOURCE_COUNT=18` 且 `SNAPSHOT_COUNT=18`。

- [ ] **Step 2: 补充 BO 与 Query 的请求边界注释**

为 `ContentArticleBo` 添加类级说明，明确它是新增和修改共用请求对象；为两个 JsonView 添加以下语义：

```java
/** 新增请求可见字段视图，不包含由后台生成的文章 ID。 */
public interface AddView {
}

/** 修改请求可见字段视图，在新增字段基础上额外接收文章 ID。 */
public interface EditView extends AddView {
}
```

字段注释必须逐项覆盖：

```java
/** 文章 ID；仅修改请求接收，新增时由后台雪花 ID 策略生成。 */
/** 文章标题，新增和修改均必填。 */
/** 文章简介，可选，最长 500 个字符。 */
/** Quill 富文本 HTML 正文，保存前由后台执行白名单过滤。 */
/** 分类字典编码，必须属于当前租户的文章分类字典。 */
/** 标签字典编码集合，后台去重并限制最多 10 个。 */
/** 封面 OSS 文件 ID，后台校验文件存在且属于当前租户。 */
/** 发布状态：0 表示草稿，1 表示已发布。 */
```

完善 `ContentArticleQuery` 类级说明，明确它只承载列表和导出的业务筛选，分页与排序由独立 `PageQuery` 提供。

- [ ] **Step 3: 补充实体、VO 和枚举字段语义**

在 `ContentArticle` 类级 Javadoc 中说明：继承 `TenantEntity` 以获得租户、部门、创建与更新审计字段；这些字段由后台填充，不来自请求。

在 `ContentArticleTag` 中为以下字段补充注释：关联主键、文章 ID、标签字典编码、创建人、创建时间，并说明创建审计由后台写入。

在 `ContentArticleVo` 中为所有字段补充面向响应和导出的说明，尤其明确：

```java
/** 标签字典编码集合，用于前端回显，不直接作为 Excel 列导出。 */
/** 发布人用户 ID，由后台在首次发布时记录。 */
/** 首次发布时间；已发布文章重复编辑时保持原值。 */
/** 创建时间，由后台审计机制填充。 */
/** 最后更新时间，由后台审计机制填充。 */
```

在 `ContentArticleDictOptionVo` 中说明每个字段与系统字典数据的对应关系。在 `ContentArticleDictType` 中分别说明 `CATEGORY` 和 `TAG` 对应的固定字典类型。

- [ ] **Step 4: 审查数据模型差异只包含注释**

对已跟踪文件执行：

```powershell
git diff --word-diff=plain -- `
  ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/bo/ContentArticleBo.java `
  ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/bo/ContentArticleQuery.java
```

对当前未跟踪文件逐一与快照比较：

```powershell
$source = 'ruoyi-modules\ruoyi-content\src\main\java'
$baseline = '.superpowers\sdd\2026-08-10-ruoyi-content-comments\baseline'
$files = @(
  'org\dromara\content\domain\ContentArticle.java',
  'org\dromara\content\domain\ContentArticleTag.java',
  'org\dromara\content\domain\vo\ContentArticleVo.java',
  'org\dromara\content\domain\vo\ContentArticleDictOptionVo.java',
  'org\dromara\content\enums\ContentArticleDictType.java'
)
foreach ($file in $files) {
    git diff --no-index --word-diff=plain -- (Join-Path $baseline $file) (Join-Path $source $file)
}
```

Expected: 所有新增内容均位于 `/** ... */`、`*` 或 `//` 注释中；Java 声明、注解和值不变。`git diff --no-index` 在存在差异时返回 1，这是正常结果。

- [ ] **Step 5: 运行数据模型相关测试**

```powershell
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' `
  -pl ruoyi-modules/ruoyi-content -am `
  '-DskipTests=false' `
  '-Dtest=ContentArticleBoValidationTest,ContentArticleRequestModelContractTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: `ContentArticleBoValidationTest` 5 个测试、`ContentArticleRequestModelContractTest` 4 个测试全部通过，构建成功。

- [ ] **Step 6: 只提交已跟踪的 BO 与 Query 注释**

```powershell
$files = @(
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/bo/ContentArticleBo.java',
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/bo/ContentArticleQuery.java'
)
git add -- $files
git commit --only -m 'docs: document article request models' -- $files
```

提交后执行：

```powershell
git show --format= --name-status HEAD
$stagedSql = @(git diff --cached --name-status -- script/sql/update)
"STAGED_SQL_UPDATE_COUNT=$($stagedSql.Count)"
```

Expected: 提交只含上述两个文件；`STAGED_SQL_UPDATE_COUNT=40`。

---

### Task 2: 补齐 Controller 与 Service 公共契约

**Files:**
- Modify: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/controller/ContentArticleController.java:1-151`
- Modify: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/IContentArticleService.java:1-33`

**Interfaces:**
- Consumes: `/content/article` 既有 URL、权限标识、`ContentArticleQuery`、`PageQuery`、`ContentArticleBo.AddView/EditView`。
- Produces: Controller 与 Service 的完整维护契约；URL、参数类型、返回类型和校验注解保持不变。

- [ ] **Step 1: 完善 Controller 方法 Javadoc**

为全部接口补齐参数与返回语义：

```java
/**
 * 分页查询当前数据权限范围内的文章。
 *
 * @param query 文章业务筛选条件
 * @param pageQuery 分页与排序参数
 * @return 分页文章数据
 */
```

其他 Controller 方法必须分别说明：

- 分类、标签选项来自当前租户固定字典；
- 导出仅使用 `title/categoryDictCode/status` 三个筛选字段；
- 详情查询受租户与数据权限限制；
- 新增请求使用 `AddView + AddGroup`，客户端传入文章 ID 不进入绑定结果；
- 修改请求使用 `EditView + EditGroup`，文章 ID 必填；
- 删除执行逻辑删除，并对 ID 集合做权限与存在性校验。

在 `add` 和 `edit` 参数上方各增加一条简短行内注释，解释 JsonView 控制可绑定字段、Validation Group 控制字段校验，两者职责不同。

- [ ] **Step 2: 完善 Service 接口方法 Javadoc**

为 7 个方法逐一补齐 `@param`、`@return` 和业务边界：

```java
/**
 * 根据文章 ID 查询当前租户和数据权限范围内的文章详情。
 *
 * @param articleId 文章 ID
 * @return 文章详情
 */
ContentArticleVo queryById(Long articleId);
```

其余方法必须明确：分页参数独立于业务筛选；导出查询不分页；新增 ID 由持久层生成；修改使用请求中的 ID；逻辑删除保留数据；物理删除只接受已逻辑删除的数据。

- [ ] **Step 3: 审查公共契约差异并运行契约测试**

```powershell
git diff --word-diff=plain -- `
  ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/controller/ContentArticleController.java `
  ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/IContentArticleService.java
git diff --check -- `
  ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/controller/ContentArticleController.java `
  ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/IContentArticleService.java
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' `
  -pl ruoyi-modules/ruoyi-content -am `
  '-DskipTests=false' `
  '-Dtest=ContentArticleControllerContractTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: 差异只含注释；`ContentArticleControllerContractTest` 9 个测试全部通过。

- [ ] **Step 4: 提交公共契约注释**

```powershell
$files = @(
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/controller/ContentArticleController.java',
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/IContentArticleService.java'
)
git add -- $files
git commit --only -m 'docs: document article API contracts' -- $files
```

Expected: 提交只含 Controller 和 Service 接口；40 个 SQL 暂存删除继续保留。

---

### Task 3: 补齐 Mapper 与标签持久化说明

**Files:**
- Modify, currently untracked: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/mapper/ContentArticleMapper.java:1-87`
- Modify, currently untracked: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/mapper/ContentArticleTagMapper.java:1-23`
- Modify, currently untracked: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/mybatis/LongListTypeHandler.java:1-69`

**Interfaces:**
- Consumes: `BaseMapperPlus`、`@DataPermission`、`LongListTypeHandler` 既有实现。
- Produces: 数据权限覆盖范围、逻辑/物理删除差异和标签列表序列化格式说明；不提交当前未跟踪业务文件。

- [ ] **Step 1: 为文章 Mapper 的每个操作补齐权限与删除语义**

类级 Javadoc 说明所有文章读写操作必须经过租户插件和 `create_dept/create_by` 数据权限约束。

为 8 个 Mapper 方法逐一补充：

- `selectArticleById`：按数据权限读取单篇文章；
- `selectArticlePage`：分页读取 VO；
- `selectArticleList`：不分页导出；
- `selectExistingArticleIds`：批量操作前核对可见且未删除的 ID；
- `updateArticleById`：更新也受数据权限限制；
- `logicalDeleteByIds`：通过 MyBatis-Plus 逻辑删除；
- `selectDeletedArticleIds`：仅从已逻辑删除数据中筛选可物理删除 ID；
- `physicalDeleteByIds`：真正删除已逻辑删除的数据。

每个方法使用准确的 `@param`、`@return`，不改动 `@DataPermission`。

- [ ] **Step 2: 补充标签 Mapper 和 TypeHandler 说明**

为标签 Mapper 的两个删除方法说明调用场景：修改文章时重建单篇标签关系；物理删除文章前清理多篇标签关系。

为 `LongListTypeHandler` 添加：

```java
/**
 * 在 Java 的 {@code List<Long>} 与数据库逗号分隔字符串之间转换。
 * 空集合写为 SQL NULL，读取 NULL 或空白值时返回空集合；任何空元素或非数字内容均拒绝处理。
 */
```

为四个重写方法和私有 `parse` 方法补齐参数、返回值和异常语义。在空集合写 NULL 的分支添加行内注释，说明数据库使用 NULL 表示“无标签”，避免保存空字符串。

- [ ] **Step 3: 与基线比较并运行持久化测试**

```powershell
$source = 'ruoyi-modules\ruoyi-content\src\main\java'
$baseline = '.superpowers\sdd\2026-08-10-ruoyi-content-comments\baseline'
$files = @(
  'org\dromara\content\mapper\ContentArticleMapper.java',
  'org\dromara\content\mapper\ContentArticleTagMapper.java',
  'org\dromara\content\mybatis\LongListTypeHandler.java'
)
foreach ($file in $files) {
    git diff --no-index --word-diff=plain -- (Join-Path $baseline $file) (Join-Path $source $file)
}
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' `
  -pl ruoyi-modules/ruoyi-content -am `
  '-DskipTests=false' `
  '-Dtest=ContentArticleMapperContractTest,LongListTypeHandlerTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: 差异只含注释；Mapper 1 个测试和 TypeHandler 5 个测试全部通过。

- [ ] **Step 4: 保持未跟踪文件不进入暂存区**

```powershell
$unexpected = @(git diff --cached --name-only | Where-Object { $_ -match '^ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/(mapper|mybatis)/' })
"UNEXPECTED_STAGED_PERSISTENCE_FILES=$($unexpected.Count)"
```

Expected: `UNEXPECTED_STAGED_PERSISTENCE_FILES=0`。本 Task 不创建提交，注释随完整文章业务模块后续统一提交。

---

### Task 4: 解释 Service 核心流程与 support 规则

**Files:**
- Modify: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/impl/ContentArticleServiceImpl.java:1-243`
- Modify, currently untracked: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/support/ContentArticlePublishPolicy.java:1-43`
- Modify, currently untracked: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/support/ContentArticleOperationContext.java:1-21`
- Modify, currently untracked: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/support/ContentArticleHtmlSanitizer.java:1-104`
- Modify, currently untracked: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/support/ContentArticleDictionaryService.java:1-92`
- Modify, currently untracked: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/support/ContentArticleDictDeleteValidator.java:1-23`

**Interfaces:**
- Consumes: Task 1 的字段语义和 Task 2 的 Service 契约。
- Produces: 新增/修改准备流程、发布状态机、字典租户校验、OSS 校验、富文本安全和删除策略的原因说明。

- [ ] **Step 1: 为 Service 实现补齐关键方法 Javadoc**

为以下私有方法添加职责说明和准确参数：

- `buildQueryWrapper`：只选择列表/导出所需字段并应用三个允许筛选项；
- `prepareArticle`：统一执行状态、字典、OSS 与富文本校验；
- `toEntity`：只复制客户端允许控制的业务字段，故意不复制 ID 和审计字段；
- `toVo`：将详情实体转换为响应对象；
- `validateStatus`：服务层防御性校验状态；
- `validateCoverOss`：校验文件存在并受当前租户约束；
- `insertTags`：批量创建标签关联并由后台写入操作人、时间；
- `normalizeIds`：拒绝空 ID，去重并保持原输入顺序。

在新增方法中添加行内注释，明确 Mapper 插入前不设置 `articleId`，由雪花 ID 策略回填；在修改方法中说明必须显式使用请求 ID，不能沿用新增映射边界。

在逻辑删除和物理删除处说明：先核对可操作 ID 数量，防止部分成功；物理删除先清标签关系再删文章。

- [ ] **Step 2: 解释发布策略与操作上下文**

为 `ContentArticlePublishPolicy` 的两个公共方法补充状态转换矩阵：

```text
新建草稿 -> 清空发布信息
新建已发布 -> 记录当前用户和当前时间
已发布保持已发布 -> 保留首次发布人和首次发布时间
草稿变已发布 -> 记录本次操作人和时间
任意状态变草稿 -> 清空发布信息
```

为 `ContentArticleOperationContext` 说明它隔离登录态和系统时间，生产环境读取当前用户，测试可替换该依赖；为两个方法补齐返回语义。

- [ ] **Step 3: 解释字典校验与删除保护**

为 `ContentArticleDictionaryService` 的公共方法补齐：

- 分类与标签选项从当前租户字典读取；
- `validateAndNormalize` 必须校验分类、去重标签、拒绝 NULL、限制 10 个并拒绝跨租户字典编码；
- `listOptions` 将框架 DTO 转成文章模块 VO，避免向外暴露不需要的字典字段。

在 `ContentArticleDictDeleteValidator` 中说明禁止删除文章字典项是为了防止现有文章产生悬空引用，允许修改展示内容但不允许删除编码。

- [ ] **Step 4: 解释富文本白名单过滤**

为 `ContentArticleHtmlSanitizer` 的常量和方法补充：

- `STYLE_ATTRIBUTE` 只定位双引号 style 属性；
- `QUILL_LIST_ATTRIBUTE` 在过滤前暂存 Quill `data-list`；
- `DANGEROUS_STYLE` 拒绝 CSS 表达式、URL、导入、脚本协议和高风险扩展；
- `ALLOWED_STYLE_PROPERTIES` 是允许保留的展示属性集合；
- `sanitize` 先兼容 Quill 属性、再过滤标签协议、最后过滤样式并恢复属性；
- `configuration` 返回 Hutool `HTMLFilter` 的标签、属性、协议和实体白名单；
- `sanitizeStyles` 逐条保留允许且安全的 CSS 声明。

在 Quill 属性转换前后各加一条原因注释，明确这是为避免 `HTMLFilter` 丢弃 `data-*` 属性，不是业务字段改名。

- [ ] **Step 5: 与基线比较并运行核心业务测试**

```powershell
git diff --word-diff=plain -- `
  ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/impl/ContentArticleServiceImpl.java
$source = 'ruoyi-modules\ruoyi-content\src\main\java'
$baseline = '.superpowers\sdd\2026-08-10-ruoyi-content-comments\baseline'
$files = @(
  'org\dromara\content\service\support\ContentArticlePublishPolicy.java',
  'org\dromara\content\service\support\ContentArticleOperationContext.java',
  'org\dromara\content\service\support\ContentArticleHtmlSanitizer.java',
  'org\dromara\content\service\support\ContentArticleDictionaryService.java',
  'org\dromara\content\service\support\ContentArticleDictDeleteValidator.java'
)
foreach ($file in $files) {
    git diff --no-index --word-diff=plain -- (Join-Path $baseline $file) (Join-Path $source $file)
}
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' `
  -pl ruoyi-modules/ruoyi-content -am `
  '-DskipTests=false' `
  '-Dtest=ContentArticleServiceImplTest,ContentArticlePublishPolicyTest,ContentArticleDictionaryServiceTest,ContentArticleDictDeleteValidatorTest,ContentArticleHtmlSanitizerTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: 差异只含注释；上述 26 个测试全部通过。

- [ ] **Step 6: 只提交已跟踪的 Service 实现注释**

```powershell
$file = 'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/impl/ContentArticleServiceImpl.java'
git add -- $file
git commit --only -m 'docs: explain article service rules' -- $file
```

Expected: 提交只含 `ContentArticleServiceImpl.java`；5 个未跟踪 support 文件保持未暂存。

---

### Task 5: 全量验证、审查和工作区保护

**Files:**
- Verify: `ruoyi-modules/ruoyi-content/src/main/java/**/*.java`
- Verify: `ruoyi-modules/ruoyi-content/src/test/java/**/*.java`
- Verify: `script/sql/update/**`
- Remove after review: `.superpowers/sdd/2026-08-10-ruoyi-content-comments/`

**Interfaces:**
- Consumes: Tasks 1–4 的全部注释变更和三个精确路径提交。
- Produces: 可审查的注释改进、全绿回归证据和未受污染的用户工作区。

- [ ] **Step 1: 全量比较 18 个文件与基线**

对已跟踪文件使用 `git diff --word-diff=plain`，对未跟踪文件使用快照比较：

```powershell
$source = 'ruoyi-modules\ruoyi-content\src\main\java'
$baseline = '.superpowers\sdd\2026-08-10-ruoyi-content-comments\baseline'
$baseSha = Get-Content -Raw -LiteralPath '.superpowers\sdd\2026-08-10-ruoyi-content-comments\base-sha.txt'
$untracked = @(git ls-files --others --exclude-standard -- $source)
foreach ($file in $untracked) {
    $relative = $file.Substring(($source -replace '\\','/').Length).TrimStart('/') -replace '/', '\'
    git diff --no-index --word-diff=plain -- (Join-Path $baseline $relative) $file
}
git diff --word-diff=plain "$($baseSha.Trim())..HEAD" -- $source
```

Expected: 所有变化均是 Javadoc、行内注释或其空白排版；无代码、注解、字符串和配置变化。

- [ ] **Step 2: 运行文章模块全量测试**

```powershell
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' `
  -pl ruoyi-modules/ruoyi-content -am `
  '-DskipTests=false' test
```

Expected: `ruoyi-content` 现有 50 个测试全部通过，`BUILD SUCCESS`。

- [ ] **Step 3: 检查格式、提交范围和 SQL 暂存保护**

```powershell
git diff --check
$stagedSql = @(git diff --cached --name-status -- script/sql/update)
$nonDeleteSql = @($stagedSql | Where-Object { $_ -notmatch '^D\s' })
$otherStaged = @(git diff --cached --name-status | Where-Object { $_ -notmatch 'script/sql/update/' })
"STAGED_SQL_UPDATE_COUNT=$($stagedSql.Count)"
"STAGED_SQL_NON_DELETE_COUNT=$($nonDeleteSql.Count)"
"OTHER_STAGED_COUNT=$($otherStaged.Count)"
git log -4 --oneline
```

Expected: `git diff --check` 无输出；SQL 数量为 40、非删除为 0、其他暂存为 0；最近提交只包括设计、计划和三个注释职责提交。

- [ ] **Step 4: 发起只读代码审查**

使用 `superpowers:requesting-code-review`，审查设计提交之后到当前 HEAD 的注释提交，并同时提供基线快照用于检查未跟踪文件。审查必须确认：

- 注释与实际代码一致；
- 没有误导性地承诺框架未提供的行为；
- 没有把实现细节描述成稳定公共契约；
- 没有生产逻辑变化；
- 未跟踪业务文件和 40 个 SQL 删除未进入提交。

- [ ] **Step 5: 清理临时快照**

审查通过后，先解析并确认目标位于 `.superpowers/sdd` 下，再删除：

```powershell
$target = (Resolve-Path -LiteralPath '.superpowers\sdd\2026-08-10-ruoyi-content-comments').Path
$allowed = (Resolve-Path -LiteralPath '.superpowers\sdd').Path
if (-not $target.StartsWith($allowed + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw '拒绝删除允许目录之外的路径'
}
Remove-Item -LiteralPath $target -Recurse -Force
```

Expected: 仅删除本任务的临时基线和审查文件，业务文件与 Git 状态不变。
