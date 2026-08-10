# 文章图片附件与视频上传 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为文章新增最多 10 张图片附件和最多 5 个视频，使用关联表保存 OSS ID，经后端流式上传至 MinIO，并完整处理回显、删除和临时文件清理。

**Architecture:** 文件模块负责流式上传、OSS 元数据、临时/绑定状态、动态 URL 和物理文件删除；文章模块通过 `content_article_attachment` 管理图片与视频的顺序和生命周期。文章接口只返回两组 OSS ID，前端统一调用文件模块获取当前 URL，不保存永久 URL。

**Tech Stack:** Java 21、Spring Boot 3.5.15、MyBatis-Plus、AWS SDK for Java 2.28.22、MinIO、PostgreSQL、Vue 3、TypeScript、Element Plus、Vitest

## Global Constraints

- 页面和文章接口只保留 `attachmentOssIds`、`videoOssIds` 两个附件字段，不保留 `coverOssId` 或 `videoOssId`。
- `attachmentOssIds` 仅允许 JPG、JPEG、PNG、WebP、GIF；最多 10 张；单张最大 50 MiB。
- `videoOssIds` 允许 MP4、AVI、MOV、WebM、MKV、WMV、FLV；最多 5 个；单个最大 2 GiB。
- 文章和关联表只保存 OSS ID，不保存永久 URL；回显统一使用 `/resource/oss/listByIds/{ossIds}`。
- 浏览器到后端是普通 multipart 上传，不实现浏览器断点续传；后端到 MinIO 使用 SDK 自动 multipart。
- SDK multipart 最小分片和阈值均为 32 MiB；Netty 连接并发保持现有上限，业务侧不额外并行启动多个文件流。
- 本地 multipart 临时文件 24 小时后才可清理，扫描周期 1 小时；MinIO stale multipart 过期 24 小时、清理周期 6 小时。
- 逻辑删除文章保留图片和视频；物理删除文章同时删除图片、视频、关系记录和 `sys_oss` 记录。
- 文章列表必须返回 `content`、`attachmentOssIds`、`videoOssIds`，附件关系必须按整页批量查询，不能产生 N+1。
- 新增业务查询全部使用 MyBatis-Plus 包装器和批量方法；只允许为表结构迁移及现有逻辑删除绕过场景使用 SQL/XML。
- 不引入 FFmpeg、浏览器直传 MinIO、永久 URL、附件共享或引用计数。
- 两个仓库分别提交：后端位于 `C:/Users/dj/ruoyi-plus/simple/RuoYi-Vue-Plus`，前端位于 `C:/Users/dj/ruoyi-plus/simple/plus-ui`。

---

### Task 1: 建立文章附件表并迁移历史封面

**Files:**
- Modify: `script/sql/postgres/content_article.sql`
- Create: `script/sql/postgres/content_article_attachment_migration.sql`
- Modify: `ruoyi-admin/src/main/resources/application.yml`
- Create: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/ContentArticleAttachment.java`
- Create: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/enums/ContentArticleAttachmentType.java`
- Create: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/mapper/ContentArticleAttachmentMapper.java`
- Create: `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/mapper/ContentArticleAttachmentMapperContractTest.java`

**Interfaces:**
- Consumes: `BaseMapperPlus<T, V>`、现有 `content_article` 和 `content_article_tag` 表约定。
- Produces: `ContentArticleAttachment`、`ContentArticleAttachmentType.IMAGE/VIDEO`、`ContentArticleAttachmentMapper.selectByArticleIds(Collection<Long>)`、`deleteByArticleIds(Collection<Long>)`。

- [ ] **Step 1: 写关联 Mapper 契约测试**

```java
@Tag("dev")
class ContentArticleAttachmentMapperContractTest {

    @Test
    void exposesWrapperBasedBatchOperations() throws Exception {
        assertThat(ContentArticleAttachmentMapper.class
            .getMethod("selectByArticleIds", Collection.class).isDefault()).isTrue();
        assertThat(ContentArticleAttachmentMapper.class
            .getMethod("deleteByArticleIds", Collection.class).isDefault()).isTrue();
    }

    @Test
    void attachmentTypeCodesAreStable() {
        assertThat(ContentArticleAttachmentType.IMAGE.getCode()).isEqualTo("0");
        assertThat(ContentArticleAttachmentType.VIDEO.getCode()).isEqualTo("1");
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
mvn -pl ruoyi-modules/ruoyi-content -am -DskipTests=false -Dtest=ContentArticleAttachmentMapperContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，提示 `ContentArticleAttachmentMapper` 或 `ContentArticleAttachmentType` 不存在。

- [ ] **Step 3: 创建实体、枚举和 MyBatis-Plus Mapper**

```java
@Data
@TableName("content_article_attachment")
public class ContentArticleAttachment implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId("article_attachment_id")
    private Long articleAttachmentId;
    private Long articleId;
    private Long ossId;
    private String attachmentType;
    private Integer sortNum;
    private Long createBy;
    private Date createTime;
}
```

```java
@Getter
@RequiredArgsConstructor
public enum ContentArticleAttachmentType {
    IMAGE("0"),
    VIDEO("1");

    private final String code;
}
```

```java
public interface ContentArticleAttachmentMapper
    extends BaseMapperPlus<ContentArticleAttachment, ContentArticleAttachment> {

    default List<ContentArticleAttachment> selectByArticleIds(Collection<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<ContentArticleAttachment>()
            .in(ContentArticleAttachment::getArticleId, articleIds)
            .orderByAsc(ContentArticleAttachment::getArticleId)
            .orderByAsc(ContentArticleAttachment::getAttachmentType)
            .orderByAsc(ContentArticleAttachment::getSortNum));
    }

    default int deleteByArticleIds(Collection<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return 0;
        }
        return delete(new LambdaQueryWrapper<ContentArticleAttachment>()
            .in(ContentArticleAttachment::getArticleId, articleIds));
    }
}
```

- [ ] **Step 4: 更新基础 SQL 和幂等迁移 SQL**

基础脚本从 `content_article` 删除 `cover_oss_id`，并创建下列表结构：

```sql
create table if not exists content_article_attachment
(
    article_attachment_id bigint,
    article_id            bigint      not null,
    oss_id                bigint      not null,
    attachment_type       char(1)     not null,
    sort_num              integer     not null default 0,
    create_by             bigint,
    create_time           timestamp,
    constraint content_article_attachment_pk primary key (article_attachment_id),
    constraint content_article_attachment_oss_uk unique (oss_id),
    constraint content_article_attachment_type_ck check (attachment_type in ('0', '1'))
);

create index if not exists content_article_attachment_article_type_sort_idx
    on content_article_attachment (article_id, attachment_type, sort_num);
```

迁移脚本按以下顺序执行：创建关联表；检查非空 `cover_oss_id` 是否重复；把历史封面写成 `attachment_type='0'`、`sort_num=0`；确认迁移数量一致；最后删除 `cover_oss_id`。冲突检查使用：

```sql
do $$
declare
    duplicated_oss_id bigint;
begin
    select cover_oss_id
      into duplicated_oss_id
      from content_article
     where cover_oss_id is not null
     group by cover_oss_id
    having count(*) > 1
     limit 1;

    if duplicated_oss_id is not null then
        raise exception '历史封面OSS ID被多篇文章复用: %', duplicated_oss_id;
    end if;
end
$$;
```

迁移插入使用 `max(article_attachment_id) + row_number()` 生成一次性关系主键，并通过 `not exists` 保证重复执行不会重复写入；只有历史记录迁移成功后才执行：

```sql
with source_rows as
(
    select article_id, cover_oss_id, create_by, create_time,
           row_number() over (order by article_id) as row_no
      from content_article
     where cover_oss_id is not null
       and not exists
       (
           select 1
             from content_article_attachment current_relation
            where current_relation.oss_id = content_article.cover_oss_id
       )
),
base as
(
    select coalesce(max(article_attachment_id), 0) as max_id
      from content_article_attachment
)
insert into content_article_attachment
    (article_attachment_id, article_id, oss_id, attachment_type, sort_num, create_by, create_time)
select base.max_id + source_rows.row_no,
       source_rows.article_id,
       source_rows.cover_oss_id,
       '0',
       0,
       source_rows.create_by,
       source_rows.create_time
  from source_rows
 cross join base;

do $$
begin
    if exists
    (
        select 1
          from content_article article
         where article.cover_oss_id is not null
           and not exists
           (
               select 1
                 from content_article_attachment relation
                where relation.article_id = article.article_id
                  and relation.oss_id = article.cover_oss_id
                  and relation.attachment_type = '0'
           )
    ) then
        raise exception '历史封面迁移数量校验失败';
    end if;
end
$$;

alter table content_article drop column if exists cover_oss_id;
```

- [ ] **Step 5: 配置无租户字段的关联表排除项**

在 `tenant.excludes` 增加：

```yaml
    - content_article_tag
    - content_article_attachment
```

- [ ] **Step 6: 运行测试和 SQL 静态检查**

Run:

```powershell
mvn -pl ruoyi-modules/ruoyi-content -am -DskipTests=false -Dtest=ContentArticleAttachmentMapperContractTest -Dsurefire.failIfNoSpecifiedTests=false test
rg -n "cover_oss_id|content_article_attachment|attachment_type" script/sql/postgres/content_article.sql script/sql/postgres/content_article_attachment_migration.sql
```

Expected: Java 测试 PASS；基础脚本不再定义 `cover_oss_id`；两个 SQL 文件都包含附件表和约束。

- [ ] **Step 7: 提交后端关系表任务**

```powershell
git add -- ruoyi-admin/src/main/resources/application.yml script/sql/postgres/content_article.sql script/sql/postgres/content_article_attachment_migration.sql ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/ContentArticleAttachment.java ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/enums/ContentArticleAttachmentType.java ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/mapper/ContentArticleAttachmentMapper.java ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/mapper/ContentArticleAttachmentMapperContractTest.java
git commit -m "feat(content): add article attachment relation"
```

### Task 2: 扩展通用 OSS 元数据、绑定和批量删除契约

**Files:**
- Modify: `ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/domain/dto/OssDTO.java`
- Modify: `ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/service/OssService.java`
- Create: `ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/support/OssClientProvider.java`
- Modify: `ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/ISysOssService.java`
- Modify: `ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/impl/SysOssServiceImpl.java`
- Create: `ruoyi-modules/ruoyi-system/src/test/java/org/dromara/system/service/impl/SysOssServiceImplTest.java`

**Interfaces:**
- Consumes: `SysOss.ext1` JSON、`SysOssExt`、`OssFactory`、`CacheNames.SYS_OSS`。
- Produces: `OssService.selectByIds(Collection<Long>)`、`bindToBusiness(Collection<Long>, String, String)`、`deleteByIds(Collection<Long>)`；`OssDTO` 暴露文件大小、MIME 和绑定状态。

- [ ] **Step 1: 写批量元数据和绑定失败测试**

```java
@Test
void selectByIdsUsesOneBatchQueryAndParsesExtMetadata() {
    SysOssVo vo = new SysOssVo();
    vo.setOssId(10L);
    vo.setFileSuffix(".png");
    vo.setExt1("{\"fileSize\":52428800,\"contentType\":\"image/png\",\"isTemp\":true}");
    when(mapper.selectVoByIds(List.of(10L))).thenReturn(List.of(vo));

    List<OssDTO> result = service.selectByIds(List.of(10L));

    assertThat(result).singleElement().satisfies(file -> {
        assertThat(file.getFileSize()).isEqualTo(52_428_800L);
        assertThat(file.getContentType()).isEqualTo("image/png");
        assertThat(file.getIsTemp()).isTrue();
    });
    verify(mapper).selectVoByIds(List.of(10L));
}

@Test
void bindRejectsMissingOssIds() {
    when(mapper.selectByIds(List.of(10L, 11L))).thenReturn(List.of(oss(10L)));

    assertThatThrownBy(() -> service.bindToBusiness(
        List.of(10L, 11L), "content_article", "100"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("部分附件不存在或不属于当前租户");
}

private static SysOss oss(Long ossId) {
    SysOss oss = new SysOss();
    oss.setOssId(ossId);
    oss.setExt1("{\"isTemp\":true}");
    return oss;
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
mvn -pl ruoyi-modules/ruoyi-system -am -DskipTests=false -Dtest=SysOssServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，提示新的 DTO 字段和服务方法不存在。

- [ ] **Step 3: 扩展通用接口和 DTO**

`OssDTO` 增加：

```java
private Long fileSize;
private String contentType;
private String bizType;
private String refId;
private String refType;
private Boolean isTemp;
```

`OssService` 增加：

```java
List<OssDTO> selectByIds(Collection<Long> ossIds);

void bindToBusiness(Collection<Long> ossIds, String refType, String refId);

void deleteByIds(Collection<Long> ossIds);
```

保留现有 `selectByIds(String)` 和 `selectUrlByIds(String)`，避免破坏已有调用方。

- [ ] **Step 4: 引入可测试的 OSS 客户端提供器**

```java
@Component
public class OssClientProvider {
    public OssClient current() {
        return OssFactory.instance();
    }

    public OssClient byService(String service) {
        return OssFactory.instance(service);
    }
}
```

`SysOssServiceImpl` 注入 `OssClientProvider`，上传、下载和删除不再直接静态调用 `OssFactory`。

- [ ] **Step 5: 使用 MyBatis-Plus 批量读取并解析 ext1**

`selectByIds(Collection<Long>)` 先去重保持顺序，再调用一次 `baseMapper.selectVoByIds(ids)`；把查询结果按 ID 映射回请求顺序。解析方法固定为：

```java
private OssDTO toDto(SysOssVo vo) {
    OssDTO dto = BeanUtil.toBean(vo, OssDTO.class);
    SysOssExt ext = StringUtils.isBlank(vo.getExt1())
        ? new SysOssExt()
        : JsonUtils.parseObject(vo.getExt1(), SysOssExt.class);
    dto.setFileSize(ext.getFileSize());
    dto.setContentType(ext.getContentType());
    dto.setBizType(ext.getBizType());
    dto.setRefId(ext.getRefId());
    dto.setRefType(ext.getRefType());
    dto.setIsTemp(ext.getIsTemp());
    return dto;
}
```

同时把控制器使用的 `listByIds(Collection<Long>)` 改成一次 `selectVoByIds`，然后逐项调用 `matchingUrl`，消除当前逐 ID 查库。

- [ ] **Step 6: 实现绑定、内部删除和缓存失效**

`bindToBusiness` 必须校验查询数量一致，更新 `SysOssExt.refType/refId/isTemp=false`，使用 `updateBatchById`，并逐个执行：

```java
CacheUtils.evict(CacheNames.SYS_OSS, ossId);
```

`deleteByIds` 是文章模块使用的内部强制删除：先批量读取记录，再按 `service` 获取客户端删除对象，全部对象删除成功后批量删除 `sys_oss` 并清缓存。公开的 `deleteWithValidByIds(..., true)` 对 `isTemp=false && refType!=null` 的已绑定文件返回“附件已被业务使用，不能直接删除”。

- [ ] **Step 7: 运行系统模块测试**

```powershell
mvn -pl ruoyi-modules/ruoyi-system -am -DskipTests=false -Dtest=SysOssServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS；Mockito 验证批量读取只调用一次 Mapper。

- [ ] **Step 8: 提交通用 OSS 业务契约**

```powershell
git add -- ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/domain/dto/OssDTO.java ruoyi-common/ruoyi-common-core/src/main/java/org/dromara/common/core/service/OssService.java ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/support/OssClientProvider.java ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/ISysOssService.java ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/impl/SysOssServiceImpl.java ruoyi-modules/ruoyi-system/src/test/java/org/dromara/system/service/impl/SysOssServiceImplTest.java
git commit -m "feat(oss): add attachment binding metadata"
```

### Task 3: 把 OSS 上传改成真正流式并启用自动 multipart

**Files:**
- Modify: `ruoyi-common/ruoyi-common-oss/pom.xml`
- Modify: `ruoyi-common/ruoyi-common-oss/src/main/java/org/dromara/common/oss/core/OssClient.java`
- Create: `ruoyi-common/ruoyi-common-oss/src/test/java/org/dromara/common/oss/core/OssClientStreamingContractTest.java`
- Modify: `ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/impl/SysOssServiceImpl.java`
- Modify: `ruoyi-modules/ruoyi-system/src/test/java/org/dromara/system/service/impl/SysOssServiceImplTest.java`

**Interfaces:**
- Consumes: `OssClient.uploadSuffix(InputStream, String, Long, String)`、`BlockingInputStreamAsyncRequestBody`。
- Produces: 不缓冲整文件的输入流上传；32 MiB 自动 multipart；数据库失败时的对象补偿删除。

- [ ] **Step 1: 写禁止整文件缓冲的回归测试**

```java
@Test
void inputStreamUploadDoesNotMaterializeWholeFile() throws IOException {
    String source = Files.readString(Path.of(
        "src/main/java/org/dromara/common/oss/core/OssClient.java"));

    assertThat(source).doesNotContain("IoUtil.readBytes(inputStream)");
    assertThat(source).contains(".multipartEnabled(true)");
    assertThat(source).contains("minimumPartSizeInBytes(32L * 1024 * 1024)");
    assertThat(source).contains("thresholdInBytes(32L * 1024 * 1024)");
}
```

在 `ruoyi-common-oss/pom.xml` 增加测试依赖 `spring-boot-starter-test`，scope 为 `test`。

- [ ] **Step 2: 运行回归测试并确认失败**

```powershell
mvn -pl ruoyi-common/ruoyi-common-oss -am -DskipTests=false -Dtest=OssClientStreamingContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，源码仍包含 `IoUtil.readBytes(inputStream)` 且未启用 multipart。

- [ ] **Step 3: 启用 S3AsyncClient 自动 multipart**

在 `S3AsyncClient.builder()` 中加入：

```java
.multipartEnabled(true)
.multipartConfiguration(builder -> builder
    .thresholdInBytes(32L * 1024 * 1024)
    .minimumPartSizeInBytes(32L * 1024 * 1024))
```

保留现有 Netty `maxConcurrency(100)`；单文件分片并发由 SDK 管理，不再为每次请求创建新的线程池。

- [ ] **Step 4: 删除输入流转字节数组逻辑**

`upload(InputStream, String, Long, String)` 直接把传入流写入 `BlockingInputStreamAsyncRequestBody`。删除 `ByteArrayInputStream` 类型判断和 `IoUtil.readBytes`，保留 `contentLength(length)`，并在异常时取消上传 future。`OssClient.delete` 对异步删除调用 `.join()`，确保返回前对象已经删除。

- [ ] **Step 5: 让系统上传服务只使用 getInputStream**

```java
try (InputStream inputStream = file.getInputStream()) {
    uploadResult = storage.uploadSuffix(
        inputStream, suffix, file.getSize(), file.getContentType());
} catch (IOException e) {
    throw new ServiceException("读取上传文件失败", e);
}
```

删除 `file.getBytes()`。`buildResultEntity` 抛错时执行：

```java
storage.delete(uploadResult.getUrl());
```

然后重新抛出原始异常。

`buildResultEntity` 必须检查 `baseMapper.insert(oss) == 1`；返回值不是 1 时抛出 `ServiceException("文件信息保存失败")`，确保补偿删除路径能够被触发。

- [ ] **Step 6: 用拒绝 getBytes 的 MultipartFile 验证服务调用**

测试构造 `MockMultipartFile` 子类，覆盖 `getBytes()` 直接抛出 `AssertionError`，让 `getInputStream()` 返回小型流；mock `OssClient.uploadSuffix(InputStream.class, ...)` 返回固定 `UploadResult`。验证上传成功且从未调用 `getBytes()`。

- [ ] **Step 7: 运行 OSS 和系统测试**

```powershell
mvn -pl ruoyi-common/ruoyi-common-oss,ruoyi-modules/ruoyi-system -am -DskipTests=false -Dtest=OssClientStreamingContractTest,SysOssServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS。

- [ ] **Step 8: 提交流式上传改动**

```powershell
git add -- ruoyi-common/ruoyi-common-oss/pom.xml ruoyi-common/ruoyi-common-oss/src/main/java/org/dromara/common/oss/core/OssClient.java ruoyi-common/ruoyi-common-oss/src/test/java/org/dromara/common/oss/core/OssClientStreamingContractTest.java ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/impl/SysOssServiceImpl.java ruoyi-modules/ruoyi-system/src/test/java/org/dromara/system/service/impl/SysOssServiceImplTest.java
git commit -m "perf(oss): stream multipart uploads"
```

### Task 4: 增加文章媒体上传策略和持续清理任务

**Files:**
- Create: `ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/support/ArticleOssUploadPolicy.java`
- Create: `ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/support/MultipartTempFileCleaner.java`
- Create: `ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/support/OssTemporaryCleanupJob.java`
- Create: `ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/support/OssSchedulingConfiguration.java`
- Modify: `ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/controller/system/SysOssController.java`
- Modify: `ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/ISysOssService.java`
- Modify: `ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/impl/SysOssServiceImpl.java`
- Create: `ruoyi-modules/ruoyi-system/src/test/java/org/dromara/system/service/support/ArticleOssUploadPolicyTest.java`
- Create: `ruoyi-modules/ruoyi-system/src/test/java/org/dromara/system/service/support/MultipartTempFileCleanerTest.java`

**Interfaces:**
- Consumes: multipart `file` 和可选 `bizType`；`SysOssExt` 临时/绑定字段；`spring.servlet.multipart.location`。
- Produces: `upload(MultipartFile, String bizType)`；每小时本地临时目录和过期文章临时 OSS 清理。

- [ ] **Step 1: 写图片、视频边界测试**

```java
@ParameterizedTest
@ValueSource(strings = {"a.jpg", "a.jpeg", "a.png", "a.webp", "a.gif"})
void acceptsArticleImages(String name) {
    MultipartFile file = mockFile(name, "image/png", 50L * 1024 * 1024);
    assertThatCode(() -> policy.validate(file, "article_attachment"))
        .doesNotThrowAnyException();
}

@Test
void rejectsImageAboveFiftyMib() {
    MultipartFile file = mockFile("a.png", "image/png", 50L * 1024 * 1024 + 1);
    assertThatThrownBy(() -> policy.validate(file, "article_attachment"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("单张文章图片不能超过50MB");
}

@ParameterizedTest
@ValueSource(strings = {"mp4", "avi", "mov", "webm", "mkv", "wmv", "flv"})
void acceptsConfiguredVideoExtensions(String extension) {
    MultipartFile file = mockFile("a." + extension, mimeFor(extension), 2L * 1024 * 1024 * 1024);
    assertThatCode(() -> policy.validate(file, "article_video"))
        .doesNotThrowAnyException();
}

@Test
void rejectsVideoAboveTwoGib() {
    MultipartFile file = mockFile("a.mp4", "video/mp4", 2L * 1024 * 1024 * 1024 + 1);
    assertThatThrownBy(() -> policy.validate(file, "article_video"))
        .isInstanceOf(ServiceException.class)
        .hasMessage("单个文章视频不能超过2GB");
}

private static MultipartFile mockFile(String name, String contentType, long size) {
    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn(name);
    when(file.getContentType()).thenReturn(contentType);
    when(file.getSize()).thenReturn(size);
    when(file.isEmpty()).thenReturn(false);
    return file;
}

private static String mimeFor(String extension) {
    return switch (extension) {
        case "mp4" -> "video/mp4";
        case "avi" -> "video/x-msvideo";
        case "mov" -> "video/quicktime";
        case "webm" -> "video/webm";
        case "mkv" -> "video/x-matroska";
        case "wmv" -> "video/x-ms-wmv";
        case "flv" -> "video/x-flv";
        default -> throw new IllegalArgumentException(extension);
    };
}
```

- [ ] **Step 2: 写临时目录清理测试**

```java
@Test
void deletesOnlyFilesOlderThanTwentyFourHours() throws IOException {
    Path oldFile = Files.writeString(tempDir.resolve("old.tmp"), "old");
    Path recentFile = Files.writeString(tempDir.resolve("recent.tmp"), "recent");
    Files.setLastModifiedTime(oldFile, FileTime.from(Instant.parse("2026-08-08T00:00:00Z")));
    Files.setLastModifiedTime(recentFile, FileTime.from(Instant.parse("2026-08-09T12:30:00Z")));

    int deleted = cleaner.clean(tempDir, Instant.parse("2026-08-09T00:00:00Z"));

    assertThat(deleted).isEqualTo(1);
    assertThat(oldFile).doesNotExist();
    assertThat(recentFile).exists();
}
```

- [ ] **Step 3: 运行测试并确认失败**

```powershell
mvn -pl ruoyi-modules/ruoyi-system -am -DskipTests=false -Dtest=ArticleOssUploadPolicyTest,MultipartTempFileCleanerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，策略和清理器不存在。

- [ ] **Step 4: 实现上传策略**

`ArticleOssUploadPolicy` 定义精确常量：

```java
public static final String ARTICLE_ATTACHMENT = "article_attachment";
public static final String ARTICLE_VIDEO = "article_video";
public static final long MAX_IMAGE_BYTES = 50L * 1024 * 1024;
public static final long MAX_VIDEO_BYTES = 2L * 1024 * 1024 * 1024;
public static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
public static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "avi", "mov", "webm", "mkv", "wmv", "flv");
```

按扩展名维护允许 MIME 集合，扩展名和 `MultipartFile.getContentType()` 必须同时匹配。没有 `bizType` 的现有普通上传保持原行为。

- [ ] **Step 5: 扩展上传接口并记录临时状态**

控制器签名改为：

```java
public R<SysOssUploadVo> upload(
    @RequestPart("file") MultipartFile file,
    @RequestParam(value = "bizType", required = false) String bizType)
```

服务新增 `upload(MultipartFile file, String bizType)`；原 `upload(MultipartFile)` 委托给新方法并传 `null`。文章业务类型上传成功后写入：

```java
ext.setBizType(bizType);
ext.setSource("userUpload");
ext.setIsTemp(true);
```

- [ ] **Step 6: 实现运行期清理**

`MultipartTempFileCleaner.clean(Path root, Instant cutoff)` 只遍历普通文件，只删除 `lastModified < cutoff` 的条目，使用 `Files.deleteIfExists`；单文件异常记录日志后继续扫描。

清理器必须拒绝空路径、文件系统根目录和不存在的目录；`spring.servlet.multipart.location` 未配置时直接跳过本地扫描，不能把当前工作目录当成临时目录。

`OssTemporaryCleanupJob` 使用：

```java
@Scheduled(fixedDelayString = "${oss.cleanup.interval:PT1H}")
public void clean() {
    Instant cutoff = Instant.now().minus(Duration.ofHours(24));
    String location = multipartProperties.getLocation();
    if (StringUtils.isNotBlank(location)) {
        multipartTempFileCleaner.clean(Path.of(location), cutoff);
    }
    ossService.deleteExpiredArticleTemps(Date.from(cutoff));
}
```

增加配置类：

```java
@Configuration
@EnableScheduling
public class OssSchedulingConfiguration {
}
```

`deleteExpiredArticleTemps` 在 `TenantHelper.ignore` 中用 MyBatis-Plus 查找 `createTime < cutoff` 且 `ext1` 包含文章 `bizType` 的候选记录，解析 JSON 后只删除 `isTemp=true && refId为空` 的对象。

- [ ] **Step 7: 运行策略与清理测试**

```powershell
mvn -pl ruoyi-modules/ruoyi-system -am -DskipTests=false -Dtest=ArticleOssUploadPolicyTest,MultipartTempFileCleanerTest,SysOssServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS。

- [ ] **Step 8: 提交上传策略和清理任务**

```powershell
git add -- ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/controller/system/SysOssController.java ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/ISysOssService.java ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/impl/SysOssServiceImpl.java ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/support/ArticleOssUploadPolicy.java ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/support/MultipartTempFileCleaner.java ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/support/OssTemporaryCleanupJob.java ruoyi-modules/ruoyi-system/src/main/java/org/dromara/system/service/support/OssSchedulingConfiguration.java ruoyi-modules/ruoyi-system/src/test/java/org/dromara/system/service/support/ArticleOssUploadPolicyTest.java ruoyi-modules/ruoyi-system/src/test/java/org/dromara/system/service/support/MultipartTempFileCleanerTest.java
git commit -m "feat(oss): validate and clean article uploads"
```

### Task 5: 实现文章附件校验、同步和批量回显

**Files:**
- Create: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/support/ContentArticleAttachmentManager.java`
- Create: `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/service/support/ContentArticleAttachmentManagerTest.java`

**Interfaces:**
- Consumes: Task 1 的附件 Mapper；Task 2 的 `OssService` 批量元数据、绑定和删除方法；`ContentArticleOperationContext`。
- Produces: `replace(Long, List<Long>, List<Long>)`、`populate(ContentArticleVo)`、`populate(Collection<ContentArticleVo>)`、`deletePermanently(Collection<Long>)`。

- [ ] **Step 1: 写数量、类型和独占校验测试**

```java
@Test
void rejectsMoreThanTenImages() {
    List<Long> ids = LongStream.rangeClosed(1, 11).boxed().toList();
    assertThatThrownBy(() -> manager.replace(100L, ids, List.of()))
        .isInstanceOf(ServiceException.class)
        .hasMessage("文章图片最多上传10张");
}

@Test
void rejectsMoreThanFiveVideos() {
    assertThatThrownBy(() -> manager.replace(
        100L, List.of(), List.of(1L, 2L, 3L, 4L, 5L, 6L)))
        .isInstanceOf(ServiceException.class)
        .hasMessage("文章视频最多上传5个");
}

@Test
void rejectsOssBoundToAnotherArticle() {
    OssDTO file = image(10L);
    file.setRefType("content_article");
    file.setRefId("99");
    when(ossService.selectByIds(List.of(10L))).thenReturn(List.of(file));

    assertThatThrownBy(() -> manager.replace(100L, List.of(10L), List.of()))
        .isInstanceOf(ServiceException.class)
        .hasMessage("附件已绑定其他文章");
}
```

- [ ] **Step 2: 写批量回显和差异同步测试**

```java
@Test
void populatesOnePageWithOneRelationQuery() {
    ContentArticleVo first = articleVo(100L);
    ContentArticleVo second = articleVo(101L);
    when(mapper.selectByArticleIds(List.of(100L, 101L))).thenReturn(List.of(
        relation(100L, 10L, IMAGE, 0),
        relation(100L, 20L, VIDEO, 0),
        relation(101L, 11L, IMAGE, 0)));

    manager.populate(List.of(first, second));

    assertThat(first.getAttachmentOssIds()).containsExactly(10L);
    assertThat(first.getVideoOssIds()).containsExactly(20L);
    assertThat(second.getAttachmentOssIds()).containsExactly(11L);
    verify(mapper).selectByArticleIds(List.of(100L, 101L));
}

@Test
void replacingAttachmentsDeletesOnlyRemovedOss() {
    when(mapper.selectByArticleIds(List.of(100L))).thenReturn(List.of(
        relation(100L, 10L, IMAGE, 0), relation(100L, 20L, VIDEO, 0)));
    when(ossService.selectByIds(List.of(10L, 21L))).thenReturn(List.of(image(10L), video(21L)));

    manager.replace(100L, List.of(10L), List.of(21L));

    verify(ossService).deleteByIds(List.of(20L));
    verify(ossService).bindToBusiness(List.of(21L), "content_article", "100");
}

private static ContentArticleVo articleVo(Long articleId) {
    ContentArticleVo vo = new ContentArticleVo();
    vo.setArticleId(articleId);
    return vo;
}

private static ContentArticleAttachment relation(
    Long articleId, Long ossId, ContentArticleAttachmentType type, int sortNum) {
    ContentArticleAttachment relation = new ContentArticleAttachment();
    relation.setArticleId(articleId);
    relation.setOssId(ossId);
    relation.setAttachmentType(type.getCode());
    relation.setSortNum(sortNum);
    return relation;
}

private static OssDTO image(Long ossId) {
    OssDTO dto = new OssDTO();
    dto.setOssId(ossId);
    dto.setFileSuffix(".png");
    dto.setContentType("image/png");
    dto.setFileSize(1L);
    dto.setIsTemp(true);
    return dto;
}

private static OssDTO video(Long ossId) {
    OssDTO dto = new OssDTO();
    dto.setOssId(ossId);
    dto.setFileSuffix(".mp4");
    dto.setContentType("video/mp4");
    dto.setFileSize(1L);
    dto.setIsTemp(true);
    return dto;
}
```

- [ ] **Step 3: 运行测试并确认失败**

```powershell
mvn -pl ruoyi-modules/ruoyi-content -am -DskipTests=false -Dtest=ContentArticleAttachmentManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，Manager 不存在。

- [ ] **Step 4: 实现规范化和元数据校验**

`replace` 对 null 转为空列表，使用 `LinkedHashSet` 去重保序，检查两个列表交集。一次调用 `ossService.selectByIds(allIds)` 并要求返回数量一致。

图片校验使用 50 MiB、图片扩展名和 `image/*` MIME；视频校验使用 2 GiB、视频扩展名与对应 MIME。绑定检查规则为：`refType/refId` 都为空可绑定；`refType=content_article && refId=当前文章ID` 可继续使用；其他情况拒绝。

- [ ] **Step 5: 实现差异同步**

按 `ossId` 比较现有关系和目标关系：

- 新 ID 批量 `insertBatch`，写入类型、顺序、创建人和创建时间。
- 已存在但类型或顺序变化的关系批量 `updateBatchById`。
- 被移除的关系按主键批量删除，并调用 `ossService.deleteByIds(removedOssIds)`。
- 新 ID 调用 `ossService.bindToBusiness(newOssIds, "content_article", articleId.toString())`。

先完成全部校验，再执行任何数据库或 MinIO 变更。

- [ ] **Step 6: 实现批量回显和物理清理**

`populate(Collection<ContentArticleVo>)` 收集文章 ID，一次调用 `selectByArticleIds`，按文章和类型分组并按 `sortNum` 写入两个列表；无附件时写空列表而不是 null。

`deletePermanently(Collection<Long>)` 一次查询所有关系，对 OSS ID 去重后调用 `ossService.deleteByIds`，成功后调用 `mapper.deleteByArticleIds`。OSS 删除失败时抛错，不继续删除关系。

- [ ] **Step 7: 运行 Manager 测试**

```powershell
mvn -pl ruoyi-modules/ruoyi-content -am -DskipTests=false -Dtest=ContentArticleAttachmentManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS。

- [ ] **Step 8: 提交文章附件 Manager**

```powershell
git add -- ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/support/ContentArticleAttachmentManager.java ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/service/support/ContentArticleAttachmentManagerTest.java
git commit -m "feat(content): manage article media relations"
```

### Task 6: 接入文章新增、修改、列表、详情和物理删除

**Files:**
- Modify: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/ContentArticle.java`
- Modify: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/bo/ContentArticleBo.java`
- Modify: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/vo/ContentArticleVo.java`
- Modify: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/impl/ContentArticleServiceImpl.java`
- Modify: `ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/controller/ContentArticleController.java`
- Modify: `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/domain/bo/ContentArticleBoValidationTest.java`
- Modify: `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/domain/bo/ContentArticleRequestModelContractTest.java`
- Modify: `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/service/impl/ContentArticleServiceImplTest.java`
- Modify: `ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/controller/ContentArticleControllerContractTest.java`

**Interfaces:**
- Consumes: Task 5 的 `ContentArticleAttachmentManager`。
- Produces: 文章 JSON 中的 `attachmentOssIds`、`videoOssIds`；列表正文；`DELETE /content/article/physical/{articleIds}`。

- [ ] **Step 1: 更新请求模型测试**

```java
@Test
void acceptsTenImagesAndFiveVideos() {
    ContentArticleBo bo = validBo();
    bo.setAttachmentOssIds(LongStream.rangeClosed(1, 10).boxed().toList());
    bo.setVideoOssIds(LongStream.rangeClosed(11, 15).boxed().toList());
    assertThat(validator.validate(bo, AddGroup.class)).isEmpty();
}

@Test
void rejectsElevenImages() {
    ContentArticleBo bo = validBo();
    bo.setAttachmentOssIds(LongStream.rangeClosed(1, 11).boxed().toList());
    assertThat(validator.validate(bo, AddGroup.class))
        .extracting(ConstraintViolation::getMessage)
        .contains("文章图片最多上传10张");
}
```

- [ ] **Step 2: 更新服务行为测试**

新增断言：新增文章后调用 `attachmentManager.replace(新articleId, 图片列表, 视频列表)`；修改时调用相同接口；详情和列表调用 `populate`；逻辑删除从不调用 `deletePermanently`；物理删除先调用 `deletePermanently(articleIds)` 再清标签和文章。

```java
verify(attachmentManager).replace(100L, List.of(10L), List.of(20L));
verify(attachmentManager, never()).deletePermanently(any());
```

- [ ] **Step 3: 运行文章测试并确认失败**

```powershell
mvn -pl ruoyi-modules/ruoyi-content -am -DskipTests=false -Dtest=ContentArticleBoValidationTest,ContentArticleRequestModelContractTest,ContentArticleServiceImplTest,ContentArticleControllerContractTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL，两个列表字段和 Manager 调用尚不存在。

- [ ] **Step 4: 修改实体、BO 和 VO**

从 `ContentArticle`、BO、VO 删除 `coverOssId`。BO 增加：

```java
@JsonView(AddView.class)
@Size(max = 10, message = "文章图片最多上传{max}张", groups = {AddGroup.class, EditGroup.class})
private List<@NotNull(message = "文章图片附件ID不能为空") Long> attachmentOssIds;

@JsonView(AddView.class)
@Size(max = 5, message = "文章视频最多上传{max}个", groups = {AddGroup.class, EditGroup.class})
private List<@NotNull(message = "文章视频附件ID不能为空") Long> videoOssIds;
```

VO 增加同名 `List<Long>` 字段，不标注 `@ExcelProperty`，避免把数组直接导出为无意义列。

- [ ] **Step 5: 接入文章服务**

- `prepareArticle` 删除封面校验，不把两个附件列表映射进主表。
- 新增文章取得雪花 ID 后调用 `attachmentManager.replace`。
- 修改文章主体成功后调用 `attachmentManager.replace`。
- `queryById` 转 VO 后调用 `attachmentManager.populate(vo)`。
- 分页和普通列表构建完成后调用一次 `attachmentManager.populate(records)`。
- `buildQueryWrapper().select(...)` 增加 `ContentArticle::getContent`，删除 `getCoverOssId`。
- 逻辑删除保持附件不动。
- 物理删除在标签和文章记录前调用 `attachmentManager.deletePermanently(articleIds)`。

- [ ] **Step 6: 暴露物理删除接口并锁住权限契约**

```java
@SaCheckPermission("content:article:remove")
@Log(title = "文章", businessType = BusinessType.CLEAN)
@DeleteMapping("/physical/{articleIds}")
public R<Void> physicalRemove(
    @NotEmpty(message = "文章ID不能为空") @PathVariable Long[] articleIds) {
    contentArticleService.physicalDeleteByIds(List.of(articleIds));
    return R.ok();
}
```

控制器契约测试校验路径、HTTP DELETE、权限值和参数校验注解。

- [ ] **Step 7: 运行全部文章模块测试**

```powershell
mvn -pl ruoyi-modules/ruoyi-content -am -DskipTests=false -Dgroups=dev -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS；列表测试确认 wrapper 的 select 字段包含 `content`。

- [ ] **Step 8: 提交文章接口集成**

```powershell
git add -- ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/ContentArticle.java ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/bo/ContentArticleBo.java ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/domain/vo/ContentArticleVo.java ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/service/impl/ContentArticleServiceImpl.java ruoyi-modules/ruoyi-content/src/main/java/org/dromara/content/controller/ContentArticleController.java ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/domain/bo/ContentArticleBoValidationTest.java ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/domain/bo/ContentArticleRequestModelContractTest.java ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/service/impl/ContentArticleServiceImplTest.java ruoyi-modules/ruoyi-content/src/test/java/org/dromara/content/controller/ContentArticleControllerContractTest.java
git commit -m "feat(content): expose article images and videos"
```

### Task 7: 配置 2 GiB 上传链路和 MinIO stale multipart 清理

**Files:**
- Modify: `ruoyi-admin/src/main/resources/application.yml`
- Modify: `ruoyi-admin/src/main/resources/application-prod.yml`
- Modify: `script/docker/nginx/conf/nginx.conf`
- Modify: `script/docker/docker-compose.yml`

**Interfaces:**
- Consumes: Spring multipart、Nginx `/prod-api/` 代理、MinIO `api` 子系统。
- Produces: 支持 2 GiB 文件及 multipart 开销的请求链路；运行期间自动清理 24 小时无活动分片。

- [ ] **Step 1: 写配置断言脚本并确认当前值失败**

Run:

```powershell
rg -n "max-file-size: 2GB|max-request-size: 2200MB|client_max_body_size 2200m|MINIO_API_STALE_UPLOADS_EXPIRY|MINIO_API_STALE_UPLOADS_CLEANUP_INTERVAL" ruoyi-admin/src/main/resources/application.yml script/docker/nginx/conf/nginx.conf script/docker/docker-compose.yml
```

Expected: 当前至少缺少 Nginx 2.2 GiB 和 MinIO stale upload 配置。

- [ ] **Step 2: 修改 Spring multipart 配置**

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 2GB
      max-request-size: 2200MB
```

生产配置保留独立临时目录：

```yaml
spring.servlet.multipart.location: /ruoyi/server/temp
```

并新增：

```yaml
oss:
  cleanup:
    interval: PT1H
```

- [ ] **Step 3: 修改 Nginx 和 MinIO 容器配置**

Nginx：

```nginx
client_max_body_size 2200m;
```

在 `/prod-api/` 中保留 `proxy_read_timeout 86400s`，增加：

```nginx
proxy_send_timeout 86400s;
proxy_request_buffering off;
```

MinIO 环境变量：

```yaml
MINIO_API_STALE_UPLOADS_EXPIRY: "24h"
MINIO_API_STALE_UPLOADS_CLEANUP_INTERVAL: "6h"
```

- [ ] **Step 4: 验证配置解析和 Nginx 语法**

```powershell
mvn -pl ruoyi-admin -am -DskipTests package
docker run --rm -v "${PWD}/script/docker/nginx/conf/nginx.conf:/etc/nginx/nginx.conf:ro" nginx:alpine nginx -t
```

Expected: Maven BUILD SUCCESS；Nginx 输出 `syntax is ok` 和 `test is successful`。

- [ ] **Step 5: 验证 MinIO 运行配置**

应用 compose 更新后执行：

```powershell
docker compose -f script/docker/docker-compose.yml up -d minio
docker exec minio sh -c 'mc alias set local http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"'
docker exec minio mc admin config get local api
```

Expected: 输出包含 `stale_uploads_expiry=24h` 和 `stale_uploads_cleanup_interval=6h`。不创建 `--expire-days` bucket 规则。

- [ ] **Step 6: 提交配置改动**

```powershell
git add -- ruoyi-admin/src/main/resources/application.yml ruoyi-admin/src/main/resources/application-prod.yml script/docker/nginx/conf/nginx.conf script/docker/docker-compose.yml
git commit -m "config: allow large article uploads"
```

### Task 8: 增加前端文章媒体 API 和双上传控件

**Files:**
- Modify: `C:/Users/dj/ruoyi-plus/simple/plus-ui/src/api/system/oss/index.ts`
- Create: `C:/Users/dj/ruoyi-plus/simple/plus-ui/src/components/ArticleMediaUpload/mediaPolicy.ts`
- Create: `C:/Users/dj/ruoyi-plus/simple/plus-ui/src/components/ArticleMediaUpload/mediaPolicy.test.ts`
- Create: `C:/Users/dj/ruoyi-plus/simple/plus-ui/src/components/ArticleMediaUpload/index.vue`

**Interfaces:**
- Consumes: `/resource/oss/upload` 的 `file + bizType` multipart；`listByIds`；`delOss`。
- Produces: `<ArticleMediaUpload v-model="LongIdArray" kind="image|video">`；上传进度；图片预览、视频预览或下载。

- [ ] **Step 1: 写前端媒体规则测试**

```typescript
import { describe, expect, it } from 'vitest';
import { validateArticleMedia } from './mediaPolicy';

describe('validateArticleMedia', () => {
  it('accepts a 50 MiB image and rejects the next byte', () => {
    expect(validateArticleMedia({ name: 'a.webp', type: 'image/webp', size: 50 * 1024 * 1024 }, 'image')).toBeUndefined();
    expect(validateArticleMedia({ name: 'a.webp', type: 'image/webp', size: 50 * 1024 * 1024 + 1 }, 'image')).toBe('单张图片不能超过50MB');
  });

  it('accepts all configured video extensions', () => {
    for (const ext of ['mp4', 'avi', 'mov', 'webm', 'mkv', 'wmv', 'flv']) {
      expect(validateArticleMedia({ name: `a.${ext}`, type: 'video/mp4', size: 1 }, 'video')).toBeUndefined();
    }
  });
});
```

- [ ] **Step 2: 运行 Vitest 并确认失败**

```powershell
npx vitest run src/components/ArticleMediaUpload/mediaPolicy.test.ts
```

Expected: FAIL，`mediaPolicy` 不存在。

- [ ] **Step 3: 实现前端规则和上传 API**

```typescript
export type ArticleMediaKind = 'image' | 'video';

export const IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp', 'gif'] as const;
export const VIDEO_EXTENSIONS = ['mp4', 'avi', 'mov', 'webm', 'mkv', 'wmv', 'flv'] as const;

export function validateArticleMedia(file: Pick<File, 'name' | 'type' | 'size'>, kind: ArticleMediaKind): string | undefined {
  const extension = file.name.split('.').pop()?.toLowerCase() ?? '';
  const allowed = kind === 'image' ? IMAGE_EXTENSIONS : VIDEO_EXTENSIONS;
  const max = kind === 'image' ? 50 * 1024 * 1024 : 2 * 1024 * 1024 * 1024;
  if (!(allowed as readonly string[]).includes(extension)) return kind === 'image' ? '仅支持JPG/JPEG/PNG/WebP/GIF图片' : '视频格式不支持';
  if (file.size > max) return kind === 'image' ? '单张图片不能超过50MB' : '单个视频不能超过2GB';
  return undefined;
}
```

OSS API 增加：

```typescript
export function uploadArticleMedia(
  file: File,
  bizType: 'article_attachment' | 'article_video',
  onUploadProgress: (percent: number) => void
) {
  const data = new FormData();
  data.append('file', file);
  data.append('bizType', bizType);
  return request({
    url: '/resource/oss/upload',
    method: 'post',
    data,
    timeout: 0,
    headers: { repeatSubmit: false },
    onUploadProgress: (event) => {
      if (event.total) onUploadProgress(Math.round((event.loaded * 100) / event.total));
    }
  });
}
```

把 `listByIds` 参数扩展为数组并使用 `ossIds.join(',')`。

- [ ] **Step 4: 实现 ArticleMediaUpload**

组件 props：

```typescript
const props = withDefaults(defineProps<{
  modelValue: Array<string | number>;
  kind: ArticleMediaKind;
  disabled?: boolean;
}>(), { modelValue: () => [], disabled: false });
```

- `kind=image` 使用 picture-card，limit=10，上传 `article_attachment`。
- `kind=video` 使用文本列表和 `<video controls preload="metadata">`，limit=5，上传 `article_video`。
- watch ID 数组后一次 `listByIds` 回显。
- `http-request` 调用 `uploadArticleMedia` 并更新 Element Plus 进度。
- 成功时向数组尾部追加 `ossId`，保持顺序。
- 删除本次会话新上传且尚未提交的文件时调用 `delOss`；删除已回显文件只更新模型，由文章修改接口在保存成功时清理。
- 浏览器触发 `<video>` error 时隐藏播放器，显示文件名和下载链接。

- [ ] **Step 5: 运行组件规则测试和类型检查**

```powershell
npx vitest run src/components/ArticleMediaUpload/mediaPolicy.test.ts
npx vue-tsc --noEmit
```

Expected: PASS。

- [ ] **Step 6: 在前端仓库提交媒体控件**

```powershell
git add -- src/api/system/oss/index.ts src/components/ArticleMediaUpload/mediaPolicy.ts src/components/ArticleMediaUpload/mediaPolicy.test.ts src/components/ArticleMediaUpload/index.vue
git commit -m "feat(content): add article media uploader"
```

### Task 9: 创建文章 API 和管理页面并接入两个字段

**Files:**
- Create: `C:/Users/dj/ruoyi-plus/simple/plus-ui/src/api/content/article/types.ts`
- Create: `C:/Users/dj/ruoyi-plus/simple/plus-ui/src/api/content/article/index.ts`
- Create: `C:/Users/dj/ruoyi-plus/simple/plus-ui/src/views/content/article/index.vue`

**Interfaces:**
- Consumes: 文章 CRUD、分类选项、标签选项、Task 8 的 `ArticleMediaUpload`。
- Produces: 菜单 `content/article/index` 对应页面；新增和编辑提交两个 ID 数组；列表接收正文但不直接渲染。

- [ ] **Step 1: 定义精确的 API 类型**

```typescript
export interface ContentArticleForm {
  articleId?: string | number;
  title: string;
  summary: string;
  content: string;
  categoryDictCode?: string | number;
  tagIds: Array<string | number>;
  attachmentOssIds: Array<string | number>;
  videoOssIds: Array<string | number>;
  status: '0' | '1';
}

export interface ContentArticleVO extends ContentArticleForm, BaseEntity {
  articleId: string | number;
  publishBy?: string | number;
  publishTime?: string;
}

export interface ContentArticleQuery extends PageQuery {
  title?: string;
  categoryDictCode?: string | number;
  status?: '0' | '1';
}

export interface ArticleDictOption {
  dictCode: string | number;
  dictLabel: string;
}
```

- [ ] **Step 2: 实现文章 API 方法**

提供 `listArticle`、`getArticle`、`addArticle`、`updateArticle`、`delArticle`、`physicalDeleteArticle`、`getCategoryOptions`、`getTagOptions`。物理删除路径为 `/content/article/physical/{ids}`；普通删除路径保持 `/content/article/{ids}`。

- [ ] **Step 3: 创建文章列表和表单页面**

页面必须包含：

- 查询区：标题、分类、状态。
- 表格：标题、简介、分类、标签、状态、发布时间、创建时间、操作；响应中的 `content` 保留在行对象中但不直接渲染。
- 新增/编辑表单：标题、简介、Quill `Editor`、分类、最多 10 个标签、图片附件控件、视频控件、状态。
- 图片绑定：

```vue
<ArticleMediaUpload v-model="form.attachmentOssIds" kind="image" />
```

- 视频绑定：

```vue
<ArticleMediaUpload v-model="form.videoOssIds" kind="video" />
```

- [ ] **Step 4: 保证新增和编辑默认值为数组**

```typescript
const initFormData: ContentArticleForm = {
  title: '',
  summary: '',
  content: '',
  categoryDictCode: undefined,
  tagIds: [],
  attachmentOssIds: [],
  videoOssIds: [],
  status: '0'
};
```

详情接口缺少任一数组时用空数组归一化，提交时发送 JSON 数组，不转换为逗号字符串。

- [ ] **Step 5: 加载全部标签但只限制选择数量**

`getTagOptions()` 的响应全部保留，不截断选项；`el-select` change 时如果 `form.tagIds.length > 10`，恢复到前 10 项并提示“文章标签最多选择10个”。后端校验仍是最终边界。

- [ ] **Step 6: 运行类型检查、ESLint 和生产构建**

```powershell
npx vue-tsc --noEmit
npm run lint:eslint -- src/api/content/article src/components/ArticleMediaUpload src/views/content/article/index.vue
npm run build:prod
```

Expected: 三个命令成功；构建产物包含 `content/article/index` 动态页面。

- [ ] **Step 7: 提交文章管理页面**

```powershell
git add -- src/api/content/article/types.ts src/api/content/article/index.ts src/views/content/article/index.vue
git commit -m "feat(content): add article management page"
```

### Task 10: 执行数据库迁移和端到端验收

**Files:**
- Verify: `script/sql/postgres/content_article_attachment_migration.sql`
- Verify: 后端和前端前述全部文件

**Interfaces:**
- Consumes: Tasks 1-9 的完整实现。
- Produces: 可从 Swagger 和文章页面验证的完整图片/视频生命周期。

- [ ] **Step 1: 运行全部相关后端测试**

```powershell
mvn -pl ruoyi-common/ruoyi-common-oss,ruoyi-modules/ruoyi-system,ruoyi-modules/ruoyi-content,ruoyi-admin -am -DskipTests=false -Dgroups=dev -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: BUILD SUCCESS，所有新增测试 PASS。

- [ ] **Step 2: 在事务中验证 PostgreSQL 迁移**

```powershell
psql -h localhost -U postgres -d ry_vue -v ON_ERROR_STOP=1 -c "begin" -f script/sql/postgres/content_article_attachment_migration.sql -c "select column_name from information_schema.columns where table_name='content_article' and column_name='cover_oss_id'" -c "select indexname from pg_indexes where tablename='content_article_attachment' order by indexname" -c "rollback"
```

Expected: 脚本执行成功；`cover_oss_id` 查询为空；主键、OSS 唯一约束索引和文章类型排序索引存在。确认无冲突后去掉事务包装正式执行迁移。

- [ ] **Step 3: 启动服务并验证 Swagger 上传鉴权**

Swagger 先执行授权，再使用 multipart 调用：

```text
POST /resource/oss/upload
file=<small.png>
bizType=article_attachment
```

Expected: HTTP 200，响应包含 `ossId`、`fileName`、`url`；Authorization 和 clientid 请求头存在，不再出现 401。

- [ ] **Step 4: 验证图片和视频业务边界**

- 50 MiB 图片成功，50 MiB + 1 byte 图片返回业务错误。
- 允许格式的小视频成功；不允许扩展名失败。
- 2 GiB 边界用 mock/受控测试文件验证后端配置，不在版本库保存大文件。
- 第 11 张图片、第 6 个视频在文章保存时被后端拒绝。
- 同一个 OSS ID 绑定第二篇文章时被拒绝。

- [ ] **Step 5: 验证文章列表、回显和下载**

- 新增文章后列表响应包含 `content`、完整 `attachmentOssIds`、完整 `videoOssIds`。
- 编辑页面只调用一次 `listByIds` 回显当前有效 URL。
- MP4/WebM 可预览；浏览器不支持的 AVI/WMV/FLV 显示下载入口。
- `/resource/oss/download/{ossId}` 能下载原文件。

- [ ] **Step 6: 验证删除和清理**

- 普通删除文章后，关系和 MinIO 文件仍存在。
- 物理删除后，文章、标签关系、附件关系、`sys_oss` 和 MinIO 对象全部删除。
- 把测试临时文件时间改为 25 小时前，清理任务删除它；24 小时内文件保持。
- `docker exec minio mc ls local/ruoyi --incomplete --recursive` 检查无长期遗留分片。

- [ ] **Step 7: 验证前端并检查两个仓库状态**

```powershell
Set-Location C:\Users\dj\ruoyi-plus\simple\plus-ui
npx vitest run src/components/ArticleMediaUpload/mediaPolicy.test.ts
npx vue-tsc --noEmit
npm run build:prod
git status --short

Set-Location C:\Users\dj\ruoyi-plus\simple\RuoYi-Vue-Plus
git status --short
```

Expected: 前端验证 PASS；两个仓库仅保留用户原有、与本功能无关的工作区改动。

- [ ] **Step 8: 记录最终验证证据**

在交付消息中列出：后端 Maven 命令与结果、前端 Vitest/类型检查/构建结果、迁移验证结果、实际小文件上传的 `ossId`、逻辑删除与物理删除验证结果。不得只写“已完成”而没有命令输出依据。
