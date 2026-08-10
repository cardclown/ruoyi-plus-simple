# 公开文章列表与详情接口实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 新增只服务中安建设租户 `140872` 的匿名文章列表和详情接口，同时保留可复用的按租户查询已发布文章能力。

**架构：** 新建公开 Controller、单位门面和通用已发布文章查询服务三层。HTTP 请求不能提供租户编号；中安建设门面固定传入 `140872`；通用服务在动态租户上下文内调用不带用户数据权限的专用 Mapper SQL，SQL 再显式限定租户、已发布状态和未删除标志。

**技术栈：** Java 17、Spring Boot、Sa-Token、MyBatis-Plus、PostgreSQL、H2、JUnit 5、Mockito、AssertJ、Maven。

## 全局约束

- 公开路径必须精确为 `GET /content/article/public/list` 和 `GET /content/article/public/{articleId}`。
- 新公开 Controller 使用 `@SaIgnore`；现有后台 `ContentArticleController` 的权限注解和行为不得修改。
- 外部请求不得包含或控制租户编号；中安建设门面固定使用有效租户 `140872`，不得使用已删除租户 `420542`。
- 公开查询只返回 `tenant_id = '140872'`、`status = '1'`、`del_flag = '0'` 的文章。
- 草稿、已删除、其他租户或不存在的文章详情统一抛出 `ServiceException("文章不存在")`。
- 通用查询服务内部接收租户编号，但不写死具体单位；租户编号只由单位门面传入。
- 公开列表复用 `ContentArticleQuery`，只读取 `title` 和 `categoryDictCode`；其 `status` 字段必须被忽略。
- 公开响应不得暴露 `tenantId`、`status`、`publishBy`、创建人或更新人等后台字段。
- 所有提交写入 `company-website-backend`，不提交到 `5.X`；提交信息使用中文。
- 当前工作区可能存在其他并发任务修改。每次提交必须使用精确路径和 `git commit --only`，禁止 `git add .`。
- 模块测试使用绝对 Maven 命令：

```powershell
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' `
  -f ruoyi-modules/ruoyi-content/pom.xml '-DskipTests=false' test
```

---

### Task 1：新增公开文章响应模型

**Files:**
- Create: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/vo/ContentArticlePublicVo.java`
- Create: `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/domain/vo/ContentArticlePublicVoContractTest.java`

**Interfaces:**
- Consumes: `ContentArticle` 的公开展示字段。
- Produces: `ContentArticlePublicVo`，供通用服务、单位门面和公开 Controller 使用。

- [ ] **Step 1：编写公开字段契约失败测试**

新建测试，使用 Java Bean 属性描述符锁定公开响应字段集合，防止以后误加入租户或审计字段：

```java
package org.dromara.content.domain.vo;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.beans.Introspector;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ContentArticlePublicVoContractTest {

    @Test
    void exposesOnlyPublicArticleFields() throws Exception {
        assertThat(Arrays.stream(Introspector.getBeanInfo(ContentArticlePublicVo.class)
                .getPropertyDescriptors())
            .map(descriptor -> descriptor.getName())
            .filter(name -> !"class".equals(name)))
            .containsExactlyInAnyOrder(
                "articleId",
                "title",
                "summary",
                "content",
                "categoryDictCode",
                "tagIds",
                "coverOssId",
                "publishTime"
            );
    }
}
```

- [ ] **Step 2：运行测试并确认因类型不存在而失败**

```powershell
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' `
  -f ruoyi-modules/ruoyi-content/pom.xml `
  '-DskipTests=false' '-Dtest=ContentArticlePublicVoContractTest' test
```

Expected: FAIL，编译错误指向 `ContentArticlePublicVo` 不存在。

- [ ] **Step 3：实现最小公开响应对象**

```java
package org.dromara.content.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 官网匿名接口使用的文章响应对象，只暴露公开展示字段。
 */
@Data
public class ContentArticlePublicVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 文章 ID。 */
    private Long articleId;

    /** 文章标题。 */
    private String title;

    /** 文章简介。 */
    private String summary;

    /** 文章正文；列表查询不加载该字段。 */
    private String content;

    /** 分类字典编码。 */
    private Long categoryDictCode;

    /** 标签字典编码集合。 */
    private List<Long> tagIds;

    /** 封面 OSS 附件 ID。 */
    private Long coverOssId;

    /** 发布时间。 */
    private Date publishTime;
}
```

- [ ] **Step 4：运行公开字段契约测试并确认通过**

运行 Step 2 的命令。

Expected: 1 test，0 failures，`BUILD SUCCESS`。

- [ ] **Step 5：精确提交响应模型**

```powershell
git add -- `
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/vo/ContentArticlePublicVo.java' `
  'ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/domain/vo/ContentArticlePublicVoContractTest.java'
git commit --only -m '功能：新增公开文章响应模型' -- `
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/vo/ContentArticlePublicVo.java' `
  'ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/domain/vo/ContentArticlePublicVoContractTest.java'
```

---

### Task 2：新增显式租户隔离的已发布文章 Mapper 查询

**Files:**
- Modify: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/mapper/ContentArticleMapper.java`
- Modify: `ruoyi-modules/ruoyi-content/src/main/resources/mapper/content/ContentArticleMapper.xml`
- Create: `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/mapper/ContentArticlePublicMapperSqlTest.java`
- Modify: `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/mapper/ContentArticleMapperContractTest.java`

**Interfaces:**
- Consumes: 内部传入的 `tenantId`、标题、分类编码、文章 ID 和 MyBatis-Plus `Page<ContentArticle>`。
- Produces:
  - `Page<ContentArticle> selectPublishedArticlePage(Page<ContentArticle> page, String tenantId, String title, Long categoryDictCode)`
  - `ContentArticle selectPublishedArticleById(String tenantId, Long articleId)`

- [ ] **Step 1：编写 Mapper 权限边界和数据库行为失败测试**

在 `ContentArticleMapperContractTest` 增加测试，公开方法必须存在且不得带 `@DataPermission`：

```java
@Test
void publicReadEntryPointsDoNotRequireLoggedInUserDataPermission() {
    for (String methodName : new String[]{
        "selectPublishedArticlePage",
        "selectPublishedArticleById"
    }) {
        Method method = Arrays.stream(ContentArticleMapper.class.getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals(methodName))
            .findFirst()
            .orElseThrow();
        assertThat(method.getAnnotation(DataPermission.class)).as(methodName).isNull();
    }
}
```

新建 `ContentArticlePublicMapperSqlTest`，使用 H2 PostgreSQL 模式实际加载 `ContentArticleMapper.xml`。测试表至少包含 `article_id`、`tenant_id`、标题、简介、正文、分类、标签、封面、状态、发布人、发布时间和删除标志。插入以下数据：

```java
insert(100L, "140872", "公开一", 11L, "1", "0");
insert(101L, "140872", "草稿", 11L, "0", "0");
insert(102L, "140872", "已删除", 11L, "1", "1");
insert(103L, "420542", "其他租户", 11L, "1", "0");
insert(104L, "140872", "公开二", 12L, "1", "0");
```

核心断言：

```java
@Test
void listReturnsOnlyPublishedUndeletedRowsFromRequestedTenant() throws Exception {
    try (SqlSession session = sqlSessionFactory(dataSource()).openSession(true)) {
        ContentArticleMapper mapper = session.getMapper(ContentArticleMapper.class);
        Page<ContentArticle> result = mapper.selectPublishedArticlePage(
            new Page<>(1, 10), "140872", null, null);

        assertThat(result.getRecords())
            .extracting(ContentArticle::getArticleId)
            .containsExactly(104L, 100L);
        assertThat(result.getRecords())
            .allSatisfy(article -> assertThat(article.getContent()).isNull());
    }
}

@Test
void listAppliesTitleAndCategoryWithoutAcceptingStatus() throws Exception {
    try (SqlSession session = sqlSessionFactory(dataSource()).openSession(true)) {
        ContentArticleMapper mapper = session.getMapper(ContentArticleMapper.class);
        Page<ContentArticle> result = mapper.selectPublishedArticlePage(
            new Page<>(1, 10), "140872", "公开一", 11L);

        assertThat(result.getRecords())
            .extracting(ContentArticle::getArticleId)
            .containsExactly(100L);
    }
}

@Test
void detailRejectsDraftDeletedAndOtherTenantRows() throws Exception {
    try (SqlSession session = sqlSessionFactory(dataSource()).openSession(true)) {
        ContentArticleMapper mapper = session.getMapper(ContentArticleMapper.class);

        assertThat(mapper.selectPublishedArticleById("140872", 100L)).isNotNull();
        assertThat(mapper.selectPublishedArticleById("140872", 101L)).isNull();
        assertThat(mapper.selectPublishedArticleById("140872", 102L)).isNull();
        assertThat(mapper.selectPublishedArticleById("140872", 103L)).isNull();
    }
}
```

测试类必须包含实际建库和 Mapper 加载代码，不通过读取 XML 文本代替数据库行为验证：

```java
private static DataSource dataSource() throws SQLException {
    DataSource dataSource = new PooledDataSource(
        "org.h2.Driver",
        "jdbc:h2:mem:public_article_" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "sa",
        ""
    );
    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement()) {
        statement.execute("""
            create table content_article
            (
                article_id bigint primary key,
                tenant_id varchar(20) not null,
                title varchar(200) not null,
                summary varchar(500),
                content varchar(2000),
                category_dict_code bigint not null,
                tag_ids varchar(200),
                cover_oss_id bigint,
                status char(1) not null,
                publish_by bigint,
                publish_time timestamp,
                del_flag char(1) not null
            )
            """);
        insert(statement, 100L, "140872", "公开一", 11L, "1", "0");
        insert(statement, 101L, "140872", "草稿", 11L, "0", "0");
        insert(statement, 102L, "140872", "已删除", 11L, "1", "1");
        insert(statement, 103L, "420542", "其他租户", 11L, "1", "0");
        insert(statement, 104L, "140872", "公开二", 12L, "1", "0");
    }
    return dataSource;
}

private static void insert(Statement statement, Long articleId, String tenantId,
                           String title, Long categoryDictCode,
                           String status, String delFlag) throws SQLException {
    statement.executeUpdate("""
        insert into content_article
            (article_id, tenant_id, title, summary, content,
             category_dict_code, tag_ids, cover_oss_id,
             status, publish_by, publish_time, del_flag)
        values
            (%d, '%s', '%s', '简介', '正文', %d, '21,22', 99,
             '%s', 9, timestamp '2026-08-10 10:00:00', '%s')
        """.formatted(articleId, tenantId, title, categoryDictCode, status, delFlag));
}

private static SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
    MybatisConfiguration configuration = new MybatisConfiguration();
    configuration.setMapUnderscoreToCamelCase(true);
    configuration.setEnvironment(new Environment(
        "test", new JdbcTransactionFactory(), dataSource));

    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
    configuration.addInterceptor(interceptor);

    String resource = "mapper/content/ContentArticleMapper.xml";
    try (InputStream input = Resources.getResourceAsStream(resource)) {
        new XMLMapperBuilder(
            input, configuration, resource, configuration.getSqlFragments()).parse();
    }
    return new MybatisSqlSessionFactoryBuilder().build(configuration);
}
```

测试类需要引入 `DbType`、`MybatisPlusInterceptor`、`PaginationInnerInterceptor`、MyBatis 配置类、JDBC 类型、`UUID` 和 `DataSource`。

- [ ] **Step 2：运行 Mapper 测试并确认方法缺失导致失败**

```powershell
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' `
  -f ruoyi-modules/ruoyi-content/pom.xml `
  '-DskipTests=false' '-Dtest=ContentArticleMapperContractTest,ContentArticlePublicMapperSqlTest' test
```

Expected: FAIL，编译或反射错误指向两个公开 Mapper 方法不存在。

- [ ] **Step 3：实现 Mapper 方法签名**

在 `ContentArticleMapper` 增加以下方法，不添加 `@DataPermission`：

```java
/**
 * 按指定租户分页查询已发布且未删除的公开文章。
 */
Page<ContentArticle> selectPublishedArticlePage(
    Page<ContentArticle> page,
    @Param("tenantId") String tenantId,
    @Param("title") String title,
    @Param("categoryDictCode") Long categoryDictCode);

/**
 * 按指定租户查询单篇已发布且未删除的公开文章。
 */
ContentArticle selectPublishedArticleById(
    @Param("tenantId") String tenantId,
    @Param("articleId") Long articleId);
```

- [ ] **Step 4：实现 XML 结果映射和显式过滤 SQL**

在 `ContentArticleMapper.xml` 增加公开结果映射：

```xml
<resultMap id="publicArticleResult" type="org.dromara.content.domain.ContentArticle">
    <id property="articleId" column="article_id"/>
    <result property="title" column="title"/>
    <result property="summary" column="summary"/>
    <result property="content" column="content"/>
    <result property="categoryDictCode" column="category_dict_code"/>
    <result property="tagIds" column="tag_ids"
            typeHandler="org.dromara.content.mybatis.LongListTypeHandler"/>
    <result property="coverOssId" column="cover_oss_id"/>
    <result property="publishTime" column="publish_time"/>
</resultMap>
```

增加列表 SQL，故意不选择正文：

```xml
<select id="selectPublishedArticlePage" resultMap="publicArticleResult">
    select article_id,
           title,
           summary,
           category_dict_code,
           tag_ids,
           cover_oss_id,
           publish_time
    from content_article
    where tenant_id = #{tenantId}
      and status = '1'
      and del_flag = '0'
    <if test="title != null and title != ''">
        and title like concat('%', #{title}, '%')
    </if>
    <if test="categoryDictCode != null">
        and category_dict_code = #{categoryDictCode}
    </if>
    order by article_id desc
</select>
```

增加详情 SQL：

```xml
<select id="selectPublishedArticleById" resultMap="publicArticleResult">
    select article_id,
           title,
           summary,
           content,
           category_dict_code,
           tag_ids,
           cover_oss_id,
           publish_time
    from content_article
    where tenant_id = #{tenantId}
      and article_id = #{articleId}
      and status = '1'
      and del_flag = '0'
</select>
```

- [ ] **Step 5：运行 Mapper 测试并确认通过**

运行 Step 2 的命令。

Expected: 公开查询相关测试全部通过；H2 只返回 `104、100`，草稿、删除数据和其他租户均不可见。

- [ ] **Step 6：精确提交 Mapper 查询**

```powershell
git add -- `
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/mapper/ContentArticleMapper.java' `
  'ruoyi-modules/ruoyi-content/src/main/resources/mapper/content/ContentArticleMapper.xml' `
  'ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/mapper/ContentArticlePublicMapperSqlTest.java' `
  'ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/mapper/ContentArticleMapperContractTest.java'
git commit --only -m '功能：新增已发布文章租户查询' -- `
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/mapper/ContentArticleMapper.java' `
  'ruoyi-modules/ruoyi-content/src/main/resources/mapper/content/ContentArticleMapper.xml' `
  'ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/mapper/ContentArticlePublicMapperSqlTest.java' `
  'ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/mapper/ContentArticleMapperContractTest.java'
```

---

### Task 3：封装通用已发布文章查询服务

**Files:**
- Create: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/support/ContentArticleTenantScope.java`
- Create: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/support/ContentArticlePublishedQueryService.java`
- Create: `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/service/support/ContentArticlePublishedQueryServiceTest.java`

**Interfaces:**
- Consumes: Task 1 的 `ContentArticlePublicVo`；Task 2 的两个 Mapper 方法；内部租户编号、`ContentArticleQuery` 和 `PageQuery`。
- Produces:
  - `TableDataInfo<ContentArticlePublicVo> queryPage(String tenantId, ContentArticleQuery query, PageQuery pageQuery)`
  - `ContentArticlePublicVo queryById(String tenantId, Long articleId)`

- [ ] **Step 1：编写通用查询服务失败测试**

测试使用一个记录租户编号并直接执行回调的 `ContentArticleTenantScope` 测试替身，避免依赖登录上下文：

```java
private final AtomicReference<String> tenantSeen = new AtomicReference<>();
private final ContentArticleTenantScope tenantScope = new ContentArticleTenantScope() {
    @Override
    public <T> T execute(String tenantId, Supplier<T> action) {
        tenantSeen.set(tenantId);
        return action.get();
    }
};
```

至少增加以下测试：

```java
@Test
void pagePassesTenantAndOnlyAllowedFiltersToMapper() {
    ContentArticleQuery query = new ContentArticleQuery();
    query.setTitle("工程");
    query.setCategoryDictCode(11L);
    query.setStatus("0");
    Page<ContentArticle> page = new Page<>(1, 10);
    page.setTotal(1);
    page.setRecords(List.of(article(100L, "正文")));
    when(mapper.selectPublishedArticlePage(any(), eq("140872"), eq("工程"), eq(11L)))
        .thenReturn(page);

    TableDataInfo<ContentArticlePublicVo> result = service.queryPage(
        "140872", query, new PageQuery(10, 1));

    assertThat(tenantSeen.get()).isEqualTo("140872");
    assertThat(result.getTotal()).isEqualTo(1);
    assertThat(result.getRows()).singleElement().satisfies(vo -> {
        assertThat(vo.getArticleId()).isEqualTo(100L);
        assertThat(vo.getContent()).isEqualTo("正文");
    });
    verify(mapper).selectPublishedArticlePage(any(), eq("140872"), eq("工程"), eq(11L));
}

@Test
void detailReturnsOnlyPublicFieldsFromMapperResult() {
    when(mapper.selectPublishedArticleById("140872", 100L))
        .thenReturn(article(100L, "正文"));

    ContentArticlePublicVo result = service.queryById("140872", 100L);

    assertThat(tenantSeen.get()).isEqualTo("140872");
    assertThat(result.getArticleId()).isEqualTo(100L);
    assertThat(result.getContent()).isEqualTo("正文");
}

@Test
void detailUsesUniformNotFoundMessage() {
    when(mapper.selectPublishedArticleById("140872", 100L)).thenReturn(null);

    assertThatThrownBy(() -> service.queryById("140872", 100L))
        .isInstanceOf(ServiceException.class)
        .hasMessage("文章不存在");
}

@Test
void blankTenantIsRejectedBeforeQuery() {
    assertThatThrownBy(() -> service.queryById(" ", 100L))
        .isInstanceOf(ServiceException.class)
        .hasMessage("租户ID不能为空");

    verifyNoInteractions(mapper);
}
```

`article` 测试工厂必须同时填充内部字段，用于确认返回类型根本不提供这些属性：

```java
private static ContentArticle article(Long articleId, String content) {
    ContentArticle article = new ContentArticle();
    article.setArticleId(articleId);
    article.setTitle("标题");
    article.setSummary("简介");
    article.setContent(content);
    article.setCategoryDictCode(11L);
    article.setTagIds(List.of(21L));
    article.setCoverOssId(99L);
    article.setStatus("1");
    article.setPublishBy(9L);
    article.setPublishTime(new Date(1_000L));
    article.setTenantId("140872");
    return article;
}
```

- [ ] **Step 2：运行服务测试并确认类型缺失导致失败**

```powershell
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' `
  -f ruoyi-modules/ruoyi-content/pom.xml `
  '-DskipTests=false' '-Dtest=ContentArticlePublishedQueryServiceTest' test
```

Expected: FAIL，编译错误指向 `ContentArticleTenantScope` 和 `ContentArticlePublishedQueryService` 不存在。

- [ ] **Step 3：实现动态租户作用域适配器**

```java
package org.dromara.content.service.support;

import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 在指定租户上下文中执行文章查询，并保证线程变量在回调结束后清理。
 */
@Component
public class ContentArticleTenantScope {

    public <T> T execute(String tenantId, Supplier<T> action) {
        return TenantHelper.dynamic(tenantId, action);
    }
}
```

- [ ] **Step 4：实现通用查询服务**

核心实现如下：

```java
@RequiredArgsConstructor
@Service
public class ContentArticlePublishedQueryService {

    private final ContentArticleMapper articleMapper;
    private final ContentArticleTenantScope tenantScope;

    public TableDataInfo<ContentArticlePublicVo> queryPage(
            String tenantId, ContentArticleQuery query, PageQuery pageQuery) {
        validateTenantId(tenantId);
        ContentArticleQuery safeQuery = query == null ? new ContentArticleQuery() : query;
        return tenantScope.execute(tenantId, () -> {
            Page<ContentArticle> page = articleMapper.selectPublishedArticlePage(
                pageQuery.build(), tenantId, safeQuery.getTitle(), safeQuery.getCategoryDictCode());
            return TableDataInfo.build(page.convert(this::toPublicVo));
        });
    }

    public ContentArticlePublicVo queryById(String tenantId, Long articleId) {
        validateTenantId(tenantId);
        return tenantScope.execute(tenantId, () -> {
            ContentArticle article = articleMapper.selectPublishedArticleById(tenantId, articleId);
            if (article == null) {
                throw new ServiceException("文章不存在");
            }
            return toPublicVo(article);
        });
    }

    private void validateTenantId(String tenantId) {
        if (StringUtils.isBlank(tenantId)) {
            throw new ServiceException("租户ID不能为空");
        }
    }

    private ContentArticlePublicVo toPublicVo(ContentArticle article) {
        ContentArticlePublicVo vo = new ContentArticlePublicVo();
        vo.setArticleId(article.getArticleId());
        vo.setTitle(article.getTitle());
        vo.setSummary(article.getSummary());
        vo.setContent(article.getContent());
        vo.setCategoryDictCode(article.getCategoryDictCode());
        vo.setTagIds(article.getTagIds());
        vo.setCoverOssId(article.getCoverOssId());
        vo.setPublishTime(article.getPublishTime());
        return vo;
    }
}
```

引入 `org.dromara.common.core.utils.StringUtils`，不要读取 `query.getStatus()`。

- [ ] **Step 5：运行通用服务测试并确认通过**

运行 Step 2 的命令。

Expected: 所有通用服务测试通过，0 failures。

- [ ] **Step 6：精确提交通用查询封装**

```powershell
git add -- `
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/support/ContentArticleTenantScope.java' `
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/support/ContentArticlePublishedQueryService.java' `
  'ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/service/support/ContentArticlePublishedQueryServiceTest.java'
git commit --only -m '功能：封装通用公开文章查询' -- `
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/support/ContentArticleTenantScope.java' `
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/support/ContentArticlePublishedQueryService.java' `
  'ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/service/support/ContentArticlePublishedQueryServiceTest.java'
```

---

### Task 4：新增中安建设固定租户门面

**Files:**
- Create: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/IContentArticlePublicFacade.java`
- Create: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/impl/ZhongAnArticlePublicFacade.java`
- Create: `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/service/impl/ZhongAnArticlePublicFacadeTest.java`

**Interfaces:**
- Consumes: Task 3 的 `ContentArticlePublishedQueryService`。
- Produces:
  - `TableDataInfo<ContentArticlePublicVo> queryPage(ContentArticleQuery query, PageQuery pageQuery)`
  - `ContentArticlePublicVo queryById(Long articleId)`

- [ ] **Step 1：编写固定租户失败测试**

```java
@Tag("dev")
class ZhongAnArticlePublicFacadeTest {

    private final ContentArticlePublishedQueryService queryService =
        mock(ContentArticlePublishedQueryService.class);
    private final ZhongAnArticlePublicFacade facade =
        new ZhongAnArticlePublicFacade(queryService);

    @Test
    void pageAlwaysUsesZhongAnTenant() {
        ContentArticleQuery query = new ContentArticleQuery();
        PageQuery pageQuery = new PageQuery(10, 1);
        TableDataInfo<ContentArticlePublicVo> expected = TableDataInfo.build();
        when(queryService.queryPage("140872", query, pageQuery)).thenReturn(expected);

        assertThat(facade.queryPage(query, pageQuery)).isSameAs(expected);
        verify(queryService).queryPage("140872", query, pageQuery);
    }

    @Test
    void detailAlwaysUsesZhongAnTenant() {
        ContentArticlePublicVo expected = new ContentArticlePublicVo();
        when(queryService.queryById("140872", 100L)).thenReturn(expected);

        assertThat(facade.queryById(100L)).isSameAs(expected);
        verify(queryService).queryById("140872", 100L);
    }
}
```

- [ ] **Step 2：运行门面测试并确认类型缺失导致失败**

```powershell
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' `
  -f ruoyi-modules/ruoyi-content/pom.xml `
  '-DskipTests=false' '-Dtest=ZhongAnArticlePublicFacadeTest' test
```

Expected: FAIL，编译错误指向门面接口和实现不存在。

- [ ] **Step 3：实现公开门面接口**

```java
package org.dromara.content.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.content.domain.bo.ContentArticleQuery;
import org.dromara.content.domain.vo.ContentArticlePublicVo;

/**
 * 官网匿名文章查询门面，不向调用方暴露租户参数。
 */
public interface IContentArticlePublicFacade {

    TableDataInfo<ContentArticlePublicVo> queryPage(
        ContentArticleQuery query, PageQuery pageQuery);

    ContentArticlePublicVo queryById(Long articleId);
}
```

- [ ] **Step 4：实现中安建设门面**

```java
package org.dromara.content.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.content.domain.bo.ContentArticleQuery;
import org.dromara.content.domain.vo.ContentArticlePublicVo;
import org.dromara.content.service.IContentArticlePublicFacade;
import org.dromara.content.service.support.ContentArticlePublishedQueryService;
import org.springframework.stereotype.Service;

/**
 * 中安建设官网文章门面，固定绑定有效租户 140872。
 */
@RequiredArgsConstructor
@Service
public class ZhongAnArticlePublicFacade implements IContentArticlePublicFacade {

    private static final String ZHONG_AN_TENANT_ID = "140872";

    private final ContentArticlePublishedQueryService queryService;

    @Override
    public TableDataInfo<ContentArticlePublicVo> queryPage(
            ContentArticleQuery query, PageQuery pageQuery) {
        return queryService.queryPage(ZHONG_AN_TENANT_ID, query, pageQuery);
    }

    @Override
    public ContentArticlePublicVo queryById(Long articleId) {
        return queryService.queryById(ZHONG_AN_TENANT_ID, articleId);
    }
}
```

- [ ] **Step 5：运行门面测试并确认通过**

运行 Step 2 的命令。

Expected: 2 tests，0 failures，`BUILD SUCCESS`。

- [ ] **Step 6：精确提交中安建设门面**

```powershell
git add -- `
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/IContentArticlePublicFacade.java' `
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/impl/ZhongAnArticlePublicFacade.java' `
  'ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/service/impl/ZhongAnArticlePublicFacadeTest.java'
git commit --only -m '功能：绑定中安建设公开文章查询' -- `
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/IContentArticlePublicFacade.java' `
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/impl/ZhongAnArticlePublicFacade.java' `
  'ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/service/impl/ZhongAnArticlePublicFacadeTest.java'
```

---

### Task 5：开放匿名文章列表与详情 HTTP 接口

**Files:**
- Create: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/controller/ContentArticlePublicController.java`
- Create: `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/controller/ContentArticlePublicControllerContractTest.java`

**Interfaces:**
- Consumes: Task 4 的 `IContentArticlePublicFacade`。
- Produces:
  - `GET /content/article/public/list`
  - `GET /content/article/public/{articleId}`

- [ ] **Step 1：编写 Controller 契约失败测试**

```java
package org.dromara.content.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.validation.constraints.NotNull;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.content.domain.bo.ContentArticleQuery;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ContentArticlePublicControllerContractTest {

    @Test
    void controllerIsAnonymousAndHasFixedBasePath() {
        assertThat(ContentArticlePublicController.class.getAnnotation(SaIgnore.class)).isNotNull();
        RequestMapping mapping = ContentArticlePublicController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping.value()).containsExactly("/content/article/public");
        assertThat(ContentArticlePublicController.class.getAnnotation(SaCheckPermission.class)).isNull();
    }

    @Test
    void exposesAnonymousPagedList() throws Exception {
        Method method = ContentArticlePublicController.class.getDeclaredMethod(
            "list", ContentArticleQuery.class, PageQuery.class);
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/list");
        assertThat(method.getAnnotation(SaCheckPermission.class)).isNull();
        assertThat(method.getParameters()[0].getAnnotations())
            .extracting(annotation -> annotation.annotationType().getName())
            .contains("org.springdoc.core.annotations.ParameterObject");
        assertThat(method.getParameters()[1].getAnnotations())
            .extracting(annotation -> annotation.annotationType().getName())
            .contains("org.springdoc.core.annotations.ParameterObject");
    }

    @Test
    void exposesAnonymousDetailWithRequiredId() throws Exception {
        Method method = ContentArticlePublicController.class.getDeclaredMethod(
            "getInfo", Long.class);
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/{articleId}");
        assertThat(method.getAnnotation(SaCheckPermission.class)).isNull();
        NotNull notNull = method.getParameters()[0].getAnnotation(NotNull.class);
        assertThat(notNull).isNotNull();
        assertThat(notNull.message()).isEqualTo("文章ID不能为空");
    }

    @Test
    void existingManagementReadsRemainPermissionProtected() throws Exception {
        Method list = ContentArticleController.class.getDeclaredMethod(
            "list", ContentArticleQuery.class, PageQuery.class);
        Method detail = ContentArticleController.class.getDeclaredMethod("getInfo", Long.class);

        assertThat(list.getAnnotation(SaCheckPermission.class).value())
            .containsExactly("content:article:list");
        assertThat(detail.getAnnotation(SaCheckPermission.class).value())
            .containsExactly("content:article:query");
    }
}
```

- [ ] **Step 2：运行 Controller 契约测试并确认类型缺失导致失败**

```powershell
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' `
  -f ruoyi-modules/ruoyi-content/pom.xml `
  '-DskipTests=false' '-Dtest=ContentArticlePublicControllerContractTest' test
```

Expected: FAIL，编译错误指向 `ContentArticlePublicController` 不存在。

- [ ] **Step 3：实现匿名公开 Controller**

```java
package org.dromara.content.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.content.domain.bo.ContentArticleQuery;
import org.dromara.content.domain.vo.ContentArticlePublicVo;
import org.dromara.content.service.IContentArticlePublicFacade;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 中安建设官网匿名文章查询接口。
 */
@SaIgnore
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/content/article/public")
public class ContentArticlePublicController {

    private final IContentArticlePublicFacade articlePublicFacade;

    /**
     * 分页查询中安建设已发布文章；请求中的状态参数不会参与查询。
     */
    @GetMapping("/list")
    public TableDataInfo<ContentArticlePublicVo> list(
            @ParameterObject ContentArticleQuery query,
            @ParameterObject PageQuery pageQuery) {
        return articlePublicFacade.queryPage(query, pageQuery);
    }

    /**
     * 查询中安建设单篇已发布文章。
     */
    @GetMapping("/{articleId}")
    public R<ContentArticlePublicVo> getInfo(
            @NotNull(message = "文章ID不能为空") @PathVariable Long articleId) {
        return R.ok(articlePublicFacade.queryById(articleId));
    }
}
```

- [ ] **Step 4：运行 Controller 契约测试并确认通过**

运行 Step 2 的命令。

Expected: 4 tests，0 failures，`BUILD SUCCESS`。

- [ ] **Step 5：精确提交匿名接口**

```powershell
git add -- `
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/controller/ContentArticlePublicController.java' `
  'ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/controller/ContentArticlePublicControllerContractTest.java'
git commit --only -m '功能：开放文章列表与详情接口' -- `
  'ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/controller/ContentArticlePublicController.java' `
  'ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/controller/ContentArticlePublicControllerContractTest.java'
```

---

### Task 6：全量验证、审查并推送 company 分支

**Files:**
- Verify: `ruoyi-modules/ruoyi-content/src/main/java/**/*.java`
- Verify: `ruoyi-modules/ruoyi-content/src/main/resources/mapper/content/ContentArticleMapper.xml`
- Verify: `ruoyi-modules/ruoyi-content/src/test/java/**/*.java`
- Preserve: 所有与本任务无关的工作区修改和提交。

**Interfaces:**
- Consumes: Tasks 1–5 的所有代码、测试和中文提交。
- Produces: 已验证并推送到 `personal/company-website-backend` 的两个匿名公开接口。

- [ ] **Step 1：运行文章模块完整测试**

```powershell
& 'C:\Users\dj\.m2\wrapper\dists\apache-maven-3.9.12-bin\7v3camr5asa67op289n7tbbbh2\apache-maven-3.9.12\bin\mvn.cmd' `
  -f ruoyi-modules/ruoyi-content/pom.xml '-DskipTests=false' test
```

Expected: 所有测试通过，0 failures，0 errors，`BUILD SUCCESS`。以执行时输出的实际测试数量为准。

- [ ] **Step 2：检查格式和提交范围**

```powershell
git diff --check
git status --short
git log -8 --oneline
```

Expected: 本任务文件没有空白错误；本任务没有遗留未提交文件；其他并发任务修改保持原状态；本任务提交信息全部为中文。

- [ ] **Step 3：逐条核对安全契约**

只读检查必须确认：

- 公开 Controller 类上存在 `@SaIgnore`，没有 `@SaCheckPermission`。
- HTTP 参数中没有 `tenantId`。
- 中安建设门面唯一固定值为 `140872`，代码中没有使用 `420542`。
- 通用查询方法仍接收内部 `tenantId`，没有绑定具体单位名称。
- Mapper 公开 SQL 同时包含 `tenant_id`、`status = '1'`、`del_flag = '0'`。
- 公开 Mapper 方法没有 `@DataPermission`；后台 Mapper 方法仍保留原注解。
- 公开详情未命中时错误消息精确为 `文章不存在`。
- 公开响应对象没有租户、状态、发布人或更新人字段。

- [ ] **Step 4：执行最终代码审查**

使用 `superpowers:requesting-code-review` 检查设计符合性、安全边界、数据库查询、错误处理、测试质量和并发工作区隔离。Critical 和 Important 问题必须修复并重新运行受影响测试。

- [ ] **Step 5：完成前重新验证并推送 company 分支**

使用 `superpowers:verification-before-completion` 重新运行完整测试并读取完整结果，然后核对当前分支和远端：

```powershell
git branch --show-current
git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'
git push personal company-website-backend
git ls-remote personal refs/heads/company-website-backend
```

Expected: 当前分支为 `company-website-backend`；推送成功；远端 SHA 与本地 `HEAD` 完全一致；不向 `5.X` 提交或推送。
