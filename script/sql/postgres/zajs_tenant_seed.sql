-- 中安建设（zajs）租户初始化数据。
--
-- 只保存可重建的租户、套餐、账号、权限、参数和字典配置；不包含业务文章、文件记录、登录信息和日志。
-- 本脚本依赖基础 RuoYi 数据、文章字典及文章菜单已先完成初始化，并支持重复执行。

do $$
begin
    if not exists (select 1 from sys_tenant where tenant_id = '000000') then
        raise exception '默认租户 000000 不存在，无法初始化中安建设租户';
    end if;

    if not exists (select 1 from sys_menu where menu_id = 2085531983779414018) then
        raise exception '文章管理菜单不存在，请先执行 content_article_menu.sql';
    end if;
end
$$;

insert into sys_tenant_package
    (package_id, package_name, menu_ids, remark, menu_check_strictly, status, del_flag,
     create_dept, create_by, create_time, update_by, update_time)
values
    (2085611272126214146, '中安建设',
     '2085531983779414018,2087080737217077250,2087080985721200641,2087081078780223490,2087081176180350978,2087081410809716737,2087080611614449666,1,131,100,1001,1002,1003,1004,1005,1006,1007,130,101,1008,1009,1010,1011,1012,102,1013,1014,1015,1016,103,1017,1018,1019,1020,104,1021,1022,1023,1024,1025,105,1026,1027,1028,1029,1030,132,106,1031,1032,1033,1034,1035,107,1036,1037,1038,1039,108,500,1040,1041,1042,501,1043,1044,1045,1050,133,118,1600,1601,1602,1603,1620,1621,1623,1622,123,1061,1062,1063,1064,1065,2,109,1046,1047,1048,113,3,116,115,1055,1056,1058,1057,1059,1060',
     '', true, '0', '0', 103, 1, now(), 1, now())
on conflict (package_id) do update
set package_name = excluded.package_name,
    menu_ids = excluded.menu_ids,
    remark = excluded.remark,
    menu_check_strictly = excluded.menu_check_strictly,
    status = excluded.status,
    del_flag = excluded.del_flag,
    update_by = excluded.update_by,
    update_time = excluded.update_time;

insert into sys_tenant
    (id, tenant_id, contact_user_name, contact_phone, company_name, license_number, address, intro,
     domain, remark, package_id, expire_time, account_count, status, del_flag,
     create_dept, create_by, create_time, update_by, update_time)
values
    (2085615241514852354, '140872', '董俊', '15189139997', '中安建设', '', '', '', '', '',
     2085611272126214146, timestamp '2099-09-09 14:10:43', 300, '0', '0',
     103, 1, now(), 1, now())
on conflict (id) do update
set tenant_id = excluded.tenant_id,
    contact_user_name = excluded.contact_user_name,
    contact_phone = excluded.contact_phone,
    company_name = excluded.company_name,
    license_number = excluded.license_number,
    address = excluded.address,
    intro = excluded.intro,
    domain = excluded.domain,
    remark = excluded.remark,
    package_id = excluded.package_id,
    expire_time = excluded.expire_time,
    account_count = excluded.account_count,
    status = excluded.status,
    del_flag = excluded.del_flag,
    update_by = excluded.update_by,
    update_time = excluded.update_time;

insert into sys_dept
    (dept_id, tenant_id, parent_id, ancestors, dept_name, dept_category, order_num, leader,
     phone, email, status, del_flag, create_dept, create_by, create_time, update_by, update_time)
values
    (2085615241825230850, '140872', 0, '0', '中安建设', '', 0, 2085615242081083393,
     '', '', '0', '0', 103, 1, now(), 1, now()),
    (2086631018238005250, '140872', 2085615241825230850, '0,2085615241825230850', '研发部', '', 0, null,
     '', '', '0', '0', 2085615241825230850, 2085615242081083393, now(), 2085615242081083393, now())
on conflict (dept_id) do update
set tenant_id = excluded.tenant_id,
    parent_id = excluded.parent_id,
    ancestors = excluded.ancestors,
    dept_name = excluded.dept_name,
    dept_category = excluded.dept_category,
    order_num = excluded.order_num,
    leader = excluded.leader,
    phone = excluded.phone,
    email = excluded.email,
    status = excluded.status,
    del_flag = excluded.del_flag,
    update_by = excluded.update_by,
    update_time = excluded.update_time;

insert into sys_role
    (role_id, tenant_id, role_name, role_key, role_sort, data_scope, menu_check_strictly,
     dept_check_strictly, status, del_flag, create_dept, create_by, create_time,
     update_by, update_time, remark)
values
    (2085615241577766914, '140872', '管理员', 'admin', 1, '1', true, true, '0', '0',
     103, 1, now(), 2085615242081083393, now(), '')
on conflict (role_id) do update
set tenant_id = excluded.tenant_id,
    role_name = excluded.role_name,
    role_key = excluded.role_key,
    role_sort = excluded.role_sort,
    data_scope = excluded.data_scope,
    menu_check_strictly = excluded.menu_check_strictly,
    dept_check_strictly = excluded.dept_check_strictly,
    status = excluded.status,
    del_flag = excluded.del_flag,
    update_by = excluded.update_by,
    update_time = excluded.update_time,
    remark = excluded.remark;

insert into sys_user
    (user_id, tenant_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex,
     avatar, password, status, del_flag, login_ip, login_date, create_dept, create_by,
     create_time, update_by, update_time, remark)
values
    (2085615242081083393, '140872', 2085615241825230850, 'zajs', 'zajs', 'sys_user', '', '', '0',
     null, '$2a$10$.BlQ/vj01BphebcizUyM8.zoUazFxFPIZPLiw/mXLYKmhCKF61.Ji', '0', '0', '', null,
     103, 1, now(), 1, now(), '')
on conflict (user_id) do update
set tenant_id = excluded.tenant_id,
    dept_id = excluded.dept_id,
    user_name = excluded.user_name,
    nick_name = excluded.nick_name,
    user_type = excluded.user_type,
    email = excluded.email,
    phonenumber = excluded.phonenumber,
    sex = excluded.sex,
    avatar = excluded.avatar,
    password = excluded.password,
    status = excluded.status,
    del_flag = excluded.del_flag,
    update_by = excluded.update_by,
    update_time = excluded.update_time,
    remark = excluded.remark;

insert into sys_user_role (user_id, role_id)
values (2085615242081083393, 2085615241577766914)
on conflict (user_id, role_id) do nothing;

-- 套餐菜单和管理员角色必须来自同一份 menu_ids，避免后续只更新套餐却遗漏角色授权。
insert into sys_role_menu (role_id, menu_id)
select 2085615241577766914, menu.menu_id
from sys_tenant_package package
cross join lateral unnest(string_to_array(package.menu_ids, ',')::bigint[]) selected(menu_id)
join sys_menu menu on menu.menu_id = selected.menu_id
where package.package_id = 2085611272126214146
on conflict (role_id, menu_id) do nothing;

with config_map(config_id, config_key) as
(
    values
        (2085615242211106824::bigint, 'sys.index.skinName'),
        (2085615242211106825::bigint, 'sys.user.initPassword'),
        (2085615242211106826::bigint, 'sys.index.sideTheme'),
        (2085615242211106827::bigint, 'sys.account.registerUser'),
        (2085615242211106828::bigint, 'sys.oss.previewListResource')
)
insert into sys_config
    (config_id, tenant_id, config_name, config_key, config_value, config_type,
     create_dept, create_by, create_time, update_by, update_time, remark)
select config_map.config_id, '140872', source.config_name, source.config_key, source.config_value,
       source.config_type, 103, 1, now(), 1, now(), source.remark
from config_map
join sys_config source
  on source.tenant_id = '000000'
 and source.config_key = config_map.config_key
on conflict (config_id) do update
set config_name = excluded.config_name,
    config_key = excluded.config_key,
    config_value = excluded.config_value,
    config_type = excluded.config_type,
    update_by = excluded.update_by,
    update_time = excluded.update_time,
    remark = excluded.remark;

with type_map(dict_id, dict_type) as
(
    values
        (2085615242081083394::bigint, 'content_article_category'),
        (2085615242143997954::bigint, 'content_article_tag'),
        (2085615242143997955::bigint, 'sys_common_status'),
        (2085615242143997956::bigint, 'sys_device_type'),
        (2085615242143997957::bigint, 'sys_grant_type'),
        (2085615242143997958::bigint, 'sys_normal_disable'),
        (2085615242143997959::bigint, 'sys_notice_status'),
        (2085615242143997960::bigint, 'sys_notice_type'),
        (2085615242143997961::bigint, 'sys_oper_type'),
        (2085615242143997962::bigint, 'sys_show_hide'),
        (2085615242143997963::bigint, 'sys_user_sex'),
        (2085615242143997964::bigint, 'sys_yes_no')
)
insert into sys_dict_type
    (dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time,
     update_by, update_time, remark)
select type_map.dict_id, '140872', source.dict_name, source.dict_type, 103, 1, now(), 1, now(), source.remark
from type_map
join sys_dict_type source
  on source.tenant_id = '000000'
 and source.dict_type = type_map.dict_type
on conflict (tenant_id, dict_type) do update
set dict_name = excluded.dict_name,
    update_by = excluded.update_by,
    update_time = excluded.update_time,
    remark = excluded.remark;

with data_map(dict_code, dict_type, dict_value) as
(
    values
        (2085615242143997965::bigint, 'sys_user_sex', '0'),
        (2085615242143997966::bigint, 'sys_user_sex', '1'),
        (2085615242143997967::bigint, 'sys_user_sex', '2'),
        (2085615242143997968::bigint, 'sys_show_hide', '0'),
        (2085615242143997969::bigint, 'sys_show_hide', '1'),
        (2085615242143997970::bigint, 'sys_normal_disable', '0'),
        (2085615242143997971::bigint, 'sys_normal_disable', '1'),
        (2085615242143997972::bigint, 'sys_yes_no', 'Y'),
        (2085615242143997973::bigint, 'sys_yes_no', 'N'),
        (2085615242143997974::bigint, 'sys_notice_type', '1'),
        (2085615242143997975::bigint, 'sys_notice_type', '2'),
        (2085615242143997976::bigint, 'sys_notice_status', '0'),
        (2085615242143997977::bigint, 'sys_notice_status', '1'),
        (2085615242143997978::bigint, 'sys_oper_type', '0'),
        (2085615242143997979::bigint, 'sys_oper_type', '1'),
        (2085615242143997980::bigint, 'sys_oper_type', '2'),
        (2085615242143997981::bigint, 'sys_oper_type', '3'),
        (2085615242143997982::bigint, 'sys_oper_type', '4'),
        (2085615242143997983::bigint, 'sys_oper_type', '5'),
        (2085615242143997984::bigint, 'sys_oper_type', '6'),
        (2085615242143997985::bigint, 'sys_oper_type', '7'),
        (2085615242143997986::bigint, 'sys_oper_type', '8'),
        (2085615242143997987::bigint, 'sys_oper_type', '9'),
        (2085615242143997988::bigint, 'sys_common_status', '0'),
        (2085615242143997989::bigint, 'sys_common_status', '1'),
        (2085615242143997990::bigint, 'sys_grant_type', 'password'),
        (2085615242143997991::bigint, 'sys_device_type', 'pc'),
        (2085615242143997992::bigint, 'content_article_category', 'company_news'),
        (2085615242143997993::bigint, 'content_article_tag', 'digital_transformation'),
        (2085615242143997994::bigint, 'content_article_tag', 'enterprise_strategy'),
        (2085615242143997995::bigint, 'content_article_tag', 'cloud_computing'),
        (2085615242211106817::bigint, 'content_article_tag', 'technology_selection'),
        (2085615242211106818::bigint, 'content_article_tag', 'version_update'),
        (2085615242211106819::bigint, 'content_article_tag', 'alba'),
        (2085615242211106820::bigint, 'content_article_tag', 'open_source'),
        (2085615242211106821::bigint, 'content_article_tag', 'project_management'),
        (2085615242211106822::bigint, 'content_article_tag', 'microservices'),
        (2085615242211106823::bigint, 'content_article_tag', 'architecture_design')
)
insert into sys_dict_data
    (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class,
     is_default, create_dept, create_by, create_time, update_by, update_time, remark)
select data_map.dict_code, '140872', source.dict_sort, source.dict_label, source.dict_value,
       source.dict_type, source.css_class, source.list_class, source.is_default,
       103, 1, now(), 1, now(), source.remark
from data_map
join sys_dict_data source
  on source.tenant_id = '000000'
 and source.dict_type = data_map.dict_type
 and source.dict_value = data_map.dict_value
on conflict (dict_code) do update
set dict_sort = excluded.dict_sort,
    dict_label = excluded.dict_label,
    dict_value = excluded.dict_value,
    dict_type = excluded.dict_type,
    css_class = excluded.css_class,
    list_class = excluded.list_class,
    is_default = excluded.is_default,
    update_by = excluded.update_by,
    update_time = excluded.update_time,
    remark = excluded.remark;

do $$
declare
    package_menu_count integer;
    assigned_menu_count integer;
begin
    select cardinality(string_to_array(menu_ids, ','))
      into package_menu_count
      from sys_tenant_package
     where package_id = 2085611272126214146;

    select count(*)
      into assigned_menu_count
      from sys_role_menu
     where role_id = 2085615241577766914;

    if package_menu_count <> assigned_menu_count then
        raise exception '中安建设套餐菜单与管理员角色授权数量不一致: 套餐 %, 角色 %',
            package_menu_count, assigned_menu_count;
    end if;

    if (select count(*) from sys_config where tenant_id = '140872') <> 5
       or (select count(*) from sys_dict_type where tenant_id = '140872') <> 12
       or (select count(*) from sys_dict_data where tenant_id = '140872') <> 38 then
        raise exception '中安建设租户参数或字典初始化数量不完整';
    end if;
end
$$;
