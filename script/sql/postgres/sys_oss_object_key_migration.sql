-- 将 sys_oss.url 从历史完整 URL 归一化为对象 Key。
--
-- file_name + service 才是附件的稳定定位信息；访问 URL 由后端按当前 OSS 配置动态生成。
-- 保留 url 列是为了兼容 RuoYi 现有表结构和查询对象，不再允许业务代码依赖该列访问或删除文件。
-- 本脚本可重复执行，不修改 oss_id、file_name 或对象存储中的实际文件。
update sys_oss
   set url = file_name
 where file_name is not null
   and btrim(file_name) <> ''
   and url is distinct from file_name;

comment on column sys_oss.url is '兼容对象定位字段（新数据保存对象Key）';
