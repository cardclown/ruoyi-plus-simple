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

comment on table content_article_attachment is '文章附件关联表';
comment on column content_article_attachment.article_attachment_id is '文章附件关联ID';
comment on column content_article_attachment.article_id is '文章ID';
comment on column content_article_attachment.oss_id is 'OSS附件ID';
comment on column content_article_attachment.attachment_type is '附件类型（0图片 1视频）';
comment on column content_article_attachment.sort_num is '同类附件排序号';
comment on column content_article_attachment.create_by is '创建者';
comment on column content_article_attachment.create_time is '创建时间';

create index if not exists content_article_attachment_article_type_sort_idx
    on content_article_attachment (article_id, attachment_type, sort_num);

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
