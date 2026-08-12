-- 生产服务器环境配置。
--
-- endpoint 只供 Java 在服务器内部访问 MinIO；domain 只用于生成浏览器可访问的文件 URL。
-- 两者必须分离，否则后端会向前端返回 localhost/127.0.0.1 地址，或让上传流量绕行公网 Nginx。
update sys_oss_config
set endpoint = '127.0.0.1:19000',
    domain = 'https://www.cicggroup.com/oss',
    is_https = 'N',
    update_by = 1,
    update_time = now()
where tenant_id = '000000'
  and config_key in ('minio', 'image');

do $$
begin
    if
    (
        select count(*)
        from sys_oss_config
        where tenant_id = '000000'
          and config_key in ('minio', 'image')
          and endpoint = '127.0.0.1:19000'
          and domain = 'https://www.cicggroup.com/oss'
    ) <> 2 then
        raise exception '生产 MinIO 配置不完整，必须同时更新 minio 和 image 两项配置';
    end if;
end
$$;
