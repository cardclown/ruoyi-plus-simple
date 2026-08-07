# Content Article Database Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create and apply an idempotent PostgreSQL migration for article storage, article-tag relations, and the default article dictionaries.

**Architecture:** Add one standalone migration under `script/sql/postgres/`. The migration creates two tables without business secondary indexes or foreign keys, then seeds two dictionary types and eleven dictionary entries for tenant `000000` using `NOT EXISTS` guards and dynamically allocated seed IDs. Validate the script twice inside a rolled-back transaction before applying it to the local `ry_vue` database.

**Tech Stack:** PostgreSQL 17, Docker, PowerShell, RuoYi-Vue-Plus 5.X schema conventions

## Global Constraints

- `content_article` is tenant-isolated through `tenant_id` and supports data permission through `create_dept` and `create_by`.
- `content_article_tag` has no `tenant_id` and will later be added to `tenant.excludes` during application implementation.
- `tag_ids` is `varchar(200)`, stores comma-separated IDs, allows the empty string, and is limited to ten tags in Java code only.
- `status` uses `0` for draft and `1` for published.
- `del_flag` uses `0` for normal and `1` for deleted.
- Rich text is stored as HTML in PostgreSQL `text`; image binaries remain in OSS.
- Do not add business secondary indexes, foreign keys, or database check constraints.
- Seed only the default tenant `000000`.
- Seed one category (`公司新闻`) and ten tags.

---

### Task 1: Create and validate the PostgreSQL migration

**Files:**
- Create: `script/sql/postgres/content_article.sql`
- Reference: `docs/superpowers/specs/2026-08-07-content-article-database-design.md`

**Interfaces:**
- Consumes: Existing `sys_tenant`, `sys_dict_type`, and `sys_dict_data` tables in database `ry_vue`.
- Produces: Tables `content_article` and `content_article_tag`; dictionary types `content_article_category` and `content_article_tag`; one category and ten tag dictionary rows for tenant `000000`.

- [ ] **Step 1: Verify the migration has not already been applied**

Run:

```powershell
docker exec postgres psql -U postgres -d ry_vue -c "select to_regclass('public.content_article') as article_table, to_regclass('public.content_article_tag') as tag_table; select dict_type, count(*) from sys_dict_type where tenant_id = '000000' and dict_type in ('content_article_category', 'content_article_tag') group by dict_type;"
```

Expected before first execution: both table columns are null and the dictionary query returns zero rows.

- [ ] **Step 2: Create the migration with the exact SQL below**

Create `script/sql/postgres/content_article.sql`:

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

- [ ] **Step 3: Run static migration checks**

Run:

```powershell
rg -n "create (unique )?index|foreign key|check\s*\(" script/sql/postgres/content_article.sql
git diff --check -- script/sql/postgres/content_article.sql
```

Expected: `rg` returns no matches and `git diff --check` returns no output.

- [ ] **Step 4: Execute the migration twice inside one rolled-back transaction**

Run:

```powershell
$sql = Get-Content -Raw 'script/sql/postgres/content_article.sql'
$validation = "begin;`n$sql`n$sql`nselect to_regclass('public.content_article'), to_regclass('public.content_article_tag');`nselect count(*) from sys_dict_type where tenant_id = '000000' and dict_type in ('content_article_category', 'content_article_tag');`nselect count(*) from sys_dict_data where tenant_id = '000000' and dict_type in ('content_article_category', 'content_article_tag');`nrollback;"
$validation | docker exec -i postgres psql -v ON_ERROR_STOP=1 -U postgres -d ry_vue
```

Expected: both tables resolve by name inside the transaction, dictionary type count is `2`, dictionary data count is `11`, and the transaction ends with `ROLLBACK`.

- [ ] **Step 5: Verify rollback restored the original database state**

Run:

```powershell
docker exec postgres psql -U postgres -d ry_vue -c "select to_regclass('public.content_article') as article_table, to_regclass('public.content_article_tag') as tag_table; select count(*) as dict_type_count from sys_dict_type where tenant_id = '000000' and dict_type in ('content_article_category', 'content_article_tag'); select count(*) as dict_data_count from sys_dict_data where tenant_id = '000000' and dict_type in ('content_article_category', 'content_article_tag');"
```

Expected: table names are null and both counts are `0`.

- [ ] **Step 6: Commit the validated migration**

```powershell
git add script/sql/postgres/content_article.sql
git commit -m "feat: add content article database migration"
```

### Task 2: Apply and verify the migration in the local database

**Files:**
- Read: `script/sql/postgres/content_article.sql`

**Interfaces:**
- Consumes: The validated migration from Task 1 and Docker container `postgres` database `ry_vue`.
- Produces: Applied article schema and default dictionary records in the local PostgreSQL database.

- [ ] **Step 1: Apply the migration with stop-on-error enabled**

Run:

```powershell
Get-Content -Raw 'script/sql/postgres/content_article.sql' | docker exec -i postgres psql -v ON_ERROR_STOP=1 -U postgres -d ry_vue
```

Expected: both `CREATE TABLE` statements, comments, and dictionary inserts complete with exit code `0`.

- [ ] **Step 2: Verify table columns, defaults, and primary keys**

Run:

```powershell
docker exec postgres psql -U postgres -d ry_vue -c "select table_name, column_name, data_type, character_maximum_length, is_nullable, column_default from information_schema.columns where table_schema = 'public' and table_name in ('content_article', 'content_article_tag') order by table_name, ordinal_position; select conrelid::regclass as table_name, conname, pg_get_constraintdef(oid) as definition from pg_constraint where conrelid in ('content_article'::regclass, 'content_article_tag'::regclass) order by conrelid::regclass::text, conname;"
```

Expected: `content_article.tag_ids` is `varchar(200)`, `content` is `text`, `del_flag` defaults to `0`, and the only constraints are the two primary keys.

- [ ] **Step 3: Verify dictionary types and entries**

Run:

```powershell
docker exec postgres psql -U postgres -d ry_vue -c "select tenant_id, dict_name, dict_type from sys_dict_type where tenant_id = '000000' and dict_type in ('content_article_category', 'content_article_tag') order by dict_type; select dict_type, dict_sort, dict_label, dict_value, is_default from sys_dict_data where tenant_id = '000000' and dict_type in ('content_article_category', 'content_article_tag') order by dict_type, dict_sort;"
```

Expected: one `公司新闻` category and ten tag rows are present.

- [ ] **Step 4: Reapply the migration to prove production idempotency**

Run:

```powershell
Get-Content -Raw 'script/sql/postgres/content_article.sql' | docker exec -i postgres psql -v ON_ERROR_STOP=1 -U postgres -d ry_vue
docker exec postgres psql -U postgres -d ry_vue -c "select count(*) as dict_type_count from sys_dict_type where tenant_id = '000000' and dict_type in ('content_article_category', 'content_article_tag'); select count(*) as dict_data_count from sys_dict_data where tenant_id = '000000' and dict_type in ('content_article_category', 'content_article_tag');"
```

Expected: exit code `0`, dictionary type count remains `2`, and dictionary data count remains `11`.

