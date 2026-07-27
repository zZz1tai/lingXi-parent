-- 系统安全配置迁移脚本
-- 将 application.yml 中的敏感配置项迁移到 sys_config 表，通过管理界面维护。
-- 执行后请重新登录后台使缓存刷新。

-- 阿里云 OSS 配置
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '阿里云OSS-AccessKey', 'security.oss.accessKey', '', 'Y', 'admin', NOW(), '系统安全配置页面维护'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'security.oss.accessKey');

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '阿里云OSS-SecretKey', 'security.oss.secretKey', '', 'Y', 'admin', NOW(), '系统安全配置页面维护'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'security.oss.secretKey');

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '阿里云OSS-Endpoint', 'security.oss.endpoint', 'oss-cn-beijing.aliyuncs.com', 'N', 'admin', NOW(), '系统安全配置页面维护'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'security.oss.endpoint');

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '阿里云OSS-BucketName', 'security.oss.bucketName', '', 'N', 'admin', NOW(), '系统安全配置页面维护'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'security.oss.bucketName');

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '阿里云OSS-访问域名', 'security.oss.domain', '', 'N', 'admin', NOW(), '系统安全配置页面维护'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'security.oss.domain');

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '阿里云OSS-基础路径', 'security.oss.basePath', 'dkd-images/', 'N', 'admin', NOW(), '系统安全配置页面维护'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'security.oss.basePath');

-- Agent 服务 API Key
INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT 'Agent服务-API Key', 'security.agent.serviceApiKey', '', 'Y', 'admin', NOW(), '系统安全配置页面维护'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'security.agent.serviceApiKey');
