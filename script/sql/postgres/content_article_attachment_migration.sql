-- 为已有文章安装附件关联表，并迁移历史封面。
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

do $$
declare
    duplicated_oss_id bigint;
begin
    if not exists
    (
        select 1
          from information_schema.columns
         where table_schema = current_schema()
           and table_name = 'content_article'
           and column_name = 'cover_oss_id'
    ) then
        return;
    end if;

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

    alter table content_article drop column if exists cover_oss_id;
end
$$;
