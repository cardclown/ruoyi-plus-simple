# 文章管理数据库实施计划

> **执行代理要求：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans`，逐项执行本计划。所有步骤使用复选框（`- [ ]`）跟踪状态。

**目标：** 创建并执行一份可重复运行的 PostgreSQL 迁移脚本，用于建立文章表、文章标签关联表以及默认文章字典。

**架构：** 在 `script/sql/postgres/` 下新增一份独立迁移脚本。脚本创建两张表，不添加业务二级索引和外键；然后使用 `NOT EXISTS` 防重和动态分配的初始化 ID，为默认租户 `000000` 写入两个字典类型及十一条字典数据。正式执行前，在回滚事务内连续执行脚本两次，验证语法和幂等性。

**技术栈：** PostgreSQL 17、Docker、PowerShell、RuoYi-Vue-Plus 5.X 数据库规范

## 全局约束

- `content_article` 通过 `tenant_id` 实现租户隔离，通过 `create_dept` 和 `create_by` 支持数据权限。
- `content_article_tag` 不保存 `tenant_id`，后续实现业务代码时加入 `tenant.excludes`。
- `tag_ids` 使用 `varchar(200)`，保存逗号分隔的标签 ID；允许空字符串，最多十个标签的限制仅在 Java 代码中校验。
- `status` 使用 `0` 表示草稿、`1` 表示已发布。
- `del_flag` 使用 `0` 表示正常、`1` 表示已删除。
- 富文本以 HTML 保存到 PostgreSQL `text` 字段；图片二进制仍然存入 OSS。
- 不添加业务二级索引、外键或数据库检查约束。
- 只初始化默认租户 `000000`。
- 初始化一个分类“公司新闻”和十个标签。

---

### 任务一：创建并验证 PostgreSQL 迁移脚本

**文件：**
- 新建：`script/sql/postgres/content_article.sql`
- 参考：`docs/superpowers/specs/2026-08-07-content-article-database-design.md`

**接口：**
- 输入：数据库 `ry_vue` 中现有的 `sys_tenant`、`sys_dict_type` 和 `sys_dict_data` 表。
- 输出：`content_article`、`content_article_tag` 两张表；`content_article_category`、`content_article_tag` 两个字典类型；默认租户 `000000` 下的一条分类字典数据和十条标签字典数据。

- [ ] **步骤 1：确认迁移尚未执行**

执行：

```powershell
docker exec postgres psql -U postgres -d ry_vue -c "select to_regclass('public.content_article') as article_table, to_regclass('public.content_article_tag') as tag_table; select dict_type, count(*) from sys_dict_type where tenant_id = '000000' and dict_type in ('content_article_category', 'content_article_tag') group by dict_type;"
```

首次执行前的预期结果：两个表名均为 `null`，字典查询返回零行。

- [ ] **步骤 2：使用以下完整 SQL 创建迁移脚本**

新建 `script/sql/postgres/content_article.sql`：

```sql
-- ----------------------------
-- 文章管理
-- ----------------------------

do $$
begin
    if not exists (select 1 from sys_tenant where tenant_id = '000000') then
        raise exception '默认租户 000000 不存在，无法初始化文章字典';
    end if;
end
$$;

create table if not exists content_article
(
    article_id          bigint,
    tenant_id           varchar(20)  not null default '000000'::varchar,
    title               varchar(200) not null,
    summary             varchar(500)          default ''::varchar,
    content             text         not null default ''::text,
    category_dict_code  bigint       not null,
    tag_ids             varchar(200) not null default ''::varchar,
    cover_oss_id        bigint,
    status              char(1)      not null default '0'::bpchar,
    publish_by          bigint,
    publish_time        timestamp,
    create_dept         bigint,
    create_by           bigint,
    create_time         timestamp,
    update_by           bigint,
    update_time         timestamp,
    del_flag            char(1)      not null default '0'::bpchar,
    constraint content_article_pk primary key (article_id)
);

comment on table content_article is '文章表';
comment on column content_article.article_id is '文章ID';
comment on column content_article.tenant_id is '租户编号';
comment on column content_article.title is '文章标题';
comment on column content_article.summary is '文章简介';
comment on column content_article.content is 'Quill富文本HTML正文';
comment on column content_article.category_dict_code is '分类字典ID';
comment on column content_article.tag_ids is '标签字典ID集合，逗号分隔，最多10个';
comment on column content_article.cover_oss_id is '封面OSS附件ID';
comment on column content_article.status is '发布状态（0草稿 1已发布）';
comment on column content_article.publish_by is '发布人';
comment on column content_article.publish_time is '发布时间';
comment on column content_article.create_dept is '创建部门';
comment on column content_article.create_by is '创建者';
comment on column content_article.create_time is '创建时间';
comment on column content_article.update_by is '更新者';
comment on column content_article.update_time is '更新时间';
comment on column content_article.del_flag is '删除标志（0正常 1删除）';

create table if not exists content_article_tag
(
    article_tag_id  bigint,
    article_id      bigint not null,
    tag_dict_code   bigint not null,
    create_by       bigint,
    create_time     timestamp,
    constraint content_article_tag_pk primary key (article_tag_id)
);

comment on table content_article_tag is '文章标签关联表';
comment on column content_article_tag.article_tag_id is '文章标签关联ID';
comment on column content_article_tag.article_id is '文章ID';
comment on column content_article_tag.tag_dict_code is '标签字典ID';
comment on column content_article_tag.create_by is '创建者';
comment on column content_article_tag.create_time is '创建时间';

with seed(dict_name, dict_type, remark) as
(
    values
        ('文章分类', 'content_article_category', '文章分类列表'),
        ('文章标签', 'content_article_tag', '文章标签列表')
),
missing as
(
    select seed.*
    from seed
    where not exists
    (
        select 1
        from sys_dict_type current_type
        where current_type.tenant_id = '000000'
          and current_type.dict_type = seed.dict_type
    )
),
numbered as
(
    select missing.*, row_number() over (order by missing.dict_type) as row_no
    from missing
),
base as
(
    select coalesce(max(dict_id), 0) as max_id
    from sys_dict_type
)
insert into sys_dict_type
    (dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time, update_by, update_time, remark)
select
    base.max_id + numbered.row_no,
    '000000',
    numbered.dict_name,
    numbered.dict_type,
    103,
    1,
    now(),
    null,
    null,
    numbered.remark
from numbered
cross join base;

with seed(dict_sort, dict_label, dict_value, dict_type, is_default, remark) as
(
    values
        (1,  '公司新闻',   'company_news',           'content_article_category', 'Y', '公司新闻'),
        (1,  '数字化转型', 'digital_transformation', 'content_article_tag',      'N', '数字化转型'),
        (2,  '企业战略',   'enterprise_strategy',    'content_article_tag',      'N', '企业战略'),
        (3,  '云计算',     'cloud_computing',        'content_article_tag',      'N', '云计算'),
        (4,  '技术选型',   'technology_selection',   'content_article_tag',      'N', '技术选型'),
        (5,  '版本更新',   'version_update',         'content_article_tag',      'N', '版本更新'),
        (6,  'Alba',       'alba',                   'content_article_tag',      'N', 'Alba'),
        (7,  '开源',       'open_source',            'content_article_tag',      'N', '开源'),
        (8,  '项目管理',   'project_management',     'content_article_tag',      'N', '项目管理'),
        (9,  '微服务',     'microservices',          'content_article_tag',      'N', '微服务'),
        (10, '架构设计',   'architecture_design',    'content_article_tag',      'N', '架构设计')
),
missing as
(
    select seed.*
    from seed
    where not exists
    (
        select 1
        from sys_dict_data current_data
        where current_data.tenant_id = '000000'
          and current_data.dict_type = seed.dict_type
          and current_data.dict_value = seed.dict_value
    )
),
numbered as
(
    select missing.*, row_number() over (order by missing.dict_type, missing.dict_sort) as row_no
    from missing
),
base as
(
    select coalesce(max(dict_code), 0) as max_code
    from sys_dict_data
)
insert into sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class,
     is_default, create_dept, create_by, create_time, update_by, update_time, remark)
select
    base.max_code + numbered.row_no,
    '000000',
    numbered.dict_sort,
    numbered.dict_label,
    numbered.dict_value,
    numbered.dict_type,
    '',
    '',
    numbered.is_default,
    103,
    1,
    now(),
    null,
    null,
    numbered.remark
from numbered
cross join base;
```

- [ ] **步骤 3：执行迁移脚本静态检查**

执行：

```powershell
rg -n "create (unique )?index|foreign key|check\s*\(" script/sql/postgres/content_article.sql
git diff --check -- script/sql/postgres/content_article.sql
```

预期结果：`rg` 不返回匹配内容，`git diff --check` 不返回任何内容。

- [ ] **步骤 4：在同一个回滚事务内连续执行迁移脚本两次**

执行：

```powershell
$sql = Get-Content -Raw 'script/sql/postgres/content_article.sql'
$validation = "begin;`n$sql`n$sql`nselect to_regclass('public.content_article'), to_regclass('public.content_article_tag');`nselect count(*) from sys_dict_type where tenant_id = '000000' and dict_type in ('content_article_category', 'content_article_tag');`nselect count(*) from sys_dict_data where tenant_id = '000000' and dict_type in ('content_article_category', 'content_article_tag');`nrollback;"
$validation | docker exec -i postgres psql -v ON_ERROR_STOP=1 -U postgres -d ry_vue
```

预期结果：事务内两个表名均能正常解析，字典类型数量为 `2`，字典数据数量为 `11`，最后输出 `ROLLBACK`。

- [ ] **步骤 5：确认回滚后数据库恢复原状**

执行：

```powershell
docker exec postgres psql -U postgres -d ry_vue -c "select to_regclass('public.content_article') as article_table, to_regclass('public.content_article_tag') as tag_table; select count(*) as dict_type_count from sys_dict_type where tenant_id = '000000' and dict_type in ('content_article_category', 'content_article_tag'); select count(*) as dict_data_count from sys_dict_data where tenant_id = '000000' and dict_type in ('content_article_category', 'content_article_tag');"
```

预期结果：两个表名均为 `null`，两个数量均为 `0`。

- [ ] **步骤 6：提交已验证的迁移脚本**

```powershell
git add script/sql/postgres/content_article.sql
git commit -m "feat: add content article database migration"
```

### 任务二：在本地数据库执行并验证迁移

**文件：**
- 读取：`script/sql/postgres/content_article.sql`

**接口：**
- 输入：任务一验证通过的迁移脚本，以及 Docker 容器 `postgres` 中的数据库 `ry_vue`。
- 输出：本地 PostgreSQL 数据库中的文章表结构和默认字典数据。

- [ ] **步骤 1：开启遇错即停并执行迁移脚本**

执行：

```powershell
Get-Content -Raw 'script/sql/postgres/content_article.sql' | docker exec -i postgres psql -v ON_ERROR_STOP=1 -U postgres -d ry_vue
```

预期结果：两条 `CREATE TABLE`、字段注释和字典插入均执行成功，命令退出码为 `0`。

- [ ] **步骤 2：验证表字段、默认值和主键**

执行：

```powershell
docker exec postgres psql -U postgres -d ry_vue -c "select table_name, column_name, data_type, character_maximum_length, is_nullable, column_default from information_schema.columns where table_schema = 'public' and table_name in ('content_article', 'content_article_tag') order by table_name, ordinal_position; select conrelid::regclass as table_name, conname, pg_get_constraintdef(oid) as definition from pg_constraint where conrelid in ('content_article'::regclass, 'content_article_tag'::regclass) order by conrelid::regclass::text, conname;"
```

预期结果：`content_article.tag_ids` 为 `varchar(200)`，`content` 为 `text`，`del_flag` 默认值为 `0`，两个表只存在各自的主键约束。

- [ ] **步骤 3：验证字典类型和字典数据**

执行：

```powershell
docker exec postgres psql -U postgres -d ry_vue -c "select tenant_id, dict_name, dict_type from sys_dict_type where tenant_id = '000000' and dict_type in ('content_article_category', 'content_article_tag') order by dict_type; select dict_type, dict_sort, dict_label, dict_value, is_default from sys_dict_data where tenant_id = '000000' and dict_type in ('content_article_category', 'content_article_tag') order by dict_type, dict_sort;"
```

预期结果：存在一条“公司新闻”分类数据和十条标签数据。

- [ ] **步骤 4：再次执行迁移脚本，验证正式环境幂等性**

执行：

```powershell
Get-Content -Raw 'script/sql/postgres/content_article.sql' | docker exec -i postgres psql -v ON_ERROR_STOP=1 -U postgres -d ry_vue
docker exec postgres psql -U postgres -d ry_vue -c "select count(*) as dict_type_count from sys_dict_type where tenant_id = '000000' and dict_type in ('content_article_category', 'content_article_tag'); select count(*) as dict_data_count from sys_dict_data where tenant_id = '000000' and dict_type in ('content_article_category', 'content_article_tag');"
```

预期结果：命令退出码为 `0`，字典类型数量仍为 `2`，字典数据数量仍为 `11`。
