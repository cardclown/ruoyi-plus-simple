-- 文章管理菜单 SQL
insert into sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values
    (2085567048592797698, '文章', 3, 1, 'article', 'content/article/index', 1, 0, 'C', '0', '0', 'content:article:list', '#', 103, 1, now(), null, null, '文章菜单');

-- 按钮 SQL
insert into sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, update_by, update_time, remark)
values
    (2085567048592797699, '文章查询', 2085567048592797698, 1, '#', '', 1, 0, 'F', '0', '0', 'content:article:query', '#', 103, 1, now(), null, null, ''),
    (2085567048592797700, '文章新增', 2085567048592797698, 2, '#', '', 1, 0, 'F', '0', '0', 'content:article:add', '#', 103, 1, now(), null, null, ''),
    (2085567048592797701, '文章修改', 2085567048592797698, 3, '#', '', 1, 0, 'F', '0', '0', 'content:article:edit', '#', 103, 1, now(), null, null, ''),
    (2085567048592797702, '文章删除', 2085567048592797698, 4, '#', '', 1, 0, 'F', '0', '0', 'content:article:remove', '#', 103, 1, now(), null, null, ''),
    (2085567048592797703, '文章导出', 2085567048592797698, 5, '#', '', 1, 0, 'F', '0', '0', 'content:article:export', '#', 103, 1, now(), null, null, '');
