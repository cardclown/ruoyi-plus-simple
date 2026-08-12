-- 文章管理菜单。
--
-- 菜单 ID 与生产租户套餐、角色授权保持稳定；脚本可重复执行，用于新环境初始化或修正菜单配置。
insert into sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache,
     menu_type, visible, status, perms, icon, create_dept, create_by, create_time,
     update_by, update_time, remark)
values
    (2085531983779414018, '文章管理', 0, 1, 'content', 'article/index', null, 1, 1,
     'C', '0', '0', 'content:article:list', 'language', 103, 1, now(), 1, now(), ''),
    (2087080611614449666, '文章查询', 2085531983779414018, 1, '', '', null, 1, 0,
     'F', '0', '0', 'content:article:query', '', 103, 1, now(), 1, now(), ''),
    (2087080737217077250, '文章新增', 2085531983779414018, 1, '', '', null, 1, 0,
     'F', '0', '0', 'content:article:add', '', 103, 1, now(), 1, now(), ''),
    (2087080985721200641, '文章修改', 2085531983779414018, 1, '', '', null, 1, 0,
     'F', '0', '0', 'content:article:edit', '', 103, 1, now(), 1, now(), ''),
    (2087081078780223490, '文章删除', 2085531983779414018, 1, '', '', null, 1, 0,
     'F', '0', '0', 'content:article:remove', '', 103, 1, now(), 1, now(), ''),
    (2087081176180350978, '文章导出', 2085531983779414018, 1, '', '', null, 1, 0,
     'F', '0', '0', 'content:article:export', '', 103, 1, now(), 1, now(), ''),
    (2087081410809716737, '发布/撤回文章', 2085531983779414018, 1, '', '', null, 1, 0,
     'F', '0', '0', 'content:article:edit', '', 103, 1, now(), 1, now(), '')
on conflict (menu_id) do update
set menu_name = excluded.menu_name,
    parent_id = excluded.parent_id,
    order_num = excluded.order_num,
    path = excluded.path,
    component = excluded.component,
    query_param = excluded.query_param,
    is_frame = excluded.is_frame,
    is_cache = excluded.is_cache,
    menu_type = excluded.menu_type,
    visible = excluded.visible,
    status = excluded.status,
    perms = excluded.perms,
    icon = excluded.icon,
    update_by = excluded.update_by,
    update_time = excluded.update_time,
    remark = excluded.remark;
