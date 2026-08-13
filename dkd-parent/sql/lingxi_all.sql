-- LingXi/DKD complete database initialization script
-- Target: MySQL 8.0+
-- Generated from the repository SQL files plus schemas required by current mappers.
-- This script creates database `dkd` and can be imported directly on a fresh MySQL server.

CREATE DATABASE IF NOT EXISTS `dkd`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
USE `dkd`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;


-- System tables and seed data
-- ----------------------------
-- 1、部门表
-- ----------------------------
drop table if exists sys_dept;
create table sys_dept (
  dept_id           bigint(20)      not null auto_increment    comment '部门id',
  parent_id         bigint(20)      default 0                  comment '父部门id',
  ancestors         varchar(50)     default ''                 comment '祖级列表',
  dept_name         varchar(30)     default ''                 comment '部门名称',
  order_num         int(4)          default 0                  comment '显示顺序',
  leader            varchar(20)     default null               comment '负责人',
  phone             varchar(11)     default null               comment '联系电话',
  email             varchar(50)     default null               comment '邮箱',
  status            char(1)         default '0'                comment '部门状态（0正常 1停用）',
  del_flag          char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (dept_id)
) engine=innodb auto_increment=200 comment = '部门表';

-- ----------------------------
-- 初始化-部门表数据
-- ----------------------------
insert into sys_dept values(100,  0,   '0',          '若依科技',   0, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(101,  100, '0,100',      '深圳总公司', 1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(102,  100, '0,100',      '长沙分公司', 2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(103,  101, '0,100,101',  '研发部门',   1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(104,  101, '0,100,101',  '市场部门',   2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(105,  101, '0,100,101',  '测试部门',   3, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(106,  101, '0,100,101',  '财务部门',   4, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(107,  101, '0,100,101',  '运维部门',   5, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(108,  102, '0,100,102',  '市场部门',   1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(109,  102, '0,100,102',  '财务部门',   2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);


-- ----------------------------
-- 2、用户信息表
-- ----------------------------
drop table if exists sys_user;
create table sys_user (
  user_id           bigint(20)      not null auto_increment    comment '用户ID',
  dept_id           bigint(20)      default null               comment '部门ID',
  user_name         varchar(30)     not null                   comment '用户账号',
  nick_name         varchar(30)     not null                   comment '用户昵称',
  user_type         varchar(2)      default '00'               comment '用户类型（00系统用户）',
  email             varchar(50)     default ''                 comment '用户邮箱',
  phonenumber       varchar(11)     default ''                 comment '手机号码',
  sex               char(1)         default '0'                comment '用户性别（0男 1女 2未知）',
  avatar            varchar(100)    default ''                 comment '头像地址',
  password          varchar(100)    default ''                 comment '密码',
  status            char(1)         default '0'                comment '帐号状态（0正常 1停用）',
  del_flag          char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  login_ip          varchar(128)    default ''                 comment '最后登录IP',
  login_date        datetime                                   comment '最后登录时间',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (user_id)
) engine=innodb auto_increment=100 comment = '用户信息表';

-- ----------------------------
-- 初始化-用户信息表数据
-- ----------------------------
insert into sys_user values(1,  103, 'admin', '若依', '00', 'ry@163.com', '15888888888', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), 'admin', sysdate(), '', null, '管理员');
insert into sys_user values(2,  105, 'ry',    '若依', '00', 'ry@qq.com',  '15666666666', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), 'admin', sysdate(), '', null, '测试员');


-- ----------------------------
-- 3、岗位信息表
-- ----------------------------
drop table if exists sys_post;
create table sys_post
(
  post_id       bigint(20)      not null auto_increment    comment '岗位ID',
  post_code     varchar(64)     not null                   comment '岗位编码',
  post_name     varchar(50)     not null                   comment '岗位名称',
  post_sort     int(4)          not null                   comment '显示顺序',
  status        char(1)         not null                   comment '状态（0正常 1停用）',
  create_by     varchar(64)     default ''                 comment '创建者',
  create_time   datetime                                   comment '创建时间',
  update_by     varchar(64)     default ''			       comment '更新者',
  update_time   datetime                                   comment '更新时间',
  remark        varchar(500)    default null               comment '备注',
  primary key (post_id)
) engine=innodb comment = '岗位信息表';

-- ----------------------------
-- 初始化-岗位信息表数据
-- ----------------------------
insert into sys_post values(1, 'ceo',  '董事长',    1, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(2, 'se',   '项目经理',  2, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(3, 'hr',   '人力资源',  3, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(4, 'user', '普通员工',  4, '0', 'admin', sysdate(), '', null, '');


-- ----------------------------
-- 4、角色信息表
-- ----------------------------
drop table if exists sys_role;
create table sys_role (
  role_id              bigint(20)      not null auto_increment    comment '角色ID',
  role_name            varchar(30)     not null                   comment '角色名称',
  role_key             varchar(100)    not null                   comment '角色权限字符串',
  role_sort            int(4)          not null                   comment '显示顺序',
  data_scope           char(1)         default '1'                comment '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）',
  menu_check_strictly  tinyint(1)      default 1                  comment '菜单树选择项是否关联显示',
  dept_check_strictly  tinyint(1)      default 1                  comment '部门树选择项是否关联显示',
  status               char(1)         not null                   comment '角色状态（0正常 1停用）',
  del_flag             char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by            varchar(64)     default ''                 comment '创建者',
  create_time          datetime                                   comment '创建时间',
  update_by            varchar(64)     default ''                 comment '更新者',
  update_time          datetime                                   comment '更新时间',
  remark               varchar(500)    default null               comment '备注',
  primary key (role_id)
) engine=innodb auto_increment=100 comment = '角色信息表';

-- ----------------------------
-- 初始化-角色信息表数据
-- ----------------------------
insert into sys_role values('1', '超级管理员',  'admin',  1, 1, 1, 1, '0', '0', 'admin', sysdate(), '', null, '超级管理员');
insert into sys_role values('2', '普通角色',    'common', 2, 2, 1, 1, '0', '0', 'admin', sysdate(), '', null, '普通角色');


-- ----------------------------
-- 5、菜单权限表
-- ----------------------------
drop table if exists sys_menu;
create table sys_menu (
  menu_id           bigint(20)      not null auto_increment    comment '菜单ID',
  menu_name         varchar(50)     not null                   comment '菜单名称',
  parent_id         bigint(20)      default 0                  comment '父菜单ID',
  order_num         int(4)          default 0                  comment '显示顺序',
  path              varchar(200)    default ''                 comment '路由地址',
  component         varchar(255)    default null               comment '组件路径',
  query             varchar(255)    default null               comment '路由参数',
  is_frame          int(1)          default 1                  comment '是否为外链（0是 1否）',
  is_cache          int(1)          default 0                  comment '是否缓存（0缓存 1不缓存）',
  menu_type         char(1)         default ''                 comment '菜单类型（M目录 C菜单 F按钮）',
  visible           char(1)         default 0                  comment '菜单状态（0显示 1隐藏）',
  status            char(1)         default 0                  comment '菜单状态（0正常 1停用）',
  perms             varchar(100)    default null               comment '权限标识',
  icon              varchar(100)    default '#'                comment '菜单图标',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default ''                 comment '备注',
  primary key (menu_id)
) engine=innodb auto_increment=2000 comment = '菜单权限表';

-- ----------------------------
-- 初始化-菜单信息表数据
-- ----------------------------
-- 一级菜单
insert into sys_menu values('1', '系统管理', '0', '11', 'system',           null, '', 1, 0, 'M', '0', '0', '', 'system',   'admin', sysdate(), '', null, '系统管理目录');
insert into sys_menu values('2', '系统监控', '0', '12', 'monitor',          null, '', 1, 0, 'M', '0', '0', '', 'monitor',  'admin', sysdate(), '', null, '系统监控目录');
insert into sys_menu values('3', '系统工具', '0', '13', 'tool',             null, '', 1, 0, 'M', '0', '0', '', 'tool',     'admin', sysdate(), '', null, '系统工具目录');
insert into sys_menu values('4', '若依官网', '0', '14', 'http://ruoyi.vip', null, '', 0, 0, 'M', '0', '0', '', 'guide',    'admin', sysdate(), '', null, '若依官网地址');
-- 二级菜单
insert into sys_menu values('100',  '用户管理', '1',   '1', 'user',       'system/user/index',        '', 1, 0, 'C', '0', '0', 'system:user:list',        'user',          'admin', sysdate(), '', null, '用户管理菜单');
insert into sys_menu values('101',  '角色管理', '1',   '2', 'role',       'system/role/index',        '', 1, 0, 'C', '0', '0', 'system:role:list',        'peoples',       'admin', sysdate(), '', null, '角色管理菜单');
insert into sys_menu values('102',  '菜单管理', '1',   '3', 'menu',       'system/menu/index',        '', 1, 0, 'C', '0', '0', 'system:menu:list',        'tree-table',    'admin', sysdate(), '', null, '菜单管理菜单');
insert into sys_menu values('103',  '部门管理', '1',   '4', 'dept',       'system/dept/index',        '', 1, 0, 'C', '0', '0', 'system:dept:list',        'tree',          'admin', sysdate(), '', null, '部门管理菜单');
insert into sys_menu values('104',  '岗位管理', '1',   '5', 'post',       'system/post/index',        '', 1, 0, 'C', '0', '0', 'system:post:list',        'post',          'admin', sysdate(), '', null, '岗位管理菜单');
insert into sys_menu values('105',  '字典管理', '1',   '6', 'dict',       'system/dict/index',        '', 1, 0, 'C', '0', '0', 'system:dict:list',        'dict',          'admin', sysdate(), '', null, '字典管理菜单');
insert into sys_menu values('106',  '参数设置', '1',   '7', 'config',     'system/config/index',      '', 1, 0, 'C', '0', '0', 'system:config:list',      'edit',          'admin', sysdate(), '', null, '参数设置菜单');
insert into sys_menu values('107',  '通知公告', '1',   '8', 'notice',     'system/notice/index',      '', 1, 0, 'C', '0', '0', 'system:notice:list',      'message',       'admin', sysdate(), '', null, '通知公告菜单');
insert into sys_menu values('108',  '日志管理', '1',   '9', 'log',        '',                         '', 1, 0, 'M', '0', '0', '',                        'log',           'admin', sysdate(), '', null, '日志管理菜单');
insert into sys_menu values('109',  '在线用户', '2',   '1', 'online',     'monitor/online/index',     '', 1, 0, 'C', '0', '0', 'monitor:online:list',     'online',        'admin', sysdate(), '', null, '在线用户菜单');
insert into sys_menu values('110',  '定时任务', '2',   '2', 'job',        'monitor/job/index',        '', 1, 0, 'C', '0', '0', 'monitor:job:list',        'job',           'admin', sysdate(), '', null, '定时任务菜单');
insert into sys_menu values('111',  '数据监控', '2',   '3', 'druid',      'monitor/druid/index',      '', 1, 0, 'C', '0', '0', 'monitor:druid:list',      'druid',         'admin', sysdate(), '', null, '数据监控菜单');
insert into sys_menu values('112',  '服务监控', '2',   '4', 'server',     'monitor/server/index',     '', 1, 0, 'C', '0', '0', 'monitor:server:list',     'server',        'admin', sysdate(), '', null, '服务监控菜单');
insert into sys_menu values('113',  '缓存监控', '2',   '5', 'cache',      'monitor/cache/index',      '', 1, 0, 'C', '0', '0', 'monitor:cache:list',      'redis',         'admin', sysdate(), '', null, '缓存监控菜单');
insert into sys_menu values('114',  '缓存列表', '2',   '6', 'cacheList',  'monitor/cache/list',       '', 1, 0, 'C', '0', '0', 'monitor:cache:list',      'redis-list',    'admin', sysdate(), '', null, '缓存列表菜单');
insert into sys_menu values('115',  '表单构建', '3',   '1', 'build',      'tool/build/index',         '', 1, 0, 'C', '0', '0', 'tool:build:list',         'build',         'admin', sysdate(), '', null, '表单构建菜单');
insert into sys_menu values('116',  '代码生成', '3',   '2', 'gen',        'tool/gen/index',           '', 1, 0, 'C', '0', '0', 'tool:gen:list',           'code',          'admin', sysdate(), '', null, '代码生成菜单');
insert into sys_menu values('117',  '系统接口', '3',   '3', 'swagger',    'tool/swagger/index',       '', 1, 0, 'C', '0', '0', 'tool:swagger:list',       'swagger',       'admin', sysdate(), '', null, '系统接口菜单');
-- 三级菜单
insert into sys_menu values('500',  '操作日志', '108', '1', 'operlog',    'monitor/operlog/index',    '', 1, 0, 'C', '0', '0', 'monitor:operlog:list',    'form',          'admin', sysdate(), '', null, '操作日志菜单');
insert into sys_menu values('501',  '登录日志', '108', '2', 'logininfor', 'monitor/logininfor/index', '', 1, 0, 'C', '0', '0', 'monitor:logininfor:list', 'logininfor',    'admin', sysdate(), '', null, '登录日志菜单');
-- 用户管理按钮
insert into sys_menu values('1000', '用户查询', '100', '1',  '', '', '', 1, 0, 'F', '0', '0', 'system:user:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1001', '用户新增', '100', '2',  '', '', '', 1, 0, 'F', '0', '0', 'system:user:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1002', '用户修改', '100', '3',  '', '', '', 1, 0, 'F', '0', '0', 'system:user:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1003', '用户删除', '100', '4',  '', '', '', 1, 0, 'F', '0', '0', 'system:user:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1004', '用户导出', '100', '5',  '', '', '', 1, 0, 'F', '0', '0', 'system:user:export',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1005', '用户导入', '100', '6',  '', '', '', 1, 0, 'F', '0', '0', 'system:user:import',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1006', '重置密码', '100', '7',  '', '', '', 1, 0, 'F', '0', '0', 'system:user:resetPwd',       '#', 'admin', sysdate(), '', null, '');
-- 角色管理按钮
insert into sys_menu values('1007', '角色查询', '101', '1',  '', '', '', 1, 0, 'F', '0', '0', 'system:role:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1008', '角色新增', '101', '2',  '', '', '', 1, 0, 'F', '0', '0', 'system:role:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1009', '角色修改', '101', '3',  '', '', '', 1, 0, 'F', '0', '0', 'system:role:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1010', '角色删除', '101', '4',  '', '', '', 1, 0, 'F', '0', '0', 'system:role:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1011', '角色导出', '101', '5',  '', '', '', 1, 0, 'F', '0', '0', 'system:role:export',         '#', 'admin', sysdate(), '', null, '');
-- 菜单管理按钮
insert into sys_menu values('1012', '菜单查询', '102', '1',  '', '', '', 1, 0, 'F', '0', '0', 'system:menu:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1013', '菜单新增', '102', '2',  '', '', '', 1, 0, 'F', '0', '0', 'system:menu:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1014', '菜单修改', '102', '3',  '', '', '', 1, 0, 'F', '0', '0', 'system:menu:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1015', '菜单删除', '102', '4',  '', '', '', 1, 0, 'F', '0', '0', 'system:menu:remove',         '#', 'admin', sysdate(), '', null, '');
-- 部门管理按钮
insert into sys_menu values('1016', '部门查询', '103', '1',  '', '', '', 1, 0, 'F', '0', '0', 'system:dept:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1017', '部门新增', '103', '2',  '', '', '', 1, 0, 'F', '0', '0', 'system:dept:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1018', '部门修改', '103', '3',  '', '', '', 1, 0, 'F', '0', '0', 'system:dept:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1019', '部门删除', '103', '4',  '', '', '', 1, 0, 'F', '0', '0', 'system:dept:remove',         '#', 'admin', sysdate(), '', null, '');
-- 岗位管理按钮
insert into sys_menu values('1020', '岗位查询', '104', '1',  '', '', '', 1, 0, 'F', '0', '0', 'system:post:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1021', '岗位新增', '104', '2',  '', '', '', 1, 0, 'F', '0', '0', 'system:post:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1022', '岗位修改', '104', '3',  '', '', '', 1, 0, 'F', '0', '0', 'system:post:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1023', '岗位删除', '104', '4',  '', '', '', 1, 0, 'F', '0', '0', 'system:post:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1024', '岗位导出', '104', '5',  '', '', '', 1, 0, 'F', '0', '0', 'system:post:export',         '#', 'admin', sysdate(), '', null, '');
-- 字典管理按钮
insert into sys_menu values('1025', '字典查询', '105', '1', '#', '', '', 1, 0, 'F', '0', '0', 'system:dict:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1026', '字典新增', '105', '2', '#', '', '', 1, 0, 'F', '0', '0', 'system:dict:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1027', '字典修改', '105', '3', '#', '', '', 1, 0, 'F', '0', '0', 'system:dict:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1028', '字典删除', '105', '4', '#', '', '', 1, 0, 'F', '0', '0', 'system:dict:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1029', '字典导出', '105', '5', '#', '', '', 1, 0, 'F', '0', '0', 'system:dict:export',         '#', 'admin', sysdate(), '', null, '');
-- 参数设置按钮
insert into sys_menu values('1030', '参数查询', '106', '1', '#', '', '', 1, 0, 'F', '0', '0', 'system:config:query',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1031', '参数新增', '106', '2', '#', '', '', 1, 0, 'F', '0', '0', 'system:config:add',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1032', '参数修改', '106', '3', '#', '', '', 1, 0, 'F', '0', '0', 'system:config:edit',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1033', '参数删除', '106', '4', '#', '', '', 1, 0, 'F', '0', '0', 'system:config:remove',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1034', '参数导出', '106', '5', '#', '', '', 1, 0, 'F', '0', '0', 'system:config:export',       '#', 'admin', sysdate(), '', null, '');
-- 通知公告按钮
insert into sys_menu values('1035', '公告查询', '107', '1', '#', '', '', 1, 0, 'F', '0', '0', 'system:notice:query',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1036', '公告新增', '107', '2', '#', '', '', 1, 0, 'F', '0', '0', 'system:notice:add',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1037', '公告修改', '107', '3', '#', '', '', 1, 0, 'F', '0', '0', 'system:notice:edit',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1038', '公告删除', '107', '4', '#', '', '', 1, 0, 'F', '0', '0', 'system:notice:remove',       '#', 'admin', sysdate(), '', null, '');
-- 操作日志按钮
insert into sys_menu values('1039', '操作查询', '500', '1', '#', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:query',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1040', '操作删除', '500', '2', '#', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:remove',     '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1041', '日志导出', '500', '3', '#', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:export',     '#', 'admin', sysdate(), '', null, '');
-- 登录日志按钮
insert into sys_menu values('1042', '登录查询', '501', '1', '#', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:query',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1043', '登录删除', '501', '2', '#', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:remove',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1044', '日志导出', '501', '3', '#', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:export',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1045', '账户解锁', '501', '4', '#', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:unlock',  '#', 'admin', sysdate(), '', null, '');
-- 在线用户按钮
insert into sys_menu values('1046', '在线查询', '109', '1', '#', '', '', 1, 0, 'F', '0', '0', 'monitor:online:query',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1047', '批量强退', '109', '2', '#', '', '', 1, 0, 'F', '0', '0', 'monitor:online:batchLogout', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1048', '单条强退', '109', '3', '#', '', '', 1, 0, 'F', '0', '0', 'monitor:online:forceLogout', '#', 'admin', sysdate(), '', null, '');
-- 定时任务按钮
insert into sys_menu values('1049', '任务查询', '110', '1', '#', '', '', 1, 0, 'F', '0', '0', 'monitor:job:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1050', '任务新增', '110', '2', '#', '', '', 1, 0, 'F', '0', '0', 'monitor:job:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1051', '任务修改', '110', '3', '#', '', '', 1, 0, 'F', '0', '0', 'monitor:job:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1052', '任务删除', '110', '4', '#', '', '', 1, 0, 'F', '0', '0', 'monitor:job:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1053', '状态修改', '110', '5', '#', '', '', 1, 0, 'F', '0', '0', 'monitor:job:changeStatus',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1054', '任务导出', '110', '6', '#', '', '', 1, 0, 'F', '0', '0', 'monitor:job:export',         '#', 'admin', sysdate(), '', null, '');
-- 代码生成按钮
insert into sys_menu values('1055', '生成查询', '116', '1', '#', '', '', 1, 0, 'F', '0', '0', 'tool:gen:query',             '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1056', '生成修改', '116', '2', '#', '', '', 1, 0, 'F', '0', '0', 'tool:gen:edit',              '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1057', '生成删除', '116', '3', '#', '', '', 1, 0, 'F', '0', '0', 'tool:gen:remove',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1058', '导入代码', '116', '4', '#', '', '', 1, 0, 'F', '0', '0', 'tool:gen:import',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1059', '预览代码', '116', '5', '#', '', '', 1, 0, 'F', '0', '0', 'tool:gen:preview',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1060', '生成代码', '116', '6', '#', '', '', 1, 0, 'F', '0', '0', 'tool:gen:code',              '#', 'admin', sysdate(), '', null, '');


-- ----------------------------
-- 6、用户和角色关联表  用户N-1角色
-- ----------------------------
drop table if exists sys_user_role;
create table sys_user_role (
  user_id   bigint(20) not null comment '用户ID',
  role_id   bigint(20) not null comment '角色ID',
  primary key(user_id, role_id)
) engine=innodb comment = '用户和角色关联表';

-- ----------------------------
-- 初始化-用户和角色关联表数据
-- ----------------------------
insert into sys_user_role values ('1', '1');
insert into sys_user_role values ('2', '2');


-- ----------------------------
-- 7、角色和菜单关联表  角色1-N菜单
-- ----------------------------
drop table if exists sys_role_menu;
create table sys_role_menu (
  role_id   bigint(20) not null comment '角色ID',
  menu_id   bigint(20) not null comment '菜单ID',
  primary key(role_id, menu_id)
) engine=innodb comment = '角色和菜单关联表';

-- ----------------------------
-- 初始化-角色和菜单关联表数据
-- ----------------------------
insert into sys_role_menu values ('2', '1');
insert into sys_role_menu values ('2', '2');
insert into sys_role_menu values ('2', '3');
insert into sys_role_menu values ('2', '4');
insert into sys_role_menu values ('2', '100');
insert into sys_role_menu values ('2', '101');
insert into sys_role_menu values ('2', '102');
insert into sys_role_menu values ('2', '103');
insert into sys_role_menu values ('2', '104');
insert into sys_role_menu values ('2', '105');
insert into sys_role_menu values ('2', '106');
insert into sys_role_menu values ('2', '107');
insert into sys_role_menu values ('2', '108');
insert into sys_role_menu values ('2', '109');
insert into sys_role_menu values ('2', '110');
insert into sys_role_menu values ('2', '111');
insert into sys_role_menu values ('2', '112');
insert into sys_role_menu values ('2', '113');
insert into sys_role_menu values ('2', '114');
insert into sys_role_menu values ('2', '115');
insert into sys_role_menu values ('2', '116');
insert into sys_role_menu values ('2', '117');
insert into sys_role_menu values ('2', '500');
insert into sys_role_menu values ('2', '501');
insert into sys_role_menu values ('2', '1000');
insert into sys_role_menu values ('2', '1001');
insert into sys_role_menu values ('2', '1002');
insert into sys_role_menu values ('2', '1003');
insert into sys_role_menu values ('2', '1004');
insert into sys_role_menu values ('2', '1005');
insert into sys_role_menu values ('2', '1006');
insert into sys_role_menu values ('2', '1007');
insert into sys_role_menu values ('2', '1008');
insert into sys_role_menu values ('2', '1009');
insert into sys_role_menu values ('2', '1010');
insert into sys_role_menu values ('2', '1011');
insert into sys_role_menu values ('2', '1012');
insert into sys_role_menu values ('2', '1013');
insert into sys_role_menu values ('2', '1014');
insert into sys_role_menu values ('2', '1015');
insert into sys_role_menu values ('2', '1016');
insert into sys_role_menu values ('2', '1017');
insert into sys_role_menu values ('2', '1018');
insert into sys_role_menu values ('2', '1019');
insert into sys_role_menu values ('2', '1020');
insert into sys_role_menu values ('2', '1021');
insert into sys_role_menu values ('2', '1022');
insert into sys_role_menu values ('2', '1023');
insert into sys_role_menu values ('2', '1024');
insert into sys_role_menu values ('2', '1025');
insert into sys_role_menu values ('2', '1026');
insert into sys_role_menu values ('2', '1027');
insert into sys_role_menu values ('2', '1028');
insert into sys_role_menu values ('2', '1029');
insert into sys_role_menu values ('2', '1030');
insert into sys_role_menu values ('2', '1031');
insert into sys_role_menu values ('2', '1032');
insert into sys_role_menu values ('2', '1033');
insert into sys_role_menu values ('2', '1034');
insert into sys_role_menu values ('2', '1035');
insert into sys_role_menu values ('2', '1036');
insert into sys_role_menu values ('2', '1037');
insert into sys_role_menu values ('2', '1038');
insert into sys_role_menu values ('2', '1039');
insert into sys_role_menu values ('2', '1040');
insert into sys_role_menu values ('2', '1041');
insert into sys_role_menu values ('2', '1042');
insert into sys_role_menu values ('2', '1043');
insert into sys_role_menu values ('2', '1044');
insert into sys_role_menu values ('2', '1045');
insert into sys_role_menu values ('2', '1046');
insert into sys_role_menu values ('2', '1047');
insert into sys_role_menu values ('2', '1048');
insert into sys_role_menu values ('2', '1049');
insert into sys_role_menu values ('2', '1050');
insert into sys_role_menu values ('2', '1051');
insert into sys_role_menu values ('2', '1052');
insert into sys_role_menu values ('2', '1053');
insert into sys_role_menu values ('2', '1054');
insert into sys_role_menu values ('2', '1055');
insert into sys_role_menu values ('2', '1056');
insert into sys_role_menu values ('2', '1057');
insert into sys_role_menu values ('2', '1058');
insert into sys_role_menu values ('2', '1059');
insert into sys_role_menu values ('2', '1060');

-- ----------------------------
-- 8、角色和部门关联表  角色1-N部门
-- ----------------------------
drop table if exists sys_role_dept;
create table sys_role_dept (
  role_id   bigint(20) not null comment '角色ID',
  dept_id   bigint(20) not null comment '部门ID',
  primary key(role_id, dept_id)
) engine=innodb comment = '角色和部门关联表';

-- ----------------------------
-- 初始化-角色和部门关联表数据
-- ----------------------------
insert into sys_role_dept values ('2', '100');
insert into sys_role_dept values ('2', '101');
insert into sys_role_dept values ('2', '105');


-- ----------------------------
-- 9、用户与岗位关联表  用户1-N岗位
-- ----------------------------
drop table if exists sys_user_post;
create table sys_user_post
(
  user_id   bigint(20) not null comment '用户ID',
  post_id   bigint(20) not null comment '岗位ID',
  primary key (user_id, post_id)
) engine=innodb comment = '用户与岗位关联表';

-- ----------------------------
-- 初始化-用户与岗位关联表数据
-- ----------------------------
insert into sys_user_post values ('1', '1');
insert into sys_user_post values ('2', '2');


-- ----------------------------
-- 10、操作日志记录
-- ----------------------------
drop table if exists sys_oper_log;
create table sys_oper_log (
  oper_id           bigint(20)      not null auto_increment    comment '日志主键',
  title             varchar(50)     default ''                 comment '模块标题',
  business_type     int(2)          default 0                  comment '业务类型（0其它 1新增 2修改 3删除）',
  method            varchar(100)    default ''                 comment '方法名称',
  request_method    varchar(10)     default ''                 comment '请求方式',
  operator_type     int(1)          default 0                  comment '操作类别（0其它 1后台用户 2手机端用户）',
  oper_name         varchar(50)     default ''                 comment '操作人员',
  dept_name         varchar(50)     default ''                 comment '部门名称',
  oper_url          varchar(255)    default ''                 comment '请求URL',
  oper_ip           varchar(128)    default ''                 comment '主机地址',
  oper_location     varchar(255)    default ''                 comment '操作地点',
  oper_param        varchar(2000)   default ''                 comment '请求参数',
  json_result       varchar(2000)   default ''                 comment '返回参数',
  status            int(1)          default 0                  comment '操作状态（0正常 1异常）',
  error_msg         varchar(2000)   default ''                 comment '错误消息',
  oper_time         datetime                                   comment '操作时间',
  cost_time         bigint(20)      default 0                  comment '消耗时间',
  primary key (oper_id),
  key idx_sys_oper_log_bt (business_type),
  key idx_sys_oper_log_s  (status),
  key idx_sys_oper_log_ot (oper_time)
) engine=innodb auto_increment=100 comment = '操作日志记录';


-- ----------------------------
-- 11、字典类型表
-- ----------------------------
drop table if exists sys_dict_type;
create table sys_dict_type
(
  dict_id          bigint(20)      not null auto_increment    comment '字典主键',
  dict_name        varchar(100)    default ''                 comment '字典名称',
  dict_type        varchar(100)    default ''                 comment '字典类型',
  status           char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by        varchar(64)     default ''                 comment '创建者',
  create_time      datetime                                   comment '创建时间',
  update_by        varchar(64)     default ''                 comment '更新者',
  update_time      datetime                                   comment '更新时间',
  remark           varchar(500)    default null               comment '备注',
  primary key (dict_id),
  unique (dict_type)
) engine=innodb auto_increment=100 comment = '字典类型表';

insert into sys_dict_type values(1,  '用户性别', 'sys_user_sex',        '0', 'admin', sysdate(), '', null, '用户性别列表');
insert into sys_dict_type values(2,  '菜单状态', 'sys_show_hide',       '0', 'admin', sysdate(), '', null, '菜单状态列表');
insert into sys_dict_type values(3,  '系统开关', 'sys_normal_disable',  '0', 'admin', sysdate(), '', null, '系统开关列表');
insert into sys_dict_type values(4,  '任务状态', 'sys_job_status',      '0', 'admin', sysdate(), '', null, '任务状态列表');
insert into sys_dict_type values(5,  '任务分组', 'sys_job_group',       '0', 'admin', sysdate(), '', null, '任务分组列表');
insert into sys_dict_type values(6,  '系统是否', 'sys_yes_no',          '0', 'admin', sysdate(), '', null, '系统是否列表');
insert into sys_dict_type values(7,  '通知类型', 'sys_notice_type',     '0', 'admin', sysdate(), '', null, '通知类型列表');
insert into sys_dict_type values(8,  '通知状态', 'sys_notice_status',   '0', 'admin', sysdate(), '', null, '通知状态列表');
insert into sys_dict_type values(9,  '操作类型', 'sys_oper_type',       '0', 'admin', sysdate(), '', null, '操作类型列表');
insert into sys_dict_type values(10, '系统状态', 'sys_common_status',   '0', 'admin', sysdate(), '', null, '登录状态列表');


-- ----------------------------
-- 12、字典数据表
-- ----------------------------
drop table if exists sys_dict_data;
create table sys_dict_data
(
  dict_code        bigint(20)      not null auto_increment    comment '字典编码',
  dict_sort        int(4)          default 0                  comment '字典排序',
  dict_label       varchar(100)    default ''                 comment '字典标签',
  dict_value       varchar(100)    default ''                 comment '字典键值',
  dict_type        varchar(100)    default ''                 comment '字典类型',
  css_class        varchar(100)    default null               comment '样式属性（其他样式扩展）',
  list_class       varchar(100)    default null               comment '表格回显样式',
  is_default       char(1)         default 'N'                comment '是否默认（Y是 N否）',
  status           char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by        varchar(64)     default ''                 comment '创建者',
  create_time      datetime                                   comment '创建时间',
  update_by        varchar(64)     default ''                 comment '更新者',
  update_time      datetime                                   comment '更新时间',
  remark           varchar(500)    default null               comment '备注',
  primary key (dict_code)
) engine=innodb auto_increment=100 comment = '字典数据表';

insert into sys_dict_data values(1,  1,  '男',       '0',       'sys_user_sex',        '',   '',        'Y', '0', 'admin', sysdate(), '', null, '性别男');
insert into sys_dict_data values(2,  2,  '女',       '1',       'sys_user_sex',        '',   '',        'N', '0', 'admin', sysdate(), '', null, '性别女');
insert into sys_dict_data values(3,  3,  '未知',     '2',       'sys_user_sex',        '',   '',        'N', '0', 'admin', sysdate(), '', null, '性别未知');
insert into sys_dict_data values(4,  1,  '显示',     '0',       'sys_show_hide',       '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '显示菜单');
insert into sys_dict_data values(5,  2,  '隐藏',     '1',       'sys_show_hide',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '隐藏菜单');
insert into sys_dict_data values(6,  1,  '正常',     '0',       'sys_normal_disable',  '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(7,  2,  '停用',     '1',       'sys_normal_disable',  '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');
insert into sys_dict_data values(8,  1,  '正常',     '0',       'sys_job_status',      '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(9,  2,  '暂停',     '1',       'sys_job_status',      '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');
insert into sys_dict_data values(10, 1,  '默认',     'DEFAULT', 'sys_job_group',       '',   '',        'Y', '0', 'admin', sysdate(), '', null, '默认分组');
insert into sys_dict_data values(11, 2,  '系统',     'SYSTEM',  'sys_job_group',       '',   '',        'N', '0', 'admin', sysdate(), '', null, '系统分组');
insert into sys_dict_data values(12, 1,  '是',       'Y',       'sys_yes_no',          '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '系统默认是');
insert into sys_dict_data values(13, 2,  '否',       'N',       'sys_yes_no',          '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '系统默认否');
insert into sys_dict_data values(14, 1,  '通知',     '1',       'sys_notice_type',     '',   'warning', 'Y', '0', 'admin', sysdate(), '', null, '通知');
insert into sys_dict_data values(15, 2,  '公告',     '2',       'sys_notice_type',     '',   'success', 'N', '0', 'admin', sysdate(), '', null, '公告');
insert into sys_dict_data values(16, 1,  '正常',     '0',       'sys_notice_status',   '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(17, 2,  '关闭',     '1',       'sys_notice_status',   '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '关闭状态');
insert into sys_dict_data values(18, 99, '其他',     '0',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '其他操作');
insert into sys_dict_data values(19, 1,  '新增',     '1',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '新增操作');
insert into sys_dict_data values(20, 2,  '修改',     '2',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '修改操作');
insert into sys_dict_data values(21, 3,  '删除',     '3',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '删除操作');
insert into sys_dict_data values(22, 4,  '授权',     '4',       'sys_oper_type',       '',   'primary', 'N', '0', 'admin', sysdate(), '', null, '授权操作');
insert into sys_dict_data values(23, 5,  '导出',     '5',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '导出操作');
insert into sys_dict_data values(24, 6,  '导入',     '6',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '导入操作');
insert into sys_dict_data values(25, 7,  '强退',     '7',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '强退操作');
insert into sys_dict_data values(26, 8,  '生成代码', '8',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '生成操作');
insert into sys_dict_data values(27, 9,  '清空数据', '9',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '清空操作');
insert into sys_dict_data values(28, 1,  '成功',     '0',       'sys_common_status',   '',   'primary', 'N', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(29, 2,  '失败',     '1',       'sys_common_status',   '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');


-- ----------------------------
-- 13、参数配置表
-- ----------------------------
drop table if exists sys_config;
create table sys_config (
  config_id         int(5)          not null auto_increment    comment '参数主键',
  config_name       varchar(100)    default ''                 comment '参数名称',
  config_key        varchar(100)    default ''                 comment '参数键名',
  config_value      varchar(500)    default ''                 comment '参数键值',
  config_type       char(1)         default 'N'                comment '系统内置（Y是 N否）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (config_id)
) engine=innodb auto_increment=100 comment = '参数配置表';

insert into sys_config values(1, '主框架页-默认皮肤样式名称',     'sys.index.skinName',            'skin-blue',     'Y', 'admin', sysdate(), '', null, '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow' );
insert into sys_config values(2, '用户管理-账号初始密码',         'sys.user.initPassword',         '123456',        'Y', 'admin', sysdate(), '', null, '初始化密码 123456' );
insert into sys_config values(3, '主框架页-侧边栏主题',           'sys.index.sideTheme',           'theme-dark',    'Y', 'admin', sysdate(), '', null, '深色主题theme-dark，浅色主题theme-light' );
insert into sys_config values(4, '账号自助-验证码开关',           'sys.account.captchaEnabled',    'true',          'Y', 'admin', sysdate(), '', null, '是否开启验证码功能（true开启，false关闭）');
insert into sys_config values(5, '账号自助-是否开启用户注册功能', 'sys.account.registerUser',      'false',         'Y', 'admin', sysdate(), '', null, '是否开启注册用户功能（true开启，false关闭）');
insert into sys_config values(6, '用户登录-黑名单列表',           'sys.login.blackIPList',         '',              'Y', 'admin', sysdate(), '', null, '设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）');


-- ----------------------------
-- 14、系统访问记录
-- ----------------------------
drop table if exists sys_logininfor;
create table sys_logininfor (
  info_id        bigint(20)     not null auto_increment   comment '访问ID',
  user_name      varchar(50)    default ''                comment '用户账号',
  ipaddr         varchar(128)   default ''                comment '登录IP地址',
  login_location varchar(255)   default ''                comment '登录地点',
  browser        varchar(50)    default ''                comment '浏览器类型',
  os             varchar(50)    default ''                comment '操作系统',
  status         char(1)        default '0'               comment '登录状态（0成功 1失败）',
  msg            varchar(255)   default ''                comment '提示消息',
  login_time     datetime                                 comment '访问时间',
  primary key (info_id),
  key idx_sys_logininfor_s  (status),
  key idx_sys_logininfor_lt (login_time)
) engine=innodb auto_increment=100 comment = '系统访问记录';


-- ----------------------------
-- 15、定时任务调度表
-- ----------------------------
drop table if exists sys_job;
create table sys_job (
  job_id              bigint(20)    not null auto_increment    comment '任务ID',
  job_name            varchar(64)   default ''                 comment '任务名称',
  job_group           varchar(64)   default 'DEFAULT'          comment '任务组名',
  invoke_target       varchar(500)  not null                   comment '调用目标字符串',
  cron_expression     varchar(255)  default ''                 comment 'cron执行表达式',
  misfire_policy      varchar(20)   default '3'                comment '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
  concurrent          char(1)       default '1'                comment '是否并发执行（0允许 1禁止）',
  status              char(1)       default '0'                comment '状态（0正常 1暂停）',
  create_by           varchar(64)   default ''                 comment '创建者',
  create_time         datetime                                 comment '创建时间',
  update_by           varchar(64)   default ''                 comment '更新者',
  update_time         datetime                                 comment '更新时间',
  remark              varchar(500)  default ''                 comment '备注信息',
  primary key (job_id, job_name, job_group)
) engine=innodb auto_increment=100 comment = '定时任务调度表';

insert into sys_job values(1, '系统默认（无参）', 'DEFAULT', 'ryTask.ryNoParams',        '0/10 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');
insert into sys_job values(2, '系统默认（有参）', 'DEFAULT', 'ryTask.ryParams(\'ry\')',  '0/15 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');
insert into sys_job values(3, '系统默认（多参）', 'DEFAULT', 'ryTask.ryMultipleParams(\'ry\', true, 2000L, 316.50D, 100)',  '0/20 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');


-- ----------------------------
-- 16、定时任务调度日志表
-- ----------------------------
drop table if exists sys_job_log;
create table sys_job_log (
  job_log_id          bigint(20)     not null auto_increment    comment '任务日志ID',
  job_name            varchar(64)    not null                   comment '任务名称',
  job_group           varchar(64)    not null                   comment '任务组名',
  invoke_target       varchar(500)   not null                   comment '调用目标字符串',
  job_message         varchar(500)                              comment '日志信息',
  status              char(1)        default '0'                comment '执行状态（0正常 1失败）',
  exception_info      varchar(2000)  default ''                 comment '异常信息',
  create_time         datetime                                  comment '创建时间',
  primary key (job_log_id)
) engine=innodb comment = '定时任务调度日志表';


-- ----------------------------
-- 17、通知公告表
-- ----------------------------
drop table if exists sys_notice;
create table sys_notice (
  notice_id         int(4)          not null auto_increment    comment '公告ID',
  notice_title      varchar(50)     not null                   comment '公告标题',
  notice_type       char(1)         not null                   comment '公告类型（1通知 2公告）',
  notice_content    longblob        default null               comment '公告内容',
  status            char(1)         default '0'                comment '公告状态（0正常 1关闭）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(255)    default null               comment '备注',
  primary key (notice_id)
) engine=innodb auto_increment=10 comment = '通知公告表';

-- ----------------------------
-- 初始化-公告信息表数据
-- ----------------------------
insert into sys_notice values('1', '温馨提醒：2018-07-01 若依新版本发布啦', '2', '新版本内容', '0', 'admin', sysdate(), '', null, '管理员');
insert into sys_notice values('2', '维护通知：2018-07-01 若依系统凌晨维护', '1', '维护内容',   '0', 'admin', sysdate(), '', null, '管理员');


-- ----------------------------
-- 18、代码生成业务表
-- ----------------------------
drop table if exists gen_table;
create table gen_table (
  table_id          bigint(20)      not null auto_increment    comment '编号',
  table_name        varchar(200)    default ''                 comment '表名称',
  table_comment     varchar(500)    default ''                 comment '表描述',
  sub_table_name    varchar(64)     default null               comment '关联子表的表名',
  sub_table_fk_name varchar(64)     default null               comment '子表关联的外键名',
  class_name        varchar(100)    default ''                 comment '实体类名称',
  tpl_category      varchar(200)    default 'crud'             comment '使用的模板（crud单表操作 tree树表操作）',
  tpl_web_type      varchar(30)     default ''                 comment '前端模板类型（element-ui模版 element-plus模版）',
  package_name      varchar(100)                               comment '生成包路径',
  module_name       varchar(30)                                comment '生成模块名',
  business_name     varchar(30)                                comment '生成业务名',
  function_name     varchar(50)                                comment '生成功能名',
  function_author   varchar(50)                                comment '生成功能作者',
  gen_type          char(1)         default '0'                comment '生成代码方式（0zip压缩包 1自定义路径）',
  gen_path          varchar(200)    default '/'                comment '生成路径（不填默认项目路径）',
  options           varchar(1000)                              comment '其它生成选项',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (table_id)
) engine=innodb auto_increment=1 comment = '代码生成业务表';


-- ----------------------------
-- 19、代码生成业务表字段
-- ----------------------------
drop table if exists gen_table_column;
create table gen_table_column (
  column_id         bigint(20)      not null auto_increment    comment '编号',
  table_id          bigint(20)                                 comment '归属表编号',
  column_name       varchar(200)                               comment '列名称',
  column_comment    varchar(500)                               comment '列描述',
  column_type       varchar(100)                               comment '列类型',
  java_type         varchar(500)                               comment 'JAVA类型',
  java_field        varchar(200)                               comment 'JAVA字段名',
  is_pk             char(1)                                    comment '是否主键（1是）',
  is_increment      char(1)                                    comment '是否自增（1是）',
  is_required       char(1)                                    comment '是否必填（1是）',
  is_insert         char(1)                                    comment '是否为插入字段（1是）',
  is_edit           char(1)                                    comment '是否编辑字段（1是）',
  is_list           char(1)                                    comment '是否列表字段（1是）',
  is_query          char(1)                                    comment '是否查询字段（1是）',
  query_type        varchar(200)    default 'EQ'               comment '查询方式（等于、不等于、大于、小于、范围）',
  html_type         varchar(200)                               comment '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）',
  dict_type         varchar(200)    default ''                 comment '字典类型',
  sort              int                                        comment '排序',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (column_id)
) engine=innodb auto_increment=1 comment = '代码生成业务表字段';

-- Quartz scheduler tables
DROP TABLE IF EXISTS QRTZ_FIRED_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_PAUSED_TRIGGER_GRPS;
DROP TABLE IF EXISTS QRTZ_SCHEDULER_STATE;
DROP TABLE IF EXISTS QRTZ_LOCKS;
DROP TABLE IF EXISTS QRTZ_SIMPLE_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_SIMPROP_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_CRON_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_BLOB_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_JOB_DETAILS;
DROP TABLE IF EXISTS QRTZ_CALENDARS;

-- ----------------------------
-- 1、存储每一个已配置的 jobDetail 的详细信息
-- ----------------------------
create table QRTZ_JOB_DETAILS (
    sched_name           varchar(120)    not null            comment '调度名称',
    job_name             varchar(200)    not null            comment '任务名称',
    job_group            varchar(200)    not null            comment '任务组名',
    description          varchar(250)    null                comment '相关介绍',
    job_class_name       varchar(250)    not null            comment '执行任务类名称',
    is_durable           varchar(1)      not null            comment '是否持久化',
    is_nonconcurrent     varchar(1)      not null            comment '是否并发',
    is_update_data       varchar(1)      not null            comment '是否更新数据',
    requests_recovery    varchar(1)      not null            comment '是否接受恢复执行',
    job_data             blob            null                comment '存放持久化job对象',
    primary key (sched_name, job_name, job_group)
) engine=innodb comment = '任务详细信息表';

-- ----------------------------
-- 2、 存储已配置的 Trigger 的信息
-- ----------------------------
create table QRTZ_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment '触发器的名字',
    trigger_group        varchar(200)    not null            comment '触发器所属组的名字',
    job_name             varchar(200)    not null            comment 'qrtz_job_details表job_name的外键',
    job_group            varchar(200)    not null            comment 'qrtz_job_details表job_group的外键',
    description          varchar(250)    null                comment '相关介绍',
    next_fire_time       bigint(13)      null                comment '上一次触发时间（毫秒）',
    prev_fire_time       bigint(13)      null                comment '下一次触发时间（默认为-1表示不触发）',
    priority             integer         null                comment '优先级',
    trigger_state        varchar(16)     not null            comment '触发器状态',
    trigger_type         varchar(8)      not null            comment '触发器的类型',
    start_time           bigint(13)      not null            comment '开始时间',
    end_time             bigint(13)      null                comment '结束时间',
    calendar_name        varchar(200)    null                comment '日程表名称',
    misfire_instr        smallint(2)     null                comment '补偿执行的策略',
    job_data             blob            null                comment '存放持久化job对象',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, job_name, job_group) references QRTZ_JOB_DETAILS(sched_name, job_name, job_group)
) engine=innodb comment = '触发器详细信息表';

-- ----------------------------
-- 3、 存储简单的 Trigger，包括重复次数，间隔，以及已触发的次数
-- ----------------------------
create table QRTZ_SIMPLE_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    repeat_count         bigint(7)       not null            comment '重复的次数统计',
    repeat_interval      bigint(12)      not null            comment '重复的间隔时间',
    times_triggered      bigint(10)      not null            comment '已经触发的次数',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = '简单触发器的信息表';

-- ----------------------------
-- 4、 存储 Cron Trigger，包括 Cron 表达式和时区信息
-- ---------------------------- 
create table QRTZ_CRON_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    cron_expression      varchar(200)    not null            comment 'cron表达式',
    time_zone_id         varchar(80)                         comment '时区',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = 'Cron类型的触发器表';

-- ----------------------------
-- 5、 Trigger 作为 Blob 类型存储(用于 Quartz 用户用 JDBC 创建他们自己定制的 Trigger 类型，JobStore 并不知道如何存储实例的时候)
-- ---------------------------- 
create table QRTZ_BLOB_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    blob_data            blob            null                comment '存放持久化Trigger对象',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = 'Blob类型的触发器表';

-- ----------------------------
-- 6、 以 Blob 类型存储存放日历信息， quartz可配置一个日历来指定一个时间范围
-- ---------------------------- 
create table QRTZ_CALENDARS (
    sched_name           varchar(120)    not null            comment '调度名称',
    calendar_name        varchar(200)    not null            comment '日历名称',
    calendar             blob            not null            comment '存放持久化calendar对象',
    primary key (sched_name, calendar_name)
) engine=innodb comment = '日历信息表';

-- ----------------------------
-- 7、 存储已暂停的 Trigger 组的信息
-- ---------------------------- 
create table QRTZ_PAUSED_TRIGGER_GRPS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    primary key (sched_name, trigger_group)
) engine=innodb comment = '暂停的触发器表';

-- ----------------------------
-- 8、 存储与已触发的 Trigger 相关的状态信息，以及相联 Job 的执行信息
-- ---------------------------- 
create table QRTZ_FIRED_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    entry_id             varchar(95)     not null            comment '调度器实例id',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    instance_name        varchar(200)    not null            comment '调度器实例名',
    fired_time           bigint(13)      not null            comment '触发的时间',
    sched_time           bigint(13)      not null            comment '定时器制定的时间',
    priority             integer         not null            comment '优先级',
    state                varchar(16)     not null            comment '状态',
    job_name             varchar(200)    null                comment '任务名称',
    job_group            varchar(200)    null                comment '任务组名',
    is_nonconcurrent     varchar(1)      null                comment '是否并发',
    requests_recovery    varchar(1)      null                comment '是否接受恢复执行',
    primary key (sched_name, entry_id)
) engine=innodb comment = '已触发的触发器表';

-- ----------------------------
-- 9、 存储少量的有关 Scheduler 的状态信息，假如是用于集群中，可以看到其他的 Scheduler 实例
-- ---------------------------- 
create table QRTZ_SCHEDULER_STATE (
    sched_name           varchar(120)    not null            comment '调度名称',
    instance_name        varchar(200)    not null            comment '实例名称',
    last_checkin_time    bigint(13)      not null            comment '上次检查时间',
    checkin_interval     bigint(13)      not null            comment '检查间隔时间',
    primary key (sched_name, instance_name)
) engine=innodb comment = '调度器状态表';

-- ----------------------------
-- 10、 存储程序的悲观锁的信息(假如使用了悲观锁)
-- ---------------------------- 
create table QRTZ_LOCKS (
    sched_name           varchar(120)    not null            comment '调度名称',
    lock_name            varchar(40)     not null            comment '悲观锁名称',
    primary key (sched_name, lock_name)
) engine=innodb comment = '存储的悲观锁信息表';

-- ----------------------------
-- 11、 Quartz集群实现同步机制的行锁表
-- ---------------------------- 
create table QRTZ_SIMPROP_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    str_prop_1           varchar(512)    null                comment 'String类型的trigger的第一个参数',
    str_prop_2           varchar(512)    null                comment 'String类型的trigger的第二个参数',
    str_prop_3           varchar(512)    null                comment 'String类型的trigger的第三个参数',
    int_prop_1           int             null                comment 'int类型的trigger的第一个参数',
    int_prop_2           int             null                comment 'int类型的trigger的第二个参数',
    long_prop_1          bigint          null                comment 'long类型的trigger的第一个参数',
    long_prop_2          bigint          null                comment 'long类型的trigger的第二个参数',
    dec_prop_1           numeric(13,4)   null                comment 'decimal类型的trigger的第一个参数',
    dec_prop_2           numeric(13,4)   null                comment 'decimal类型的trigger的第二个参数',
    bool_prop_1          varchar(1)      null                comment 'Boolean类型的trigger的第一个参数',
    bool_prop_2          varchar(1)      null                comment 'Boolean类型的trigger的第二个参数',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = '同步机制的行锁表';

commit;

-- ---------------------------------------------------------------------------
-- Business tables referenced by current code but absent from lingXi.sql
-- ---------------------------------------------------------------------------

DROP TABLE IF EXISTS `tb_task_status`;
CREATE TABLE `tb_task_status` (
  `status_id` int NOT NULL AUTO_INCREMENT COMMENT '状态ID',
  `status_name` varchar(50) NOT NULL COMMENT '状态名称',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用：0停用，1启用',
  PRIMARY KEY (`status_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单状态字典';

DROP TABLE IF EXISTS `tb_node`;
DROP TABLE IF EXISTS `tb_partner`;
DROP TABLE IF EXISTS `tb_region`;

CREATE TABLE `tb_region` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '区域ID',
  `region_name` varchar(50) NOT NULL COMMENT '区域名称',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) DEFAULT '',
  `update_by` varchar(64) DEFAULT '',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tb_region_name` (`region_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='区域表';

CREATE TABLE `tb_partner` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '合作商ID',
  `partner_name` varchar(100) NOT NULL COMMENT '合作商名称',
  `contact_person` varchar(50) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `commission_rate` int NOT NULL DEFAULT 0 COMMENT '分成比例（百分比）',
  `account` varchar(50) DEFAULT NULL COMMENT '登录账号',
  `password` varchar(255) DEFAULT NULL COMMENT '登录密码',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) DEFAULT '',
  `update_by` varchar(64) DEFAULT '',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tb_partner_name` (`partner_name`),
  UNIQUE KEY `uk_tb_partner_account` (`account`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合作商表';

CREATE TABLE `tb_node` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '点位ID',
  `node_name` varchar(100) NOT NULL COMMENT '点位名称',
  `address` varchar(255) NOT NULL COMMENT '详细地址',
  `business_type` int NOT NULL DEFAULT 1 COMMENT '商圈类型',
  `region_id` int NOT NULL COMMENT '区域ID',
  `partner_id` int NOT NULL COMMENT '合作商ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` varchar(64) DEFAULT '',
  `update_by` varchar(64) DEFAULT '',
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_tb_node_region` (`region_id`),
  KEY `idx_tb_node_partner` (`partner_id`),
  CONSTRAINT `fk_tb_node_region` FOREIGN KEY (`region_id`) REFERENCES `tb_region` (`id`),
  CONSTRAINT `fk_tb_node_partner` FOREIGN KEY (`partner_id`) REFERENCES `tb_partner` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点位表';

INSERT INTO `tb_region` (`id`, `region_name`, `create_by`, `remark`) VALUES
  (1, '北京市朝阳区', 'admin', '初始化数据'),
  (2, '北京市海淀区', 'admin', '初始化数据'),
  (3, '北京市西城区', 'admin', '初始化数据');

INSERT INTO `tb_partner` (`id`, `partner_name`, `contact_person`, `contact_phone`, `commission_rate`, `account`, `password`, `create_by`) VALUES
  (1, '默认合作商', '张经理', '13800001001', 15, 'partner01', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 'admin'),
  (2, '海淀合作商', '李经理', '13800001002', 12, 'partner02', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 'admin'),
  (28, '金燕龙合作商', '王经理', '13800001028', 15, 'partner28', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 'admin');

INSERT INTO `tb_node` (`id`, `node_name`, `address`, `business_type`, `region_id`, `partner_id`, `create_by`) VALUES
  (1, '建材城西路点位', '北京市昌平区建材城西路22号', 2, 1, 1, 'admin'),
  (2, '西直门点位', '北京市海淀区西直门北大街32号', 2, 2, 2, 'admin');

INSERT INTO `tb_task_status` (`status_id`, `status_name`, `status`) VALUES
  (1, '待处理', 1),
  (2, '进行中', 1),
  (3, '已完成', 1),
  (4, '已取消', 1);


-- Legacy business tables and seed data
-- MySQL dump 10.13  Distrib 8.0.31, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: dkd-v3
-- ------------------------------------------------------
-- Server version	8.0.31

/*!40101 SET @OLD_CHARACTER_SET_CLIENT = @@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS = @@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION = @@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE = @@TIME_ZONE */;
/*!40103 SET TIME_ZONE = '+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS = @@UNIQUE_CHECKS, UNIQUE_CHECKS = 0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS = @@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS = 0 */;
/*!40101 SET @OLD_SQL_MODE = @@SQL_MODE, SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES = @@SQL_NOTES, SQL_NOTES = 0 */;

--
-- Table structure for table `tb_vendout_running`
--

DROP TABLE IF EXISTS `tb_vendout_running`;
/*!40101 SET @saved_cs_client = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_vendout_running`
(
    `id`           bigint      NOT NULL AUTO_INCREMENT COMMENT 'id',
    `order_no`     varchar(38) NOT NULL DEFAULT '' COMMENT '订单编号',
    `inner_code`   varchar(15) NOT NULL COMMENT '售货机编号',
    `channel_code` varchar(10)          DEFAULT NULL COMMENT '货道编号',
    `status`       char(1)              DEFAULT NULL COMMENT '状态',
    `create_time`  timestamp   NULL     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  timestamp   NULL     DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1665296081440129026
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='出货流水';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tb_vendout_running`
--

LOCK TABLES `tb_vendout_running` WRITE;
/*!40000 ALTER TABLE `tb_vendout_running`
    DISABLE KEYS */;
INSERT INTO `tb_vendout_running`
VALUES (1640253535886454786, 'A1000001972287294582300', 'A1000001', NULL, '1', '2023-03-27 07:24:52',
        '2023-03-27 07:24:52'),
       (1640294341087305730, 'A1000001982025093546600', 'A1000001', NULL, '0', '2023-03-27 10:07:01',
        '2023-03-27 10:07:01'),
       (1640295508953505793, 'A1000001982308461482000', 'A1000001', NULL, '0', '2023-03-27 10:11:39',
        '2023-03-27 10:11:39'),
       (1665193032268836866, 'A10000011867384516930600', 'A1000001', NULL, '0', '2023-06-04 03:05:31',
        '2023-06-04 03:05:31'),
       (1665193181468618753, 'A10000011867429590093200', 'A1000001', NULL, '0', '2023-06-04 03:06:07',
        '2023-06-04 03:06:07'),
       (1665194583502811137, 'A10000011867548883835400', 'A1000001', NULL, '0', '2023-06-04 03:11:41',
        '2023-06-04 03:11:41'),
       (1665197927541698561, 'A10000011868551878012500', 'A1000001', NULL, '0', '2023-06-04 03:24:58',
        '2023-06-04 03:24:58'),
       (1665200173490827265, 'A10000011869054027266500', 'A1000001', NULL, '0', '2023-06-04 03:33:54',
        '2023-06-04 03:33:54'),
       (1665295023343058946, 'A10000011891589370978600', 'A1000001', NULL, '1', '2023-06-04 09:50:48',
        '2023-06-04 09:51:34'),
       (1665296081440129025, 'A10000011891956192562900', 'A1000001', NULL, '0', '2023-06-04 09:55:00',
        '2023-06-04 09:55:00');
/*!40000 ALTER TABLE `tb_vendout_running`
    ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tb_sku`
--

DROP TABLE IF EXISTS `tb_sku`;
/*!40101 SET @saved_cs_client = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_sku`
(
    `sku_id`      bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `sku_name`    varchar(50)  NOT NULL COMMENT '商品名称',
    `sku_image`   varchar(500) NOT NULL COMMENT '商品图片',
    `brand_Name`  varchar(50)  NOT NULL COMMENT '品牌',
    `unit`        varchar(20)           DEFAULT NULL COMMENT '规格(净含量)',
    `price`       int          NOT NULL DEFAULT '1' COMMENT '商品价格，单位分',
    `class_id`    int          NOT NULL COMMENT '商品类型Id',
    `is_discount` tinyint(1)            DEFAULT '0' COMMENT '是否打折促销',
    `create_time` timestamp    NULL     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` timestamp    NULL     DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`sku_id`),
    UNIQUE KEY `tb_sku_sku_name_uindex` (`sku_name`),
    KEY `sku_sku_class_ClassId_fk` (`class_id`),
    CONSTRAINT `tb_sku_ibfk_1` FOREIGN KEY (`class_id`) REFERENCES `tb_sku_class` (`class_id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 12
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='商品表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tb_sku`
--

LOCK TABLES `tb_sku` WRITE;
/*!40000 ALTER TABLE `tb_sku`
    DISABLE KEYS */;
INSERT INTO `tb_sku`
VALUES (1, '可口可乐', 'https://likede2-java.itheima.net/image/product1.png', '可口可乐', '550ML', 200, 1, 1,
        '2020-09-14 01:14:17', '2024-05-14 02:47:42'),
       (2, '康师傅', 'https://likede2-java.itheima.net/image/product2.png', '可口可乐', '330ML', 200, 1, 0,
        '2020-09-14 01:15:43', '2024-05-14 02:47:52'),
       (3, '统一奶茶', 'https://likede2-java.itheima.net/image/product3.png', '可口可乐', '500ML', 100, 1, 0,
        '2020-09-16 06:41:50', '2020-09-16 06:41:50'),
       (4, '青梅绿茶', 'https://likede2-java.itheima.net/image/product4.png', '可口可乐', '500ML', 200, 1, 0,
        '2020-11-20 10:27:35', '2020-11-20 10:27:35'),
       (5, '冰糖雪梨', 'https://likede2-java.itheima.net/image/product5.png', '冰糖雪梨', '500ML', 250, 1, 0,
        '2020-12-18 06:03:41', '2024-05-14 02:47:28'),
       (6, '怡宝至尊', 'https://likede2-java.itheima.net/image/product6.png', '怡宝', '500ML', 200, 1, 0,
        '2020-12-18 06:04:02', '2024-05-14 02:47:35'),
       (7, '一百橙汁', 'https://likede2-java.itheima.net/image/product7.png', '100橙汁自然纯', '500ML', 100, 1, 0,
        '2020-12-18 06:04:26', '2020-12-18 06:04:26'),
       (8, '魔力花茶', 'https://likede2-java.itheima.net/image/product8.png', '茉莉花茶', '500ML', 100, 1, 0,
        '2020-12-18 06:04:45', '2020-12-18 06:04:45'),
       (9, '新星巴克', 'https://likede2-java.itheima.net/image/product9.png', '星巴克', '500ML', 100, 1, 0,
        '2020-12-18 06:05:01', '2020-12-18 06:05:01');
/*!40000 ALTER TABLE `tb_sku`
    ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tb_order_collect`
--

DROP TABLE IF EXISTS `tb_order_collect`;
/*!40101 SET @saved_cs_client = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_order_collect`
(
    `id`                bigint NOT NULL COMMENT 'Id',
    `partner_id`        int          DEFAULT NULL COMMENT '合作商Id',
    `partner_name`      varchar(100) DEFAULT NULL COMMENT '合作商名称',
    `order_total_money` bigint       DEFAULT NULL COMMENT '日订单收入总金额(平台端总数)',
    `order_date`        date         DEFAULT NULL COMMENT '发生日期',
    `total_bill`        int          DEFAULT '0' COMMENT '分成总金额',
    `node_id`           int          DEFAULT NULL,
    `node_name`         varchar(50)  DEFAULT NULL COMMENT '点位',
    `order_count`       int          DEFAULT NULL COMMENT '订单数',
    `ratio`             int          DEFAULT NULL COMMENT '分成比例',
    `region_name`       varchar(50)  DEFAULT NULL COMMENT '区域名称',
    PRIMARY KEY (`id`),
    UNIQUE KEY `tb_order_collect_id_uindex` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='合作商订单汇总表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tb_order_collect`
--

LOCK TABLES `tb_order_collect` WRITE;
/*!40000 ALTER TABLE `tb_order_collect`
    DISABLE KEYS */;
INSERT INTO `tb_order_collect`
VALUES (1701144293748969474, 1, '金燕龙合作商', 6, '2023-09-27', 0, 1, '龙门石窟', 3, 15, '昌平'),
       (1701167701270380546, 1, '金燕龙合作商', 1, '2023-09-27', 0, 1, '龙门石窟', 3, 15, '顺义'),
       (1701168456614256642, 1, '金燕龙合作商', 2, '2023-09-27', 0, 1, '龙门石窟', 3, 15, '海淀'),
       (1701168704594092033, 1, '金燕龙合作商', 4, '2023-09-27', 0, 1, '龙门石窟', 3, 15, '东城'),
       (1701168956252332033, 1, '金燕龙合作商', 5, '2023-09-27', 0, 1, '龙门石窟', 3, 15, '朝阳');
/*!40000 ALTER TABLE `tb_order_collect`
    ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tb_vending_machine`
--

DROP TABLE IF EXISTS `tb_vending_machine`;
/*!40101 SET @saved_cs_client = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_vending_machine`
(
    `id`                   bigint    NOT NULL AUTO_INCREMENT COMMENT '主键',
    `inner_code`           varchar(15)        DEFAULT '000' COMMENT '设备编号',
    `channel_max_capacity` int                DEFAULT NULL COMMENT '设备容量',
    `node_id`              int       NOT NULL COMMENT '点位Id',
    `addr`                 varchar(100)       DEFAULT NULL COMMENT '详细地址',
    `last_supply_time`     datetime  NOT NULL DEFAULT '2000-01-01 00:00:00' COMMENT '上次补货时间',
    `business_type`        int       NOT NULL COMMENT '商圈类型',
    `region_id`            int       NOT NULL COMMENT '区域Id',
    `partner_id`           int       NOT NULL COMMENT '合作商Id',
    `vm_type_id`           int       NOT NULL DEFAULT '0' COMMENT '设备型号',
    `vm_status`            int       NOT NULL DEFAULT '0' COMMENT '设备状态，0:未投放;1-运营;3-撤机',
    `running_status`       varchar(100)       DEFAULT NULL COMMENT '运行状态',
    `longitudes`           double             DEFAULT '0' COMMENT '经度',
    `latitude`             double             DEFAULT '0' COMMENT '维度',
    `client_id`            varchar(50)        DEFAULT NULL COMMENT '客户端连接Id,做emq认证用',
    `policy_id`            bigint             DEFAULT NULL COMMENT '策略id',
    `create_time`          timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`          timestamp NULL     DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `vendingmachine_VmId_uindex` (`inner_code`),
    KEY `vendingmachine_node_Id_fk` (`node_id`),
    KEY `vendingmachine_vmtype_TypeId_fk` (`vm_type_id`),
    KEY `policy_id` (`policy_id`),
    CONSTRAINT `tb_vending_machine_ibfk_1` FOREIGN KEY (`vm_type_id`) REFERENCES `tb_vm_type` (`id`),
    CONSTRAINT `tb_vending_machine_ibfk_2` FOREIGN KEY (`node_id`) REFERENCES `tb_node` (`id`),
    CONSTRAINT `tb_vending_machine_ibfk_3` FOREIGN KEY (`policy_id`) REFERENCES `tb_policy` (`policy_id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 112
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='设备表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tb_vending_machine`
--

LOCK TABLES `tb_vending_machine` WRITE;
/*!40000 ALTER TABLE `tb_vending_machine`
    DISABLE KEYS */;
INSERT INTO `tb_vending_machine`
VALUES (80, 'A1000001', 10, 2, '顺义奥林匹克水上公园', '2023-03-22 00:00:00', 1, 3, 28, 1, 1,
        '{\"statusCode\":\"1001\",\"status\":true}', 0, 0, '70122567fcc13e7615e7239812c20448', 1, '2020-12-18 07:49:03',
        '2024-05-14 01:21:58'),
       (86, 'aim5xu4I', 10, 2, '北京市海淀区西直门北大街32号', '2000-01-01 00:00:00', 2, 1, 1, 2, 1,
        '{\"statusCode\":\"1001\",\"status\":true}', 0, 0, '9d1d927b2651dba9f117a9801e7fd916', 1, '2020-12-18 02:39:22',
        '2024-05-14 01:24:53'),
       (87, '5cy5BdUs', 10, 2, '北京市海淀区西直门北大街32号', '2022-12-05 00:00:00', 2, 1, 1, 2, 1,
        '{\"statusCode\":\"1001\",\"status\":true}', 0, 0, 'fdf33db4de889d6c31fe6351a7333072', 2, '2020-12-18 02:39:22',
        '2024-05-12 15:19:10'),
       (88, 'tCeiyxLx', 10, 1, '北京市昌平区建材城西路22号', '2000-01-01 00:00:00', 2, 1, 1, 1, 1,
        '{\"statusCode\":\"1001\",\"status\":true}', 0, 0, '0805f7585e4366b021268f8850d1e091', 1, '2020-09-10 01:41:02',
        '2024-05-14 01:24:55'),
       (89, 'bR1cfQRa', 10, 2, '北京市海淀区西直门北大街32号', '2000-01-01 00:00:00', 2, 1, 1, 2, 1,
        '{\"statusCode\":\"1001\",\"status\":true}', 0, 0, 'e4e02a07b11865bf262afb5d4507e7b3', 1, '2020-12-18 02:39:22',
        '2024-05-14 01:24:56'),
       (90, 'RhLWjaeR', 10, 1, '北京市昌平区建材城西路22号', '2000-01-01 00:00:00', 2, 1, 1, 1, 1,
        '{\"statusCode\":\"1001\",\"status\":true}', 0, 0, '0cc9e22badf6a0f41b5fad2c8b788fcd', 2, '2020-09-10 01:41:02',
        '2024-05-14 01:25:07'),
       (91, 'qUObmvbM', 10, 2, '北京市海淀区西直门北大街32号', '2022-12-06 00:00:00', 2, 1, 1, 2, 1,
        '{\"statusCode\":\"1001\",\"status\":true}', 0, 0, '34a1779725b4e06edd7cac8a518474f1', 2, '2020-12-18 02:39:22',
        '2024-05-14 01:25:11'),
       (92, 'tU6K5IHg', 10, 1, '北京市昌平区建材城西路22号', '2000-01-01 00:00:00', 2, 1, 1, 2, 1,
        '{\"statusCode\":\"1001\",\"status\":true}', 0, 0, 'fbb7f7c0bfd38785866844f983b3a887', 5, '2020-09-10 01:41:02',
        '2024-05-14 01:25:14'),
       (93, 'iSzMcQXJ', 10, 1, '北京市昌平区建材城西路22号', '2000-01-01 00:00:00', 2, 1, 1, 1, 1,
        '{\"statusCode\":\"1001\",\"status\":true}', 0, 0, '7c6f5ad6edd6e161d6ca8b94d0324fe5', 2, '2020-09-10 01:41:02',
        '2024-05-14 01:25:19'),
       (105, 'nf2UVwi5', 8, 2, '北京顺义区国际鲜花港', '2000-01-01 00:00:00', 1, 3, 1, 4, 0, NULL, 0, 0, NULL, NULL,
        '2020-12-18 07:48:13', '2024-05-13 10:47:32'),
       (106, 'vWgqPhpu', 10, 1, '北京市昌平区建材城西路22号', '2024-05-18 15:26:37', 2, 1, 1, 1, 1, NULL, 0, 0, NULL,
        NULL, '2020-09-10 01:41:02', '2024-05-12 15:20:41'),
       (107, 'SFNuCUe8', 8, 1, '北京市昌平区建材城西路22号', '2000-01-01 00:00:00', 2, 1, 1, 4, 0, NULL, 0, 0, NULL,
        NULL, '2020-09-10 01:41:02', '2024-05-12 15:20:41'),
       (111, 'K6YYXHLY', 10, 2, '北京顺义区国际鲜花港', '2000-01-01 00:00:00', 1, 3, 1, 1, 0,
        '{\"statusCode\":\"1001\",\"status\":true}', 0, 0, NULL, NULL, '2020-12-18 07:48:13', '2024-05-19 07:32:27');
/*!40000 ALTER TABLE `tb_vending_machine`
    ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tb_task_collect`
--

DROP TABLE IF EXISTS `tb_task_collect`;
/*!40101 SET @saved_cs_client = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_task_collect`
(
    `id`             int NOT NULL AUTO_INCREMENT,
    `user_id`        int  DEFAULT NULL,
    `finish_count`   int  DEFAULT '0' COMMENT '当日工单完成数',
    `progress_count` int  DEFAULT '0' COMMENT '当日进行中的工单数',
    `cancel_count`   int  DEFAULT '0' COMMENT '当日取消工单数',
    `collect_date`   date DEFAULT NULL COMMENT '汇总的日期',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 90
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='工单按日统计表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tb_task_collect`
--

LOCK TABLES `tb_task_collect` WRITE;
/*!40000 ALTER TABLE `tb_task_collect`
    DISABLE KEYS */;
INSERT INTO `tb_task_collect`
VALUES (89, NULL, 0, 0, 0, NULL);
/*!40000 ALTER TABLE `tb_task_collect`
    ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tb_channel`
--

DROP TABLE IF EXISTS `tb_channel`;
/*!40101 SET @saved_cs_client = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_channel`
(
    `id`               bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `channel_code`     varchar(10) NOT NULL COMMENT '货道编号',
    `sku_id`           bigint               DEFAULT '0' COMMENT '商品Id',
    `vm_id`            bigint      NOT NULL COMMENT '售货机Id',
    `inner_code`       varchar(15) NOT NULL COMMENT '售货机软编号',
    `max_capacity`     int         NOT NULL DEFAULT '0' COMMENT '货道最大容量',
    `current_capacity` int                  DEFAULT '0' COMMENT '货道当前容量',
    `last_supply_time` datetime             DEFAULT NULL COMMENT '上次补货时间',
    `create_time`      datetime             DEFAULT NULL COMMENT '创建时间',
    `update_time`      datetime             DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`),
    KEY `channel_vendingmachine_Id_fk` (`vm_id`),
    KEY `tb_channel_inner_code_index` (`inner_code`),
    CONSTRAINT `tb_channel_ibfk_1` FOREIGN KEY (`vm_id`) REFERENCES `tb_vending_machine` (`id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 5209
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='售货机货道表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tb_channel`
--

LOCK TABLES `tb_channel` WRITE;
/*!40000 ALTER TABLE `tb_channel`
    DISABLE KEYS */;
INSERT INTO `tb_channel`
VALUES (4703, '1-1', 9, 80, 'A1000001', 10, 8, '2023-03-22 17:16:46', '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4704, '1-2', 2, 80, 'A1000001', 10, 2, '2023-03-22 17:16:46', '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4705, '1-3', 2, 80, 'A1000001', 10, 6, '2023-03-22 17:16:46', '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4706, '1-4', 4, 80, 'A1000001', 10, 10, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4707, '1-5', 3, 80, 'A1000001', 10, 9, '2023-03-22 17:16:46', '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4708, '1-6', 4, 80, 'A1000001', 10, 4, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4709, '2-1', 1, 80, 'A1000001', 10, 10, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4710, '2-2', 6, 80, 'A1000001', 10, 10, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4711, '2-3', 8, 80, 'A1000001', 10, 0, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4712, '2-4', 9, 80, 'A1000001', 10, 9, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4713, '2-5', 2, 80, 'A1000001', 10, 6, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4714, '2-6', 4, 80, 'A1000001', 10, 10, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4715, '3-1', 7, 80, 'A1000001', 10, 5, '2023-03-22 17:16:46', '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4716, '3-2', 6, 80, 'A1000001', 10, 10, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4717, '3-3', 3, 80, 'A1000001', 10, 10, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4718, '3-4', 2, 80, 'A1000001', 10, 10, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4719, '3-5', 2, 80, 'A1000001', 10, 10, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4720, '3-6', 1, 80, 'A1000001', 10, 10, '2023-03-22 17:16:46', '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4721, '4-1', 2, 80, 'A1000001', 10, 1, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4722, '4-2', 4, 80, 'A1000001', 10, 10, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4723, '4-3', 5, 80, 'A1000001', 10, 6, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4724, '4-4', 6, 80, 'A1000001', 10, 10, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4725, '4-5', 6, 80, 'A1000001', 10, 10, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4726, '4-6', 6, 80, 'A1000001', 10, 10, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4727, '5-1', 6, 80, 'A1000001', 10, 10, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4728, '5-2', 6, 80, 'A1000001', 10, 10, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4729, '5-3', 6, 80, 'A1000001', 10, 10, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4730, '5-4', 6, 80, 'A1000001', 10, 10, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4731, '5-5', 9, 80, 'A1000001', 10, 10, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4732, '5-6', 6, 80, 'A1000001', 10, 10, NULL, '2022-05-11 12:12:40', '2024-05-19 16:05:35'),
       (4883, '1-1', 5, 86, 'aim5xu4I', 10, 10, NULL, '2022-12-05 17:01:38', '2022-12-05 17:01:38'),
       (4884, '1-2', 1, 86, 'aim5xu4I', 10, 10, NULL, '2022-12-05 17:01:38', '2022-12-05 17:01:38'),
       (4885, '2-1', 0, 86, 'aim5xu4I', 10, 10, NULL, '2022-12-05 17:01:38', '2022-12-05 17:01:38'),
       (4886, '2-2', 0, 86, 'aim5xu4I', 10, 10, NULL, '2022-12-05 17:01:38', '2022-12-05 17:01:38'),
       (4887, '1-1', 5, 87, '5cy5BdUs', 10, 10, '2022-12-05 21:45:36', '2022-12-05 21:35:56', '2022-12-05 21:45:36'),
       (4888, '1-2', 1, 87, '5cy5BdUs', 10, 10, '2022-12-05 21:45:36', '2022-12-05 21:35:56', '2022-12-05 21:45:36'),
       (4889, '2-1', 0, 87, '5cy5BdUs', 10, 10, NULL, '2022-12-05 21:35:56', '2022-12-05 21:35:56'),
       (4890, '2-2', 0, 87, '5cy5BdUs', 10, 10, NULL, '2022-12-05 21:35:56', '2022-12-05 21:35:56'),
       (4891, '1-1', 2, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4892, '1-2', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4893, '1-3', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4894, '1-4', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4895, '1-5', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4896, '1-6', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4897, '2-1', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4898, '2-2', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4899, '2-3', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4900, '2-4', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4901, '2-5', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4902, '2-6', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4903, '3-1', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4904, '3-2', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4905, '3-3', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4906, '3-4', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4907, '3-5', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4908, '3-6', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4909, '4-1', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4910, '4-2', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4911, '4-3', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4912, '4-4', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4913, '4-5', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4914, '4-6', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4915, '5-1', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4916, '5-2', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4917, '5-3', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4918, '5-4', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4919, '5-5', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4920, '5-6', 0, 88, 'tCeiyxLx', 10, 10, NULL, '2022-12-05 22:39:26', '2024-05-16 22:02:27'),
       (4921, '1-1', 2, 89, 'bR1cfQRa', 10, 10, NULL, '2022-12-06 10:06:11', '2023-09-22 15:43:44'),
       (4922, '1-2', 0, 89, 'bR1cfQRa', 10, 10, NULL, '2022-12-06 10:06:11', '2022-12-06 10:06:11'),
       (4923, '2-1', 0, 89, 'bR1cfQRa', 10, 10, NULL, '2022-12-06 10:06:11', '2022-12-06 10:06:11'),
       (4924, '2-2', 0, 89, 'bR1cfQRa', 10, 10, NULL, '2022-12-06 10:06:11', '2022-12-06 10:06:11'),
       (4925, '1-1', 2, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4926, '1-2', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4927, '1-3', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4928, '1-4', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4929, '1-5', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4930, '1-6', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4931, '2-1', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4932, '2-2', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4933, '2-3', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4934, '2-4', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4935, '2-5', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4936, '2-6', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4937, '3-1', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4938, '3-2', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4939, '3-3', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4940, '3-4', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4941, '3-5', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4942, '3-6', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4943, '4-1', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4944, '4-2', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4945, '4-3', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4946, '4-4', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4947, '4-5', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4948, '4-6', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4949, '5-1', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4950, '5-2', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4951, '5-3', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4952, '5-4', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4953, '5-5', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4954, '5-6', 0, 90, 'RhLWjaeR', 10, 10, NULL, '2022-12-06 10:17:32', '2024-05-15 16:31:18'),
       (4955, '1-1', 5, 91, 'qUObmvbM', 10, 10, '2022-12-06 15:11:20', '2022-12-06 14:58:46', '2022-12-06 15:11:20'),
       (4956, '1-2', 1, 91, 'qUObmvbM', 10, 10, '2022-12-06 15:11:20', '2022-12-06 14:58:46', '2022-12-06 15:11:20'),
       (4957, '2-1', 0, 91, 'qUObmvbM', 10, 10, NULL, '2022-12-06 14:58:46', '2022-12-06 14:58:46'),
       (4958, '2-2', 0, 91, 'qUObmvbM', 10, 10, NULL, '2022-12-06 14:58:46', '2022-12-06 14:58:46'),
       (4959, '1-1', 0, 92, 'tU6K5IHg', 10, 0, NULL, '2023-01-03 19:37:43', '2023-01-03 19:37:43'),
       (4960, '1-2', 0, 92, 'tU6K5IHg', 10, 0, NULL, '2023-01-03 19:37:43', '2023-01-03 19:37:43'),
       (4961, '2-1', 0, 92, 'tU6K5IHg', 10, 0, NULL, '2023-01-03 19:37:43', '2023-01-03 19:37:43'),
       (4962, '2-2', 0, 92, 'tU6K5IHg', 10, 0, NULL, '2023-01-03 19:37:43', '2023-01-03 19:37:43'),
       (4963, '1-1', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4964, '1-2', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4965, '1-3', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4966, '1-4', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4967, '1-5', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4968, '1-6', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4969, '2-1', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4970, '2-2', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4971, '2-3', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4972, '2-4', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4973, '2-5', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4974, '2-6', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4975, '3-1', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4976, '3-2', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4977, '3-3', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4978, '3-4', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4979, '3-5', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4980, '3-6', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4981, '4-1', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4982, '4-2', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4983, '4-3', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4984, '4-4', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4985, '4-5', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4986, '4-6', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4987, '5-1', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4988, '5-2', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4989, '5-3', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4990, '5-4', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4991, '5-5', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (4992, '5-6', 0, 93, 'iSzMcQXJ', 10, 0, NULL, '2023-02-01 11:16:02', '2023-02-01 11:16:02'),
       (5027, '1-1', 0, 105, 'nf2UVwi5', 8, 0, NULL, '2024-05-13 21:37:53', NULL),
       (5028, '1-2', 0, 105, 'nf2UVwi5', 8, 0, NULL, '2024-05-13 21:37:53', NULL),
       (5029, '1-3', 0, 105, 'nf2UVwi5', 8, 0, NULL, '2024-05-13 21:37:53', NULL),
       (5030, '1-4', 0, 105, 'nf2UVwi5', 8, 0, NULL, '2024-05-13 21:37:53', NULL),
       (5031, '2-1', 0, 105, 'nf2UVwi5', 8, 0, NULL, '2024-05-13 21:37:53', NULL),
       (5032, '2-2', 0, 105, 'nf2UVwi5', 8, 0, NULL, '2024-05-13 21:37:53', NULL),
       (5033, '2-3', 0, 105, 'nf2UVwi5', 8, 0, NULL, '2024-05-13 21:37:53', NULL),
       (5034, '2-4', 0, 105, 'nf2UVwi5', 8, 0, NULL, '2024-05-13 21:37:53', NULL),
       (5035, '3-1', 0, 105, 'nf2UVwi5', 8, 0, NULL, '2024-05-13 21:37:53', NULL),
       (5036, '3-2', 0, 105, 'nf2UVwi5', 8, 0, NULL, '2024-05-13 21:37:53', NULL),
       (5037, '3-3', 0, 105, 'nf2UVwi5', 8, 0, NULL, '2024-05-13 21:37:53', NULL),
       (5038, '3-4', 0, 105, 'nf2UVwi5', 8, 0, NULL, '2024-05-13 21:37:53', NULL),
       (5039, '4-1', 0, 105, 'nf2UVwi5', 8, 0, NULL, '2024-05-13 21:37:53', NULL),
       (5040, '4-2', 0, 105, 'nf2UVwi5', 8, 0, NULL, '2024-05-13 21:37:53', NULL),
       (5041, '4-3', 0, 105, 'nf2UVwi5', 8, 0, NULL, '2024-05-13 21:37:53', NULL),
       (5042, '4-4', 0, 105, 'nf2UVwi5', 8, 0, NULL, '2024-05-13 21:37:53', NULL),
       (5043, '1-1', 1, 106, 'vWgqPhpu', 10, 5, '2024-05-18 15:26:37', '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5044, '1-2', 2, 106, 'vWgqPhpu', 10, 5, '2024-05-18 15:26:37', '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5045, '1-3', 3, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5046, '1-4', 8, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5047, '1-5', 6, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5048, '1-6', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5049, '2-1', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5050, '2-2', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5051, '2-3', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5052, '2-4', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5053, '2-5', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5054, '2-6', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5055, '3-1', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5056, '3-2', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5057, '3-3', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5058, '3-4', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5059, '3-5', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5060, '3-6', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5061, '4-1', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5062, '4-2', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5063, '4-3', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5064, '4-4', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5065, '4-5', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5066, '4-6', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5067, '5-1', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5068, '5-2', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5069, '5-3', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5070, '5-4', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5071, '5-5', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5072, '5-6', 0, 106, 'vWgqPhpu', 10, 0, NULL, '2024-05-15 14:26:08', '2024-05-18 15:02:48'),
       (5073, '1-1', 1, 107, 'SFNuCUe8', 8, 0, NULL, '2024-05-15 14:26:48', '2024-05-15 16:19:18'),
       (5074, '1-2', 2, 107, 'SFNuCUe8', 8, 0, NULL, '2024-05-15 14:26:48', '2024-05-15 16:19:18'),
       (5075, '1-3', 2, 107, 'SFNuCUe8', 8, 0, NULL, '2024-05-15 14:26:48', '2024-05-15 16:19:18'),
       (5076, '1-4', 4, 107, 'SFNuCUe8', 8, 0, NULL, '2024-05-15 14:26:48', '2024-05-15 16:19:18'),
       (5077, '2-1', 0, 107, 'SFNuCUe8', 8, 0, NULL, '2024-05-15 14:26:48', '2024-05-15 16:19:18'),
       (5078, '2-2', 0, 107, 'SFNuCUe8', 8, 0, NULL, '2024-05-15 14:26:48', '2024-05-15 16:19:18'),
       (5079, '2-3', 0, 107, 'SFNuCUe8', 8, 0, NULL, '2024-05-15 14:26:48', '2024-05-15 16:19:18'),
       (5080, '2-4', 0, 107, 'SFNuCUe8', 8, 0, NULL, '2024-05-15 14:26:48', '2024-05-15 16:19:18'),
       (5081, '3-1', 0, 107, 'SFNuCUe8', 8, 0, NULL, '2024-05-15 14:26:48', '2024-05-15 16:19:18'),
       (5082, '3-2', 0, 107, 'SFNuCUe8', 8, 0, NULL, '2024-05-15 14:26:48', '2024-05-15 16:19:18'),
       (5083, '3-3', 0, 107, 'SFNuCUe8', 8, 0, NULL, '2024-05-15 14:26:48', '2024-05-15 16:19:18'),
       (5084, '3-4', 0, 107, 'SFNuCUe8', 8, 0, NULL, '2024-05-15 14:26:48', '2024-05-15 16:19:18'),
       (5085, '4-1', 0, 107, 'SFNuCUe8', 8, 0, NULL, '2024-05-15 14:26:48', '2024-05-15 16:19:18'),
       (5086, '4-2', 0, 107, 'SFNuCUe8', 8, 0, NULL, '2024-05-15 14:26:48', '2024-05-15 16:19:18'),
       (5087, '4-3', 0, 107, 'SFNuCUe8', 8, 0, NULL, '2024-05-15 14:26:48', '2024-05-15 16:19:18'),
       (5088, '4-4', 0, 107, 'SFNuCUe8', 8, 0, NULL, '2024-05-15 14:26:48', '2024-05-15 16:19:18'),
       (5179, '1-1', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5180, '1-2', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5181, '1-3', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5182, '1-4', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5183, '1-5', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5184, '1-6', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5185, '2-1', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5186, '2-2', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5187, '2-3', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5188, '2-4', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5189, '2-5', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5190, '2-6', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5191, '3-1', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5192, '3-2', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5193, '3-3', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5194, '3-4', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5195, '3-5', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5196, '3-6', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5197, '4-1', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5198, '4-2', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5199, '4-3', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5200, '4-4', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5201, '4-5', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5202, '4-6', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5203, '5-1', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5204, '5-2', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5205, '5-3', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5206, '5-4', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5207, '5-5', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27'),
       (5208, '5-6', 0, 111, 'K6YYXHLY', 10, 0, NULL, '2024-05-19 15:28:27', '2024-05-19 15:28:27');
/*!40000 ALTER TABLE `tb_channel`
    ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tb_order_month_collect`
--

DROP TABLE IF EXISTS `tb_order_month_collect`;
/*!40101 SET @saved_cs_client = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_order_month_collect`
(
    `id`                bigint NOT NULL COMMENT 'id',
    `partner_id`        int          DEFAULT NULL COMMENT '合作商Id',
    `partner_name`      varchar(100) DEFAULT NULL COMMENT '合作商名称',
    `region_id`         int          DEFAULT NULL COMMENT '区域Id',
    `region_name`       varchar(50)  DEFAULT NULL COMMENT '地区名称',
    `order_total_money` bigint       DEFAULT NULL COMMENT '订单总金额',
    `order_total_count` bigint       DEFAULT NULL COMMENT '订单总数',
    `month`             int          DEFAULT NULL COMMENT '月份',
    `year`              int          DEFAULT NULL COMMENT '年份',
    PRIMARY KEY (`id`),
    UNIQUE KEY `tb_order_month_collect_id_uindex` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='按月统计各公司销售情况表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tb_order_month_collect`
--

LOCK TABLES `tb_order_month_collect` WRITE;
/*!40000 ALTER TABLE `tb_order_month_collect`
    DISABLE KEYS */;
/*!40000 ALTER TABLE `tb_order_month_collect`
    ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tb_policy`
--

DROP TABLE IF EXISTS `tb_policy`;
/*!40101 SET @saved_cs_client = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_policy`
(
    `policy_id`   bigint    NOT NULL AUTO_INCREMENT COMMENT '策略id',
    `policy_name` varchar(30)    DEFAULT NULL COMMENT '策略名称',
    `discount`    int            DEFAULT NULL COMMENT '策略方案，如：80代表8折',
    `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`policy_id`),
    UNIQUE KEY `tb_policy_policy_name_uindex` (`policy_name`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 11
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='策略表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tb_policy`
--

LOCK TABLES `tb_policy` WRITE;
/*!40000 ALTER TABLE `tb_policy`
    DISABLE KEYS */;
INSERT INTO `tb_policy`
VALUES (1, '九折优惠', 90, '2020-09-14 02:51:05', '2021-02-01 08:25:06'),
       (2, '八折优惠', 80, '2020-12-18 06:10:39', '2020-12-18 06:10:39'),
       (5, '冬季折扣', 70, '2021-01-11 07:29:32', '2024-05-13 14:15:32'),
       (9, '清爽夏日', 50, '2021-02-01 08:23:10', '2024-05-13 14:15:51');
/*!40000 ALTER TABLE `tb_policy`
    ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tb_task_details`
--

DROP TABLE IF EXISTS `tb_task_details`;
/*!40101 SET @saved_cs_client = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_task_details`
(
    `details_id`      bigint NOT NULL AUTO_INCREMENT,
    `task_id`         bigint                                                        DEFAULT NULL COMMENT '工单Id',
    `channel_code`    varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '货道编号',
    `expect_capacity` int    NOT NULL                                               DEFAULT '0' COMMENT '补货期望容量',
    `sku_id`          bigint                                                        DEFAULT NULL COMMENT '商品Id',
    `sku_name`        varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  DEFAULT NULL,
    `sku_image`       varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    PRIMARY KEY (`details_id`),
    KEY `taskdetails_task_TaskId_fk` (`task_id`),
    CONSTRAINT `taskdetails_task_TaskId_fk` FOREIGN KEY (`task_id`) REFERENCES `tb_task` (`task_id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 3770
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='工单详情，只有补货工单才有';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tb_task_details`
--

LOCK TABLES `tb_task_details` WRITE;
/*!40000 ALTER TABLE `tb_task_details`
    DISABLE KEYS */;
INSERT INTO `tb_task_details`
VALUES (3750, 526, '1-1', 8, 1, '可口可乐', 'https://likede2-java.itheima.net/image/product1.png'),
       (3751, 526, '1-2', 9, 1, '可口可乐', 'https://likede2-java.itheima.net/image/product1.png'),
       (3752, 526, '1-3', 1, 2, '小康师傅', 'https://likede2-java.itheima.net/image/product2.png'),
       (3753, 526, '1-5', 6, 3, '统一奶茶', 'https://likede2-java.itheima.net/image/product3.png'),
       (3754, 526, '3-1', 1, 7, '一百橙汁', 'https://likede2-java.itheima.net/image/product7.png'),
       (3755, 526, '3-6', 10, 1, '可口可乐', 'https://likede2-java.itheima.net/image/product1.png'),
       (3768, 542, '1-1', 5, 1, '可口可乐', 'https://likede2-java.itheima.net/image/product1.png'),
       (3769, 542, '1-2', 5, 2, '康师傅', 'https://likede2-java.itheima.net/image/product2.png');
/*!40000 ALTER TABLE `tb_task_details`
    ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tb_job`
--

DROP TABLE IF EXISTS `tb_job`;
/*!40101 SET @saved_cs_client = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_job`
(
    `id`          int NOT NULL AUTO_INCREMENT COMMENT '主键',
    `alert_value` int DEFAULT '0' COMMENT '警戒值百分比',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 2
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='自动补货任务';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tb_job`
--

LOCK TABLES `tb_job` WRITE;
/*!40000 ALTER TABLE `tb_job`
    DISABLE KEYS */;
INSERT INTO `tb_job`
VALUES (1, 80);
/*!40000 ALTER TABLE `tb_job`
    ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tb_vm_type`
--

DROP TABLE IF EXISTS `tb_vm_type`;
/*!40101 SET @saved_cs_client = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_vm_type`
(
    `id`                   int         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`                 varchar(15) NOT NULL COMMENT '型号名称',
    `model`                varchar(20)          DEFAULT NULL COMMENT '型号编码',
    `image`                varchar(500)         DEFAULT NULL COMMENT '设备图片',
    `vm_row`               int         NOT NULL DEFAULT '1' COMMENT '货道行',
    `vm_col`               int         NOT NULL DEFAULT '1' COMMENT '货道列',
    `channel_max_capacity` int                  DEFAULT '0' COMMENT '设备容量',
    PRIMARY KEY (`id`),
    UNIQUE KEY `tb_vm_type_name_uindex` (`name`),
    UNIQUE KEY `tb_vm_type_model_uindex` (`model`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 21
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='设备类型表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tb_vm_type`
--

LOCK TABLES `tb_vm_type` WRITE;
/*!40000 ALTER TABLE `tb_vm_type`
    DISABLE KEYS */;
INSERT INTO `tb_vm_type`
VALUES (1, '饮料机', 'CZ-10011', 'https://sy-192-ys.oss-cn-beijing.aliyuncs.com/images/2024/05/15/1715762932971.png', 5,
        6, 10),
       (2, '综合机', 'CZ-10012', 'https://sy-192-ys.oss-cn-beijing.aliyuncs.com/images/2024/05/15/1715762984492.png', 2,
        2, 10),
       (3, '零食机', 'CZ-10013', 'https://sy-192-ys.oss-cn-beijing.aliyuncs.com/images/2024/05/15/1715763009476.png', 2,
        5, 5),
       (4, '果蔬机', 'CZ-10014', 'https://sy-192-ys.oss-cn-beijing.aliyuncs.com/images/2024/05/15/1715763319518.png\n',
        4, 4, 8);
/*!40000 ALTER TABLE `tb_vm_type`
    ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tb_task`
--

DROP TABLE IF EXISTS `tb_task`;
/*!40101 SET @saved_cs_client = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_task`
(
    `task_id`         bigint                                                       NOT NULL AUTO_INCREMENT COMMENT '工单ID',
    `task_code`       varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工单编号',
    `task_status`     int                                                               DEFAULT NULL COMMENT '工单状态',
    `create_type`     int                                                               DEFAULT NULL COMMENT '创建类型 0：自动 1：手动',
    `inner_code`      varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci      DEFAULT NULL COMMENT '售货机编码',
    `user_id`         int                                                               DEFAULT NULL COMMENT '执行人id',
    `user_name`       varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci      DEFAULT NULL COMMENT '执行人名称',
    `region_id`       bigint                                                            DEFAULT NULL COMMENT '所属区域Id',
    `desc`            varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci     DEFAULT NULL COMMENT '备注',
    `product_type_id` int                                                               DEFAULT '1' COMMENT '工单类型id',
    `assignor_id`     int                                                               DEFAULT NULL COMMENT '指派人Id',
    `addr`            varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci     DEFAULT NULL COMMENT '地址',
    `create_time`     timestamp                                                    NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     timestamp                                                    NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`task_id`),
    UNIQUE KEY `tb_task_task_code_uindex` (`task_code`),
    KEY `task_productiontype_TypeId_fk` (`product_type_id`),
    KEY `task_taskstatustype_StatusID_fk` (`task_status`),
    KEY `task_tasktype_TypeId_fk` (`create_type`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 544
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='工单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tb_user_task_relation`
--

DROP TABLE IF EXISTS `tb_user_task_relation`;
/*!40101 SET @saved_cs_client = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_user_task_relation`
(
    `id`             bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`        bigint NOT NULL COMMENT '系统用户ID',
    `task_id`        bigint NOT NULL COMMENT '工单ID',
    `relation_type`  int    NOT NULL DEFAULT 0 COMMENT '关联类型：0-执行人 1-指派人',
    `create_time`    timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_task_type` (`user_id`, `task_id`, `relation_type`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='用户工单关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tb_emp_sys_user_mapping`
--

DROP TABLE IF EXISTS `tb_emp_sys_user_mapping`;
/*!40101 SET @saved_cs_client = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_emp_sys_user_mapping`
(
    `id`             bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `emp_id`         bigint NOT NULL COMMENT '员工ID',
    `sys_user_id`    bigint NOT NULL COMMENT '系统用户ID',
    `is_valid`       tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否有效：1-有效 0-无效',
    `create_time`    timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_emp_sys_user` (`emp_id`, `sys_user_id`),
    KEY `idx_emp_id` (`emp_id`),
    KEY `idx_sys_user_id` (`sys_user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='员工系统用户映射表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tb_task`
--

LOCK TABLES `tb_task` WRITE;
/*!40000 ALTER TABLE `tb_task`
    DISABLE KEYS */;
INSERT INTO `tb_task`
VALUES (523, '202303220001', 4, 1, 'RhLWjaeR', 8, '许褚', 1, '投放', 1, 1, '河南省-洛阳市-龙门石窟',
        '2023-03-22 07:51:54', '2023-03-22 07:57:05'),
       (524, '202303220002', 4, 1, 'iSzMcQXJ', 8, '许褚', 1, '投放', 1, 1, '河南省-洛阳市-龙门石窟',
        '2023-03-22 08:02:26', '2023-03-22 08:13:24'),
       (525, '202303220003', 4, 1, 'iSzMcQXJ', 8, '许褚', 1, '11', 1, 1, '河南省-洛阳市-龙门石窟',
        '2023-03-22 09:11:22', '2023-03-22 09:12:15'),
       (526, '202303220004', 4, 1, 'A1000001', 6, '曹操', 1, '1', 2, 1, '河南省-洛阳市-龙门石窟', '2023-03-22 09:14:45',
        '2023-03-22 09:16:46'),
       (527, '202303240001', 4, 1, 'A1000001', 9, '张辽', 1, '111', 3, 1, '河南省-洛阳市-龙门石窟',
        '2023-03-24 02:41:10', '2023-03-24 02:41:10'),
       (528, '202303240002', 4, 1, 'Ut548Hpf', 8, '许褚', 1, '111', 3, 1, '河南省-洛阳市-洛阳白云山',
        '2023-03-24 02:42:50', '2023-03-24 02:42:50'),
       (529, '202303240003', 4, 1, 'aim5xu4I', 50, '孙一百', 1, '111', 3, 1, '河南省-洛阳市-洛阳白云山',
        '2023-03-24 02:43:43', '2023-03-24 02:43:43'),
       (530, '202303240004', 4, 1, '5cy5BdUs', 50, '孙一百', 1, '111', 3, 1, '河南省-洛阳市-洛阳白云山',
        '2023-03-24 02:44:26', '2023-03-24 02:44:26'),
       (531, '202303240005', 1, 0, 'A1000001', 50, '孙一百', 1,
        '{\"innerCode\":\"A1000001\",\"statusInfo\":[{\"statusCode\":\"1001\",\"status\":true},{\"statusCode\":\"1002\",\"status\":false},{\"statusCode\":\"1003\",\"status\":true}]}',
        3, 0, '河南省-洛阳市-龙门石窟', '2023-03-24 10:04:44', '2023-03-24 10:04:44'),
       (533, '202309200001', 3, 1, 'RhLWjaeR', 8, '许褚', 1, '', 1, 1, '河南省-洛阳市-龙门石窟', '2023-09-20 08:30:53',
        '2024-05-18 08:08:00'),
       (535, '202405170001', 4, 1, 'vWgqPhpu', 8, '许褚', 1, '不想干了', 1, 1, '北京市昌平区建材城西路22号',
        '2024-05-17 06:39:26', NULL),
       (542, '202405180001', 4, 1, 'vWgqPhpu', 6, '曹操', 1, '卖完货了', 2, 1, '北京市昌平区建材城西路22号',
        '2024-05-18 07:13:05', NULL),
       (543, '202405190001', 1, 1, 'K6YYXHLY', 13, '陆逊', 3, '', 1, 1, '北京顺义区国际鲜花港', '2024-05-19 10:45:06',
        '2024-05-19 13:13:16');
/*!40000 ALTER TABLE `tb_task`
    ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tb_sku_class`
--

DROP TABLE IF EXISTS `tb_sku_class`;
/*!40101 SET @saved_cs_client = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_sku_class`
(
    `class_id`   int NOT NULL AUTO_INCREMENT COMMENT '主键',
    `class_name` varchar(50) DEFAULT '' COMMENT '类别名称',
    `parent_id`  int         DEFAULT '0' COMMENT '上级id',
    PRIMARY KEY (`class_id`),
    UNIQUE KEY `tb_sku_class_class_name_uindex` (`class_name`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 15
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='商品类型表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tb_sku_class`
--

LOCK TABLES `tb_sku_class` WRITE;
/*!40000 ALTER TABLE `tb_sku_class`
    DISABLE KEYS */;
INSERT INTO `tb_sku_class`
VALUES (1, '饮料', 0),
       (2, '零食', 0),
       (3, '食品', 0),
       (4, '玩具', 0),
       (14, '化妆品', 0);
/*!40000 ALTER TABLE `tb_sku_class`
    ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tb_order`
--

DROP TABLE IF EXISTS `tb_order`;
/*!40101 SET @saved_cs_client = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_order`
(
    `id`            bigint      NOT NULL COMMENT '主键',
    `order_no`      varchar(50) NOT NULL COMMENT '订单编号',
    `third_no`      varchar(34)                                                  DEFAULT NULL COMMENT '第三方平台单号',
    `inner_code`    varchar(15)                                                  DEFAULT NULL COMMENT '机器编号',
    `channel_code`  varchar(10)                                                  DEFAULT NULL COMMENT '货道编号',
    `sku_id`        bigint                                                       DEFAULT NULL COMMENT 'skuId',
    `sku_name`      varchar(20)                                                  DEFAULT NULL COMMENT '商品名称',
    `class_id`      int                                                          DEFAULT NULL COMMENT '商品类别Id',
    `status`        int                                                          DEFAULT NULL COMMENT '订单状态:0-待支付;1-支付完成;2-出货成功;3-出货失败;4-已取消',
    `amount`        int         NOT NULL                                         DEFAULT '0' COMMENT '支付金额',
    `price`         int         NOT NULL                                         DEFAULT '0' COMMENT '商品金额',
    `pay_type`      varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '支付类型，1支付宝 2微信',
    `pay_status`    int                                                          DEFAULT '0' COMMENT '支付状态，0-未支付;1-支付完成;2-退款中;3-退款完成',
    `bill`          int                                                          DEFAULT '0' COMMENT '合作商账单金额',
    `addr`          varchar(200)                                                 DEFAULT NULL COMMENT '点位地址',
    `region_id`     bigint                                                       DEFAULT NULL COMMENT '所属区域Id',
    `region_name`   varchar(50)                                                  DEFAULT NULL COMMENT '区域名称',
    `business_type` int                                                          DEFAULT NULL COMMENT '所属商圈',
    `partner_id`    int                                                          DEFAULT NULL COMMENT '合作商Id',
    `open_id`       varchar(200)                                                 DEFAULT NULL COMMENT '跨站身份验证',
    `node_id`       bigint                                                       DEFAULT NULL COMMENT '点位Id',
    `node_name`     varchar(50)                                                  DEFAULT NULL COMMENT '点位名称',
    `cancel_desc`   varchar(200)                                                 DEFAULT '' COMMENT '取消原因',
    `create_time`   timestamp   NULL                                             DEFAULT NULL COMMENT '创建时间',
    `update_time`   timestamp   NULL                                             DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `Order_OrderNo_uindex` (`order_no`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='订单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tb_order`
--

LOCK TABLES `tb_order` WRITE;
/*!40000 ALTER TABLE `tb_order`
    DISABLE KEYS */;
INSERT INTO `tb_order`
VALUES (1639542977692344321, 'A1000001802891882192300', NULL, 'A1000001', NULL, 3, '统一奶茶', 1, 1, 1, 1, 'wxpay', 1,
        0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '1', '2023-03-25 08:21:22',
        '2023-03-25 08:21:51'),
       (1639551491689062401, 'A1000001804921842908200', NULL, 'A1000001', NULL, 2, '小康师傅', 1, 4, 1, 1, 'wxpay', 0,
        0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '2', '2023-03-25 08:55:12',
        '2023-03-25 09:00:13'),
       (1639551769091940353, 'A1000001804987986430800', NULL, 'A1000001', NULL, 7, '一百橙汁', 1, 4, 1, 1, 'wxpay', 0,
        0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-03-25 08:56:18',
        '2023-03-25 09:01:19'),
       (1639570465608884226, 'A1000001809445445129300', NULL, 'A1000001', NULL, 2, '小康师傅', 1, 4, 1, 1, 'wxpay', 0,
        0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-03-25 10:10:35',
        '2023-03-25 10:11:05'),
       (1640253438704431106, 'A1000001972287294582300', NULL, 'A1000001', NULL, 1, '可口可乐', 1, 2, 1, 1, 'wxpay', 1,
        0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-03-27 07:24:29',
        '2023-03-27 07:24:52'),
       (1640291223389851650, 'A1000001981295877562800', NULL, 'A1000001', NULL, 1, '可口可乐', 1, 4, 1, 1, 'wxpay', 0,
        0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-03-27 09:54:37',
        '2023-03-27 09:59:39'),
       (1640291496925581313, 'A1000001981361996771600', NULL, 'A1000001', NULL, 1, '可口可乐', 1, 4, 1, 1, 'wxpay', 0,
        0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-03-27 09:55:43',
        '2023-03-27 10:00:44'),
       (1640292440643940353, 'A1000001981586907029400', NULL, 'A1000001', NULL, 1, '可口可乐', 1, 4, 1, 1, 'alipay', 0,
        0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-03-27 09:59:28',
        '2023-03-27 10:04:29'),
       (1640294278558597121, 'A1000001982025093546600', NULL, 'A1000001', NULL, 1, '可口可乐', 1, 1, 1, 1, 'wxpay', 1,
        0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-03-27 10:06:46',
        '2023-03-27 10:06:59'),
       (1640295466658119682, 'A1000001982308461482000', NULL, 'A1000001', NULL, 1, '可口可乐', 1, 3, 1, 1, 'wxpay', 3,
        0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-03-27 10:11:29',
        '2023-03-27 10:11:56'),
       (1665192943211196418, 'A10000011867384516930600', NULL, 'A1000001', NULL, 2, '小康师傅', 1, 1, 1, 1, 'wxpay', 1,
        0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-06-04 03:05:10',
        '2023-06-04 03:05:30'),
       (1665193128339386370, 'A10000011867429590093200', NULL, 'A1000001', NULL, 3, '统一奶茶', 1, 1, 1, 1, 'alipay', 1,
        0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-06-04 03:05:54',
        '2023-06-04 03:06:07'),
       (1665193628363337730, 'A10000011867548883835400', NULL, 'A1000001', NULL, 1, '可口可乐', 1, 1, 1, 1, 'wxpay', 1,
        0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-06-04 03:07:53',
        '2023-06-04 03:11:41'),
       (1665197835275415554, 'A10000011868551878012500', NULL, 'A1000001', NULL, 2, '小康师傅', 1, 1, 1, 1, 'wxpay', 1,
        0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, 'oJ9WJ5MhIS-hiwuUX0GmsHDzqTyQ', 1, '龙门石窟', '',
        '2023-06-04 03:24:36', '2023-06-04 03:24:58'),
       (1665199941424197633, 'A10000011869054027266500', NULL, 'A1000001', NULL, 2, '小康师傅', 1, 1, 1, 1, 'wxpay', 1,
        0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, 'oJ9WJ5MhIS-hiwuUX0GmsHDzqTyQ', 1, '龙门石窟', '',
        '2023-06-04 03:32:59', '2023-06-04 03:33:54'),
       (1665294465416785921, 'A10000011891589370978600', NULL, 'A1000001', NULL, 9, '新星巴克', 1, 2, 1, 1, 'wxpay', 1,
        0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-06-04 09:48:35',
        '2023-06-04 09:51:34'),
       (1665296000083259393, 'A10000011891956192562900', NULL, 'A1000001', NULL, 9, '新星巴克', 1, 3, 1, 1, 'wxpay', 3,
        0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-06-04 09:54:41',
        '2023-06-04 09:55:42'),
       (1699412789128679425, 'A100000132363273473600', NULL, 'A1000001', NULL, 3, '统一奶茶', 1, 0, 1, 1, 'wxpay', 0, 0,
        NULL, 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-09-06 13:22:37', '2023-09-06 13:22:37'),
       (1699413852900573185, 'A100000132616927206000', NULL, 'A1000001', '1-8', 3, '统一奶茶', 1, 1, 1, 1, 'wxpay', 1,
        0, NULL, 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-09-06 13:26:51', '2023-09-06 13:27:51'),
       (1699609497649393665, 'A100000179261600800100', NULL, 'A1000001', '1-3', 2, '小康师傅', NULL, 1, 1, 1, 'wxpay',
        1, 0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-09-07 02:24:16',
        '2023-09-07 02:25:17'),
       (1699665320891179009, 'A100000192570825504800', NULL, 'A1000001', '1-5', 3, '统一奶茶', NULL, 0, 1, 1, 'alipay',
        0, 0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-09-07 06:06:05',
        '2023-09-07 06:06:05'),
       (1699666273908350978, 'A100000192798155831900', NULL, 'A1000001', '1-3', 2, '小康师傅', NULL, 2, 1, 1, 'alipay',
        1, 0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-09-07 06:09:53',
        '2023-09-07 06:12:18'),
       (1699667483335909378, 'A100000193086524012200', NULL, 'A1000001', '1-1', 1, '可口可乐', NULL, 2, 1, 1, 'alipay',
        1, 0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-09-10 06:14:41',
        '2023-09-07 06:15:51'),
       (1700104081533829121, 'A1000001197178608837400', NULL, 'A1000001', '1-5', 3, '统一奶茶', NULL, 2, 1, 1, 'wxpay',
        1, 0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-09-10 11:09:34',
        '2023-09-08 11:10:36'),
       (1700104233585737730, 'A1000001197215723001700', NULL, 'A1000001', '1-1', 1, '可口可乐', NULL, 2, 1, 1, 'wxpay',
        1, 0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-09-10 11:10:10',
        '2023-09-08 11:11:11'),
       (1702608867975180289, 'A1000001794398419433700', NULL, 'A1000001', '2-2', 6, '怡宝至尊', NULL, 2, 3, 1, 'wxpay',
        0, 0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-09-15 09:02:42',
        '2023-09-15 09:02:42'),
       (1702614203809349633, 'A1000001795671154933400', NULL, 'A1000001', '1-3', 2, '小康师傅', NULL, 0, 1, 1, 'alipay',
        0, 0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-09-15 09:23:54',
        '2023-09-15 09:23:54'),
       (1702615729550376962, 'A1000001796034964829100', NULL, 'A1000001', '2-3', 8, '魔力花茶', NULL, 4, 1, 1, 'alipay',
        0, 0, '河南省-洛阳市-龙门石窟', 1, NULL, 1, 1, '', 1, '龙门石窟', '', '2023-09-15 09:29:58',
        '2023-09-15 09:30:58');
/*!40000 ALTER TABLE `tb_order`
    ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tb_role`
--

DROP TABLE IF EXISTS `tb_role`;
/*!40101 SET @saved_cs_client = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_role`
(
    `role_id`   int NOT NULL AUTO_INCREMENT,
    `role_code` varchar(50) DEFAULT NULL COMMENT '角色编码\n',
    `role_name` varchar(50) DEFAULT NULL COMMENT '角色名称\n',
    PRIMARY KEY (`role_id`),
    UNIQUE KEY `tb_role_role_code_uindex` (`role_code`),
    UNIQUE KEY `tb_role_role_name_uindex` (`role_name`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 5
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='工单角色表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tb_role`
--

LOCK TABLES `tb_role` WRITE;
/*!40000 ALTER TABLE `tb_role`
    DISABLE KEYS */;
INSERT INTO `tb_role`
VALUES (1, '1001', '工单管理员'),
       (2, '1002', '运营员'),
       (3, '1003', '维修员');
/*!40000 ALTER TABLE `tb_role`
    ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tb_task_type`
--

DROP TABLE IF EXISTS `tb_task_type`;
/*!40101 SET @saved_cs_client = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_task_type`
(
    `type_id`   int NOT NULL,
    `type_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '类型名称',
    `type`      int                                                          DEFAULT '1' COMMENT '工单类型。1:维修工单;2:运营工单',
    PRIMARY KEY (`type_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='工单类型';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tb_task_type`
--

LOCK TABLES `tb_task_type` WRITE;
/*!40000 ALTER TABLE `tb_task_type`
    DISABLE KEYS */;
INSERT INTO `tb_task_type`
VALUES (1, '投放工单', 1),
       (2, '补货工单', 2),
       (3, '维修工单', 1),
       (4, '撤机工单', 1);
/*!40000 ALTER TABLE `tb_task_type`
    ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tb_emp`
--

DROP TABLE IF EXISTS `tb_emp`;
/*!40101 SET @saved_cs_client = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tb_emp`
(
    `id`          int         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_name`   varchar(50) NOT NULL COMMENT '员工名称',
    `region_id`   int              DEFAULT NULL COMMENT '所属区域Id',
    `region_name` varchar(50)      DEFAULT NULL COMMENT '区域名称',
    `role_id`     int              DEFAULT NULL COMMENT '角色id',
    `role_code`   varchar(10)      DEFAULT NULL COMMENT '角色编号',
    `role_name`   varchar(50)      DEFAULT NULL COMMENT '角色名称',
    `mobile`      varchar(15)      DEFAULT NULL COMMENT '联系电话',
    `image`       varchar(500)     DEFAULT NULL COMMENT '员工头像',
    `status`      tinyint          DEFAULT '1' COMMENT '是否启用',
    `create_time` timestamp   NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` timestamp   NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `tb_user_Id_uindex` (`id`),
    UNIQUE KEY `tb_user_user_name_uindex` (`user_name`),
    UNIQUE KEY `tb_user_mobile_uindex` (`mobile`),
    KEY `role_id` (`role_id`),
    KEY `region_id` (`region_id`),
    CONSTRAINT `tb_emp_ibfk_1` FOREIGN KEY (`role_id`) REFERENCES `tb_role` (`role_id`),
    CONSTRAINT `tb_emp_ibfk_2` FOREIGN KEY (`region_id`) REFERENCES `tb_region` (`id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 55
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='工单员工表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tb_emp`
--

LOCK TABLES `tb_emp` WRITE;
/*!40000 ALTER TABLE `tb_emp`
    DISABLE KEYS */;
INSERT INTO `tb_emp`
VALUES (2, '刘备', 2, '北京市海淀区', 2, '1002', '运营员', '13800000001',
        'http://likede2-java.itheima.net/image/avatar.png', 1, '2024-06-10 07:06:58', '2024-06-10 08:11:04'),
       (3, '关羽', 2, '北京市海淀区', 2, '1002', '运营员', '13800000002',
        'http://likede2-java.itheima.net/image/avatar.png', 1, '2024-06-10 07:06:58', '2024-06-10 08:11:06'),
       (4, '张飞', 2, '北京市海淀区', 3, '1003', '维修员', '13800000003',
        'http://likede2-java.itheima.net/image/avatar.png', 1, '2024-06-10 07:06:58', '2024-06-10 08:11:08'),
       (5, '赵云', 2, '北京市海淀区', 3, '1003', '维修员', '13800000004',
        'http://likede2-java.itheima.net/image/avatar.png', 1, '2024-06-10 07:06:58', '2024-06-10 08:11:10'),
       (6, '曹操', 1, '北京市朝阳区', 2, '1002', '运营员', '13900139001',
        'http://likede2-java.itheima.net/image/avatar.png', 1, '2024-06-10 07:06:58', '2024-06-10 08:11:11'),
       (7, '夏侯惇', 1, '北京市朝阳区', 2, '1002', '运营员', '13900000002',
        'http://likede2-java.itheima.net/image/avatar.png', 1, '2024-06-10 07:06:58', '2024-06-10 08:11:13'),
       (8, '许褚', 1, '北京市朝阳区', 3, '1003', '维修员', '13900139003',
        'http://likede2-java.itheima.net/image/avatar.png', 1, '2024-06-10 07:06:58', '2024-06-10 08:11:15'),
       (9, '张辽', 1, '北京市朝阳区', 3, '1003', '维修员', '13900000004',
        'http://likede2-java.itheima.net/image/avatar.png', 1, '2024-06-10 07:06:58', '2024-06-10 08:11:17'),
       (10, '孙权', 3, '北京市西城区', 2, '1002', '运营员', '13700000001',
        'http://likede2-java.itheima.net/image/avatar.png', 1, '2024-06-10 07:06:58', '2024-06-10 08:11:19'),
       (11, '周瑜', 3, '北京市西城区', 2, '1002', '运营员', '13700000002',
        'http://likede2-java.itheima.net/image/avatar.png', 0, '2024-06-10 07:06:58', '2024-06-10 08:11:21'),
       (12, '吕蒙', 3, '北京市西城区', 3, '1003', '维修员', '13700000003',
        'http://likede2-java.itheima.net/image/avatar.png', 0, '2024-06-10 07:06:58', '2024-06-10 08:11:24'),
       (13, '陆逊', 3, '北京市西城区', 3, '1003', '维修员', '13700000005',
        'http://likede2-java.itheima.net/image/avatar.png', 1, '2024-06-10 07:06:58', '2024-06-10 08:11:26'),
       (50, '孙一百', 1, '北京市朝阳区', 3, '1003', '维修员', '13700137009',
        'http://likede2-java.itheima.net/image/avatar.png', 1, '2024-06-10 07:06:58', '2024-06-10 08:11:28'),
       (51, '马超', 2, '北京市海淀区', 3, '1003', '维修员', '13900002222',
        'http://likede2-java.itheima.net/image/avatar.png', 1, '2024-06-10 07:06:58', '2024-06-10 08:11:30'),
       (52, '黄忠', 2, '北京市海淀区', 2, '1002', '运营员', '13900005555',
        'http://likede2-java.itheima.net/image/avatar.png', 1, '2024-06-10 07:06:58', '2024-06-10 08:11:31'),
       (53, '测试员工', 1, '北京市朝阳区', 1, '1001', '工单管理员', '15100000001',
        '/profile/upload/2024/05/18/4e7f3a15429bfda99bce42a18cdd1jpeg_20240518103539A002.jpeg', 1,
        '2024-06-10 07:06:58', '2024-06-10 08:11:33');
/*!40000 ALTER TABLE `tb_emp`
    ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE = @OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE = @OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS = @OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT = @OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS = @OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION = @OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES = @OLD_SQL_NOTES */;

-- Dump completed on 2024-06-11 20:41:57


DROP TABLE IF EXISTS `tb_model_history`;
CREATE TABLE tb_model_history
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    session_id   VARCHAR(128)               NOT NULL COMMENT '会话唯一标识',
    user_id      VARCHAR(64)                NOT NULL COMMENT '用户唯一标识',
    user_name    VARCHAR(128)               NOT NULL COMMENT '用户名字',
    message_type ENUM ('user', 'assistant') NOT NULL DEFAULT 'user' COMMENT '消息类型',
    status       VARCHAR(20)                NOT NULL DEFAULT 'SUCCEEDED'
        COMMENT '消息处理状态：ACCEPTED/STREAMING/SUCCEEDED/FAILED/CANCELLED/REJECTED',
    error_code   VARCHAR(64)                         DEFAULT NULL COMMENT '稳定错误码（与 Java/Agent 契约一致）',
    request_id   VARCHAR(64)                         DEFAULT NULL COMMENT '请求标识（request_id，跨 Java/Agent 链路）',
    content      TEXT                       NOT NULL COMMENT '消息内容',
    ui_json      TEXT                               DEFAULT NULL COMMENT 'OpenUI 渲染历史（JSON：{"renders":[...]}，仅助手成功消息可能携带）',
    model_name   VARCHAR(64)                         DEFAULT NULL COMMENT '使用的模型名称',
    tokens       INT UNSIGNED                        DEFAULT 0 COMMENT '消耗的token数量',
    created_at   DATETIME                            DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    started_at   DATETIME                            DEFAULT NULL COMMENT '开始处理时间（用户消息受理写入）',
    updated_at   DATETIME                            DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    completed_at DATETIME                            DEFAULT NULL COMMENT '消息完成时间（SUCCEEDED/FAILED/CANCELLED 写入）',

    INDEX idx_session_id (session_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_request_id (request_id),
    INDEX idx_created_at (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 创建聊天会话表
DROP TABLE IF EXISTS `tb_chat_session`;
CREATE TABLE tb_chat_session
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    session_id   VARCHAR(128) NOT NULL UNIQUE COMMENT '会话唯一标识',
    user_id      VARCHAR(64)  NOT NULL COMMENT '用户唯一标识',
    session_name VARCHAR(128) NOT NULL COMMENT '会话名称',
    status       VARCHAR(16)  DEFAULT 'ACTIVE' COMMENT '会话状态：ACTIVE 正常/DELETING 删除中（拒绝新消息）',
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='聊天会话表';


-- ---------------------------------------------------------------------------
-- Bring legacy dump tables in line with fields used by the current mappers
-- ---------------------------------------------------------------------------

ALTER TABLE `tb_emp`
  ADD COLUMN `password` varchar(255) NOT NULL DEFAULT '' COMMENT '登录密码' AFTER `status`,
  ADD COLUMN `user_id` bigint DEFAULT NULL COMMENT '系统用户ID' AFTER `password`,
  ADD KEY `idx_emp_user_id` (`user_id`);

ALTER TABLE `tb_order`
  ADD COLUMN `quantity` int NOT NULL DEFAULT 1 COMMENT '购买数量' AFTER `cancel_desc`,
  ADD COLUMN `channel_id` bigint DEFAULT NULL COMMENT '货道ID' AFTER `quantity`,
  ADD KEY `idx_tb_order_channel_id` (`channel_id`);

-- Bind one employee to the built-in administrator for app-side test queries.
UPDATE `tb_emp` SET `user_id` = 1 WHERE `id` = 2;


-- ---------------------------------------------------------------------------
-- Clean business menu tree (replaces legacy menu migrations with hard-coded,
-- missing parent IDs such as 2019/2026/2047/2067/2148)
-- ---------------------------------------------------------------------------
INSERT INTO `sys_menu`
(`menu_id`,`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`)
VALUES
  (2000, '灵犀业务', 0, 1, 'lingxi', NULL, '', 1, 0, 'M', '0', '0', '', 'dashboard', 'admin', NOW(), '', NULL, '灵犀业务根菜单'),
  (2001, '区域管理', 2000, 1, 'region', 'manage/region/index', '', 1, 0, 'C', '0', '0', 'manage:region:list', '#', 'admin', NOW(), '', NULL, ''),
  (2002, '合作商管理', 2000, 2, 'partner', 'manage/partner/index', '', 1, 0, 'C', '0', '0', 'manage:partner:list', '#', 'admin', NOW(), '', NULL, ''),
  (2003, '点位管理', 2000, 3, 'node', 'manage/node/index', '', 1, 0, 'C', '0', '0', 'manage:node:list', '#', 'admin', NOW(), '', NULL, ''),
  (2004, '设备类型', 2000, 4, 'vmType', 'manage/vmType/index', '', 1, 0, 'C', '0', '0', 'manage:vmType:list', '#', 'admin', NOW(), '', NULL, ''),
  (2005, '设备管理', 2000, 5, 'vm', 'manage/vm/index', '', 1, 0, 'C', '0', '0', 'manage:vm:list', '#', 'admin', NOW(), '', NULL, ''),
  (2006, '商品类型', 2000, 6, 'skuClass', 'manage/skuClass/index', '', 1, 0, 'C', '0', '0', 'manage:skuClass:list', '#', 'admin', NOW(), '', NULL, ''),
  (2007, '商品管理', 2000, 7, 'sku', 'manage/sku/index', '', 1, 0, 'C', '0', '0', 'manage:sku:list', '#', 'admin', NOW(), '', NULL, ''),
  (2008, '策略管理', 2000, 8, 'policy', 'manage/policy/index', '', 1, 0, 'C', '0', '0', 'manage:policy:list', '#', 'admin', NOW(), '', NULL, ''),
  (2009, '订单管理', 2000, 9, 'order', 'manage/order/index', '', 1, 0, 'C', '0', '0', 'manage:order:list', '#', 'admin', NOW(), '', NULL, ''),
  (2010, '人员管理', 2000, 10, 'emp', 'manage/emp/index', '', 1, 0, 'C', '0', '0', 'manage:emp:list', '#', 'admin', NOW(), '', NULL, ''),
  (2011, '工单管理', 2000, 11, 'task', NULL, '', 1, 0, 'M', '0', '0', '', 'form', 'admin', NOW(), '', NULL, ''),
  (2012, '运营工单', 2011, 1, 'business', 'manage/task/business', '', 1, 0, 'C', '0', '0', 'manage:task:list', '#', 'admin', NOW(), '', NULL, ''),
  (2013, '运维工单', 2011, 2, 'operation', 'manage/task/operation', '', 1, 0, 'C', '0', '0', 'manage:task:list', '#', 'admin', NOW(), '', NULL, ''),
  (2015, '灵犀智能助手', 2000, 12, 'ai', 'ai/qwen-chat', '', 1, 0, 'C', '0', '0', 'ai:chat:list', 'message', 'admin', NOW(), '', NULL, '');

-- Common button permissions used by v-hasPermi. They do not appear as routes.
INSERT INTO `sys_menu`
(`menu_name`,`parent_id`,`order_num`,`path`,`component`,`query`,`is_frame`,`is_cache`,`menu_type`,`visible`,`status`,`perms`,`icon`,`create_by`,`create_time`,`update_by`,`update_time`,`remark`)
SELECT CONCAT(m.menu_name, p.action_name), m.menu_id, p.order_num, '#', '', '', 1, 0, 'F', '0', '0',
       CONCAT(SUBSTRING_INDEX(m.perms, ':', 2), ':', p.action_code), '#', 'admin', NOW(), '', NULL, ''
FROM `sys_menu` m
JOIN (
  SELECT 1 order_num, '查询' action_name, 'query' action_code
  UNION ALL SELECT 2, '新增', 'add'
  UNION ALL SELECT 3, '修改', 'edit'
  UNION ALL SELECT 4, '删除', 'remove'
  UNION ALL SELECT 5, '导出', 'export'
) p
WHERE m.menu_id BETWEEN 2001 AND 2015
  AND m.menu_type = 'C'
  AND m.perms <> '';

CREATE TABLE IF NOT EXISTS tb_order_detail (
                                               id BIGINT NOT NULL AUTO_INCREMENT,
                                               order_id BIGINT NOT NULL,
                                               channel_id BIGINT NOT NULL,
                                               sku_id BIGINT NOT NULL,
                                               sku_name VARCHAR(255),
                                               quantity INT NOT NULL,
                                               price BIGINT NOT NULL,
                                               amount BIGINT NOT NULL,
                                               PRIMARY KEY (id),
                                               KEY idx_order_detail_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;

-- Initialization summary
SELECT 'dkd database initialized successfully' AS message;

-- ============================================================
-- AI controlled write actions (from ai_agent_action_migration.sql)
-- ============================================================

-- 阶段 5：AI 受控写操作持久化与工单幂等迁移
-- 本迁移默认不会启用写操作，仍需显式设置
-- AGENT_WRITE_ACTIONS_ENABLED=true 才会开放提案与执行链路。

CREATE TABLE IF NOT EXISTS `ai_agent_action` (
  `action_id` varchar(64) NOT NULL COMMENT '受控动作ID',
  `idempotency_key` varchar(128) NOT NULL COMMENT 'Agent 单次工具调用幂等键',
  `action_type` varchar(64) NOT NULL COMMENT '动作类型',
  `user_id` varchar(64) NOT NULL COMMENT '提案所属登录用户',
  `thread_id` varchar(128) NOT NULL COMMENT '提案所属聊天会话',
  `region_id` bigint DEFAULT NULL COMMENT '动作目标区域',
  `inner_code` varchar(64) NOT NULL COMMENT '目标设备编号',
  `action_desc` varchar(500) NOT NULL COMMENT '拟创建工单描述',
  `status` varchar(20) NOT NULL COMMENT 'PENDING/APPROVED/REJECTED/SUCCEEDED/FAILED/EXPIRED',
  `created_at` datetime NOT NULL,
  `expires_at` datetime NOT NULL,
  `decided_at` datetime DEFAULT NULL,
  `decided_by` bigint DEFAULT NULL COMMENT '批准或拒绝的登录用户ID',
  `executed_at` datetime DEFAULT NULL,
  `task_id` bigint DEFAULT NULL,
  `task_code` varchar(64) DEFAULT NULL,
  `last_error_code` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`action_id`),
  UNIQUE KEY `uk_ai_agent_action_idempotency` (`user_id`, `thread_id`, `idempotency_key`),
  KEY `idx_ai_agent_action_status_expiry` (`status`, `expires_at`),
  KEY `idx_ai_agent_action_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 人工确认受控动作';

ALTER TABLE `tb_task`
  ADD COLUMN `agent_action_id` varchar(64) DEFAULT NULL COMMENT 'AI 受控动作ID' AFTER `addr`,
  ADD UNIQUE KEY `uk_tb_task_agent_action_id` (`agent_action_id`);

-- ============================================================
-- AI video workflow schema (from ai_video_workflow.sql)
-- ============================================================

-- AI 小说视频自动化工作流模块（MySQL 8.0 / utf8mb4）
-- 逻辑关联使用索引，未使用数据库外键，便于任务重试、归档和软删除。

CREATE TABLE IF NOT EXISTS ai_video_project (
  project_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目ID',
  project_name VARCHAR(128) NOT NULL COMMENT '项目名称',
  source_type VARCHAR(32) NOT NULL DEFAULT 'NOVEL' COMMENT '来源:NOVEL/ORIGINAL_SCRIPT',
  adaptation_mode VARCHAR(32) NOT NULL DEFAULT 'FAITHFUL' COMMENT '改编模式:FAITHFUL/ADAPTIVE',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT/ACTIVE/PAUSED/ARCHIVED',
  owner_user_id BIGINT NOT NULL COMMENT '所属用户ID',
  cover_url VARCHAR(1024) DEFAULT NULL COMMENT '项目封面图片URL',
  visual_style VARCHAR(255) DEFAULT NULL COMMENT '视觉风格摘要',
  style_guide_json JSON DEFAULT NULL COMMENT '全局风格、色板、摄影规则',
  default_aspect_ratio VARCHAR(16) NOT NULL DEFAULT '16:9' COMMENT '默认画幅',
  default_language VARCHAR(16) NOT NULL DEFAULT 'zh-CN' COMMENT '默认语言',
  storage_bytes BIGINT NOT NULL DEFAULT 0 COMMENT '已占用存储字节',
  estimated_cost DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '预估成本',
  actual_cost DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '实际成本',
  create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志:0存在 2删除',
  PRIMARY KEY (project_id),
  KEY idx_project_owner_status (owner_user_id, status),
  KEY idx_project_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI视频项目';

CREATE TABLE IF NOT EXISTS ai_video_chapter (
  chapter_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '章节ID',
  project_id BIGINT NOT NULL COMMENT '项目ID',
  chapter_no INT NOT NULL COMMENT '章节序号',
  chapter_title VARCHAR(255) DEFAULT NULL COMMENT '章节标题',
  source_text LONGTEXT NOT NULL COMMENT '原始小说文本',
  source_hash CHAR(64) NOT NULL COMMENT '原文SHA-256',
  word_count INT NOT NULL DEFAULT 0 COMMENT '字数',
  summary_text TEXT DEFAULT NULL COMMENT '章节摘要',
  parse_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCEEDED/FAILED',
  pipeline_status VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED' COMMENT '章节流水线状态',
  current_bible_version INT NOT NULL DEFAULT 0 COMMENT '当前故事圣经版本',
  source_metadata_json JSON DEFAULT NULL COMMENT '来源文件、页码等元数据',
  create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NULL DEFAULT '0',
  PRIMARY KEY (chapter_id),
  UNIQUE KEY uk_chapter_project_no (project_id, chapter_no, del_flag),
  KEY idx_chapter_project_status (project_id, pipeline_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI视频章节';

CREATE TABLE IF NOT EXISTS ai_video_story_bible (
  bible_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '故事圣经ID',
  project_id BIGINT NOT NULL COMMENT '项目ID',
  chapter_id BIGINT DEFAULT NULL COMMENT '产生该版本的章节ID',
  version_no INT NOT NULL COMMENT '版本号',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVED/DEPRECATED',
  world_setting TEXT DEFAULT NULL COMMENT '世界观',
  timeline_json JSON DEFAULT NULL COMMENT '事件时间线',
  relationship_json JSON DEFAULT NULL COMMENT '人物关系',
  immutable_facts_json JSON DEFAULT NULL COMMENT '不可修改事实',
  content_json JSON NOT NULL COMMENT '完整结构化故事圣经',
  source_reference_json JSON DEFAULT NULL COMMENT '原文引用范围',
  model_name VARCHAR(128) DEFAULT NULL COMMENT '生成模型',
  prompt_version VARCHAR(64) DEFAULT NULL COMMENT '提示词模板版本',
  create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (bible_id),
  UNIQUE KEY uk_bible_chapter_version (chapter_id, version_no, del_flag),
  KEY idx_bible_chapter (chapter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='版本化故事圣经';

CREATE TABLE IF NOT EXISTS ai_video_character (
  character_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '人物ID',
  project_id BIGINT NOT NULL COMMENT '项目ID',
  character_code VARCHAR(64) NOT NULL COMMENT '项目内人物编码',
  character_name VARCHAR(128) NOT NULL COMMENT '人物名称',
  aliases_json JSON DEFAULT NULL COMMENT '别名',
  gender VARCHAR(32) DEFAULT NULL COMMENT '性别',
  age_range VARCHAR(64) DEFAULT NULL COMMENT '年龄范围',
  personality_json JSON DEFAULT NULL COMMENT '性格标签',
  appearance_text TEXT DEFAULT NULL COMMENT '外观描述',
  speaking_style TEXT DEFAULT NULL COMMENT '说话风格',
  visual_prompt_base TEXT DEFAULT NULL COMMENT '固定视觉提示词',
  voice_profile_json JSON DEFAULT NULL COMMENT '音色和语速规则',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED',
  create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (character_id),
  UNIQUE KEY uk_character_project_code (project_id, character_code, del_flag),
  KEY idx_character_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI视频人物档案';

CREATE TABLE IF NOT EXISTS ai_video_character_state (
  state_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '状态ID',
  project_id BIGINT NOT NULL COMMENT '项目ID',
  character_id BIGINT NOT NULL COMMENT '人物ID',
  chapter_id BIGINT NOT NULL COMMENT '章节ID',
  scene_id BIGINT DEFAULT NULL COMMENT '场景ID，为空表示章节状态',
  state_order INT NOT NULL DEFAULT 0 COMMENT '同章节顺序',
  outfit_asset_id BIGINT DEFAULT NULL COMMENT '服装资产ID',
  location_asset_id BIGINT DEFAULT NULL COMMENT '所在场景资产ID',
  prop_asset_ids JSON DEFAULT NULL COMMENT '携带道具资产ID列表',
  injury_text VARCHAR(1000) DEFAULT NULL COMMENT '伤势/妆容连续性',
  emotional_baseline VARCHAR(255) DEFAULT NULL COMMENT '情绪基线',
  state_json JSON DEFAULT NULL COMMENT '扩展状态',
  create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (state_id),
  KEY idx_character_state_lookup (character_id, chapter_id, state_order),
  KEY idx_state_project_chapter (project_id, chapter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人物连续性状态';

CREATE TABLE IF NOT EXISTS ai_video_scene (
  scene_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '场景ID',
  project_id BIGINT NOT NULL COMMENT '项目ID',
  chapter_id BIGINT NOT NULL COMMENT '章节ID',
  scene_no INT NOT NULL COMMENT '章节内场景序号',
  scene_title VARCHAR(255) DEFAULT NULL COMMENT '场景标题',
  source_paragraph_from INT DEFAULT NULL COMMENT '原文起始段落',
  source_paragraph_to INT DEFAULT NULL COMMENT '原文结束段落',
  source_excerpt TEXT DEFAULT NULL COMMENT '原文依据',
  time_description VARCHAR(255) DEFAULT NULL COMMENT '时间',
  location_description VARCHAR(500) DEFAULT NULL COMMENT '地点',
  atmosphere VARCHAR(500) DEFAULT NULL COMMENT '氛围',
  dramatic_goal TEXT DEFAULT NULL COMMENT '戏剧目标',
  conflict_text TEXT DEFAULT NULL COMMENT '冲突',
  character_ids JSON DEFAULT NULL COMMENT '参与人物ID列表',
  scene_package_json JSON DEFAULT NULL COMMENT '场景生产包',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVED/DEPRECATED',
  version_no INT NOT NULL DEFAULT 1 COMMENT '版本号',
  create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (scene_id),
  UNIQUE KEY uk_scene_chapter_no_version (chapter_id, scene_no, version_no, del_flag),
  KEY idx_scene_project_chapter (project_id, chapter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI视频场景';

CREATE TABLE IF NOT EXISTS ai_video_shot (
  shot_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '镜头ID',
  project_id BIGINT NOT NULL COMMENT '项目ID',
  chapter_id BIGINT NOT NULL COMMENT '章节ID',
  scene_id BIGINT NOT NULL COMMENT '场景ID',
  shot_no INT NOT NULL COMMENT '场景内镜头序号',
  duration_ms INT NOT NULL DEFAULT 4000 COMMENT '目标时长毫秒',
  shot_size VARCHAR(64) DEFAULT NULL COMMENT '景别',
  camera_angle VARCHAR(64) DEFAULT NULL COMMENT '机位角度',
  camera_movement VARCHAR(64) DEFAULT NULL COMMENT '运镜',
  composition_text TEXT DEFAULT NULL COMMENT '构图',
  action_text TEXT DEFAULT NULL COMMENT '动作',
  emotion_text VARCHAR(500) DEFAULT NULL COMMENT '情绪',
  dialogue_json JSON DEFAULT NULL COMMENT '对白序列',
  required_asset_ids JSON DEFAULT NULL COMMENT '所需资产ID',
  prompt_context_json JSON DEFAULT NULL COMMENT '提示词上下文',
  source_paragraph_from INT DEFAULT NULL COMMENT '原文起始段落',
  source_paragraph_to INT DEFAULT NULL COMMENT '原文结束段落',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVED/GENERATING/COMPLETED/DEPRECATED',
  version_no INT NOT NULL DEFAULT 1 COMMENT '版本号',
  create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (shot_id),
  UNIQUE KEY uk_shot_scene_no_version (scene_id, shot_no, version_no, del_flag),
  KEY idx_shot_chapter_scene (chapter_id, scene_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI视频分镜';

CREATE TABLE IF NOT EXISTS ai_video_asset (
  asset_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '资产ID',
  project_id BIGINT NOT NULL COMMENT '项目ID',
  chapter_id BIGINT DEFAULT NULL COMMENT '章节ID',
  scene_id BIGINT DEFAULT NULL COMMENT '场景ID',
  shot_id BIGINT DEFAULT NULL COMMENT '镜头ID',
  character_id BIGINT DEFAULT NULL COMMENT '人物ID',
  asset_code VARCHAR(96) NOT NULL COMMENT '项目内资产编码',
  asset_name VARCHAR(255) NOT NULL COMMENT '资产名称',
  asset_type VARCHAR(64) NOT NULL COMMENT '资产类型',
  asset_scope VARCHAR(32) NOT NULL DEFAULT 'PROJECT' COMMENT 'PROJECT/CHAPTER/SCENE/SHOT/CHARACTER',
  canonical_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否规范资产',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/GENERATING/GENERATED/VALIDATING/APPROVED/REJECTED/DEPRECATED/ARCHIVED',
  version_no INT NOT NULL DEFAULT 1 COMMENT '版本号',
  source_asset_id BIGINT DEFAULT NULL COMMENT '直接来源资产',
  storage_provider VARCHAR(32) DEFAULT NULL COMMENT 'OSS/MINIO/S3',
  object_key VARCHAR(1024) DEFAULT NULL COMMENT '对象存储键',
  preview_object_key VARCHAR(1024) DEFAULT NULL COMMENT '预览对象键',
  mime_type VARCHAR(128) DEFAULT NULL COMMENT '文件类型',
  file_size BIGINT DEFAULT NULL COMMENT '文件字节数',
  content_hash CHAR(64) DEFAULT NULL COMMENT '文件SHA-256',
  width INT DEFAULT NULL COMMENT '宽度',
  height INT DEFAULT NULL COMMENT '高度',
  duration_ms INT DEFAULT NULL COMMENT '音视频时长',
  prompt_text LONGTEXT DEFAULT NULL COMMENT '正向提示词',
  negative_prompt_text TEXT DEFAULT NULL COMMENT '负向提示词',
  generation_params_json JSON DEFAULT NULL COMMENT '模型参数、seed等',
  metadata_json JSON DEFAULT NULL COMMENT '标签、授权、扩展元数据',
  approved_by VARCHAR(64) DEFAULT NULL COMMENT '批准者',
  approved_time DATETIME DEFAULT NULL COMMENT '批准时间',
  create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (asset_id),
  UNIQUE KEY uk_asset_project_code_version (project_id, asset_code, version_no, del_flag),
  KEY idx_asset_project_type_status (project_id, asset_type, status),
  KEY idx_asset_scene_shot (scene_id, shot_id),
  KEY idx_asset_character (character_id),
  KEY idx_asset_hash (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI视频统一资产';

CREATE TABLE IF NOT EXISTS ai_video_asset_relation (
  relation_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '关系ID',
  project_id BIGINT NOT NULL COMMENT '项目ID',
  from_asset_id BIGINT NOT NULL COMMENT '来源资产ID',
  to_asset_id BIGINT NOT NULL COMMENT '目标资产ID',
  relation_type VARCHAR(32) NOT NULL COMMENT 'DERIVED_FROM/USES/REPLACES/REFERENCE_IMAGE',
  relation_order INT NOT NULL DEFAULT 0 COMMENT '引用顺序',
  metadata_json JSON DEFAULT NULL COMMENT '裁剪、权重、引用说明',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (relation_id),
  UNIQUE KEY uk_asset_relation (from_asset_id, to_asset_id, relation_type),
  KEY idx_asset_relation_to (to_asset_id),
  KEY idx_asset_relation_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI视频资产血缘关系';

CREATE TABLE IF NOT EXISTS ai_video_provider_model (
  model_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模型配置ID',
  provider_code VARCHAR(64) NOT NULL COMMENT '供应商编码',
  model_code VARCHAR(128) NOT NULL COMMENT '模型编码',
  model_name VARCHAR(255) NOT NULL COMMENT '模型名称',
  model_type VARCHAR(32) NOT NULL COMMENT 'LLM/IMAGE/VIDEO/VOICE/SAFETY',
  enabled_flag CHAR(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  capability_json JSON NOT NULL COMMENT '支持图生视频、参考图数量等能力',
  pricing_json JSON DEFAULT NULL COMMENT '成本规则',
  config_json JSON DEFAULT NULL COMMENT '非敏感配置',
  secret_ref VARCHAR(255) DEFAULT NULL COMMENT '密钥引用，不存明文',
  callback_config_json JSON DEFAULT NULL COMMENT '回调验签配置',
  create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (model_id),
  UNIQUE KEY uk_provider_model (provider_code, model_code, del_flag),
  KEY idx_model_type_enabled (model_type, enabled_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI视频供应商模型配置';

CREATE TABLE IF NOT EXISTS ai_video_prompt_template (
  template_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '模板ID',
  template_code VARCHAR(96) NOT NULL COMMENT '模板编码',
  template_name VARCHAR(255) NOT NULL COMMENT '模板名称',
  task_type VARCHAR(64) NOT NULL COMMENT 'STORY_BIBLE/SCENE/SHOT/IMAGE/VIDEO/QUALITY',
  version_no INT NOT NULL COMMENT '版本号',
  system_prompt LONGTEXT DEFAULT NULL COMMENT '系统提示词',
  user_prompt_template LONGTEXT NOT NULL COMMENT '用户提示词模板',
  output_schema_json JSON DEFAULT NULL COMMENT '输出JSON Schema',
  enabled_flag CHAR(1) NOT NULL DEFAULT '1',
  create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (template_id),
  UNIQUE KEY uk_prompt_template_version (template_code, version_no, del_flag),
  KEY idx_prompt_task_enabled (task_type, enabled_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI视频提示词模板';

CREATE TABLE IF NOT EXISTS ai_video_generation_task (
  task_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  project_id BIGINT NOT NULL COMMENT '项目ID',
  chapter_id BIGINT DEFAULT NULL COMMENT '章节ID',
  scene_id BIGINT DEFAULT NULL COMMENT '场景ID',
  shot_id BIGINT DEFAULT NULL COMMENT '镜头ID',
  asset_id BIGINT DEFAULT NULL COMMENT '目标资产ID',
  parent_task_id BIGINT DEFAULT NULL COMMENT '父任务ID',
  task_type VARCHAR(64) NOT NULL COMMENT 'PARSE/STORY_BIBLE/ASSET_PLAN/IMAGE/VIDEO/VOICE/QUALITY/RENDER',
  task_name VARCHAR(255) NOT NULL COMMENT '任务名称',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/QUEUED/RUNNING/WAITING_CALLBACK/QUALITY_CHECK/SUCCEEDED/FAILED/RETRYING/NEEDS_REVIEW/CANCELED',
  priority INT NOT NULL DEFAULT 100 COMMENT '优先级，数值小优先',
  idempotency_key VARCHAR(128) NOT NULL COMMENT '幂等键',
  provider_code VARCHAR(64) DEFAULT NULL COMMENT '供应商',
  model_code VARCHAR(128) DEFAULT NULL COMMENT '模型',
  provider_task_id VARCHAR(255) DEFAULT NULL COMMENT '供应商任务ID',
  request_json JSON DEFAULT NULL COMMENT '脱敏后的请求参数',
  response_json JSON DEFAULT NULL COMMENT '供应商原始响应',
  progress TINYINT NOT NULL DEFAULT 0 COMMENT '进度0-100',
  retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
  max_retry INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
  recover_count INT NOT NULL DEFAULT 0 COMMENT '恢复重投递累计次数，达到上限后终止任务',
  next_retry_time DATETIME DEFAULT NULL COMMENT '下次重试时间',
  error_code VARCHAR(128) DEFAULT NULL COMMENT '错误码',
  error_message TEXT DEFAULT NULL COMMENT '错误信息',
  estimated_cost DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '预估成本',
  actual_cost DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '实际成本',
  callback_event_id VARCHAR(255) DEFAULT NULL COMMENT '最后回调事件ID',
  worker_id VARCHAR(64) DEFAULT NULL COMMENT '当前执行者标识（租约持有者）',
  lease_expire DATETIME DEFAULT NULL COMMENT '租约过期时间，过期后可被其他执行者回收',
  started_time DATETIME DEFAULT NULL,
  completed_time DATETIME DEFAULT NULL,
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (task_id),
  UNIQUE KEY uk_task_idempotency (idempotency_key, del_flag),
  KEY idx_task_schedule (status, priority, next_retry_time),
  KEY idx_task_provider (provider_code, provider_task_id),
  KEY idx_task_project_chapter (project_id, chapter_id),
  KEY idx_task_asset (asset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI视频异步生成任务';

CREATE TABLE IF NOT EXISTS ai_video_quality_report (
  report_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '质检报告ID',
  project_id BIGINT NOT NULL COMMENT '项目ID',
  task_id BIGINT DEFAULT NULL COMMENT '任务ID',
  asset_id BIGINT DEFAULT NULL COMMENT '资产ID',
  check_type VARCHAR(64) NOT NULL COMMENT 'CONTENT/CONSISTENCY/TECHNICAL/STORY',
  checker_code VARCHAR(128) NOT NULL COMMENT '检查器或模型编码',
  status VARCHAR(32) NOT NULL COMMENT 'PASSED/WARNING/FAILED',
  overall_score DECIMAL(5,4) DEFAULT NULL COMMENT '总分0-1',
  metric_json JSON DEFAULT NULL COMMENT '各指标评分',
  issue_json JSON DEFAULT NULL COMMENT '问题列表',
  suggested_action VARCHAR(64) DEFAULT NULL COMMENT 'APPROVE/REGENERATE/NEEDS_REVIEW/BLOCK',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (report_id),
  KEY idx_quality_asset (asset_id, create_time),
  KEY idx_quality_task (task_id),
  KEY idx_quality_project_status (project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI视频质量报告';

CREATE TABLE IF NOT EXISTS ai_video_render (
  render_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '渲染ID',
  project_id BIGINT NOT NULL COMMENT '项目ID',
  chapter_id BIGINT DEFAULT NULL COMMENT '章节ID',
  render_name VARCHAR(255) NOT NULL COMMENT '成片名称',
  render_type VARCHAR(32) NOT NULL COMMENT 'CHAPTER/PROJECT/TRAILER',
  aspect_ratio VARCHAR(16) NOT NULL DEFAULT '16:9',
  resolution VARCHAR(32) DEFAULT NULL COMMENT '分辨率',
  timeline_json JSON NOT NULL COMMENT '镜头、音频、字幕时间线',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/QUEUED/RUNNING/SUCCEEDED/FAILED',
  output_asset_id BIGINT DEFAULT NULL COMMENT '最终视频资产ID',
  task_id BIGINT DEFAULT NULL COMMENT '关联渲染任务',
  version_no INT NOT NULL DEFAULT 1,
  create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (render_id),
  KEY idx_render_project_chapter (project_id, chapter_id),
  KEY idx_render_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI视频成片渲染';

CREATE TABLE IF NOT EXISTS ai_video_task_outbox (
  outbox_id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Outbox ID',
  task_id BIGINT NOT NULL COMMENT '任务ID',
  event_type VARCHAR(64) NOT NULL COMMENT 'TASK_CREATED/TASK_RETRY/ASSET_APPROVED',
  payload_json JSON NOT NULL COMMENT '投递负载',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_time DATETIME DEFAULT NULL,
  sent_time DATETIME DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (outbox_id),
  KEY idx_outbox_dispatch (status, next_retry_time),
  KEY idx_outbox_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI视频任务可靠投递箱';

CREATE TABLE IF NOT EXISTS ai_video_task_attempt (
  attempt_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '尝试ID',
  task_id BIGINT NOT NULL COMMENT '任务ID',
  attempt_no INT NOT NULL COMMENT '尝试序号，从1开始递增',
  status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED' COMMENT 'SUBMITTED/SUCCEEDED/FAILED',
  provider_code VARCHAR(64) DEFAULT NULL COMMENT '供应商',
  model_code VARCHAR(128) DEFAULT NULL COMMENT '模型',
  provider_request_id VARCHAR(255) DEFAULT NULL COMMENT '供应商请求标识（提交幂等键）',
  provider_task_id VARCHAR(255) DEFAULT NULL COMMENT '供应商任务ID（异步查询/回调关联）',
  error_code VARCHAR(128) DEFAULT NULL COMMENT '错误分类码',
  error_message TEXT DEFAULT NULL COMMENT '错误信息',
  started_time DATETIME DEFAULT NULL COMMENT '尝试开始时间',
  completed_time DATETIME DEFAULT NULL COMMENT '尝试结束时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (attempt_id),
  UNIQUE KEY uk_attempt_task_no (task_id, attempt_no),
  KEY idx_attempt_provider (provider_code, provider_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI视频生成任务尝试记录';

-- ============================================================
-- AI novel workflow schema (from ai_novel_workflow.sql)
-- ============================================================

-- AI 小说创作模块（MySQL 8.0 / utf8mb4）
-- 作品为顶层容器；长篇小说按章节存储正文，短篇小说正文直接放在作品表。
-- 逻辑关联使用索引，未使用数据库外键。

CREATE TABLE IF NOT EXISTS ai_novel_work (
  work_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '作品ID',
  work_name VARCHAR(128) NOT NULL COMMENT '作品名称',
  work_type VARCHAR(16) NOT NULL DEFAULT 'novel' COMMENT '作品类型:short-短篇, novel-长篇小说',
  genre VARCHAR(64) DEFAULT '' COMMENT '题材类型，如东方玄幻',
  synopsis TEXT DEFAULT NULL COMMENT '作品梗概',
  manuscript LONGTEXT DEFAULT NULL COMMENT '短篇正文（长篇按章节存储）',
  status VARCHAR(16) NOT NULL DEFAULT 'draft' COMMENT '状态:draft-草稿, writing-写作中, finished-已完成',
  pacing_level VARCHAR(16) NOT NULL DEFAULT 'balanced' COMMENT '节奏档位:relaxed-舒缓, steady-平稳, balanced-均衡, intense-紧凑, rapid-激烈',
  owner_user_id BIGINT NOT NULL COMMENT '所属用户ID',
  create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志:0存在 2删除',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (work_id),
  KEY idx_novel_work_owner (owner_user_id),
  KEY idx_novel_work_update (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI小说作品';

CREATE TABLE IF NOT EXISTS ai_novel_chapter (
  chapter_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '章节ID',
  work_id BIGINT NOT NULL COMMENT '作品ID',
  chapter_no INT NOT NULL DEFAULT 1 COMMENT '章节序号',
  chapter_title VARCHAR(128) DEFAULT NULL COMMENT '章节标题',
  chapter_brief TEXT DEFAULT NULL COMMENT '本章梗概',
  content LONGTEXT DEFAULT NULL COMMENT '章节正文',
  word_count INT NOT NULL DEFAULT 0 COMMENT '正文字数',
  status VARCHAR(16) NOT NULL DEFAULT 'draft' COMMENT '状态:draft-草稿, published-已发布',
  create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志:0存在 2删除',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (chapter_id),
  UNIQUE KEY uk_novel_chapter_work_no (work_id, chapter_no, del_flag),
  KEY idx_novel_chapter_work (work_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI小说章节';

CREATE TABLE IF NOT EXISTS ai_novel_setting (
  setting_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '设定ID',
  work_id BIGINT NOT NULL COMMENT '作品ID',
  setting_type VARCHAR(32) NOT NULL COMMENT '设定类型:character-人物, world-世界观, outline-大纲, item-物品, organization-组织, event-事件, style-文风, other-其他',
  title VARCHAR(128) NOT NULL COMMENT '设定标题',
  content TEXT DEFAULT NULL COMMENT '设定内容',
  create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志:0存在 2删除',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (setting_id),
  KEY idx_novel_setting_work (work_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI小说设定卡';
CREATE TABLE IF NOT EXISTS ai_novel_outline (
  outline_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '大纲ID',
  work_id BIGINT NOT NULL COMMENT '作品ID',
  outline_level VARCHAR(16) NOT NULL COMMENT '层级:BOOK-全书, VOLUME-卷, CHAPTER-章',
  parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父级大纲ID，BOOK 层为 0',
  seq_no INT NOT NULL DEFAULT 0 COMMENT '同级排序序号',
  outline_title VARCHAR(128) NOT NULL COMMENT '大纲标题',
  outline_content TEXT DEFAULT NULL COMMENT '概述/梗概内容',
  chapter_id BIGINT DEFAULT NULL COMMENT '关联章节ID（章级大纲）',
  create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志:0存在 2删除',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (outline_id),
  KEY idx_novel_outline_work (work_id, outline_level),
  KEY idx_novel_outline_parent (parent_id),
  KEY idx_novel_outline_chapter (chapter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI小说三层大纲（全书-卷-章）';

CREATE TABLE IF NOT EXISTS ai_novel_foreshadow (
  foreshadow_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '伏笔ID',
  work_id BIGINT NOT NULL COMMENT '作品ID',
  title VARCHAR(128) NOT NULL COMMENT '伏笔名称',
  description TEXT DEFAULT NULL COMMENT '伏笔详情（埋设内容与预期效果）',
  status VARCHAR(16) NOT NULL DEFAULT 'buried' COMMENT '状态:buried-已埋, pending-待解, resolved-已解',
  priority VARCHAR(8) NOT NULL DEFAULT 'medium' COMMENT '重要等级:high-高, medium-中, low-低',
  keyword VARCHAR(128) DEFAULT NULL COMMENT '伏笔关键词，用于索引与检索',
  resolve_chapter_no INT DEFAULT NULL COMMENT '计划回收章节号',
  create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志:0存在 2删除',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (foreshadow_id),
  KEY idx_novel_foreshadow_work (work_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI小说伏笔';

-- ============================================================
-- AI video chapter soft-delete migration
-- ============================================================

-- 修复 ai_video_chapter 软删除与唯一索引冲突。
-- 唯一键为 (project_id, chapter_no, del_flag)：正常记录使用 '0'，
-- 被删除记录使用 NULL。MySQL 允许唯一键中存在多个 NULL，因而可保留多次删除历史。

ALTER TABLE ai_video_chapter
    MODIFY COLUMN del_flag CHAR(1) NULL DEFAULT '0' COMMENT '删除标志：0为正常，NULL为已删除';

UPDATE ai_video_chapter
SET del_flag = NULL
WHERE del_flag = '2';

-- Note: ai_video_project_cover_migration.sql is intentionally omitted.
-- Its cover_url column is already included in the CREATE TABLE statement above
-- and executing it here would attempt to add the column a second time.

-- ============================================================
-- AI video menus and permissions (from ai_vedio_menu.sql)
-- ============================================================

-- AI 视频创作模块菜单与权限。
-- 执行后请重新登录后台，使动态菜单和权限缓存刷新。
-- 该脚本仅向管理员角色（role_id = 1）授权；其他角色请在“角色管理”中按需分配。

SET @ai_vedio_parent_id := (
    SELECT menu_id FROM sys_menu WHERE parent_id = 0 AND path = 'aiVedio' LIMIT 1
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 'AI视频创作', 0, 15, 'aiVedio', NULL, '', 1, 0, 'M', '0', '0', '', 'video-play', 'admin', NOW(), 'AI视频创作目录'
WHERE @ai_vedio_parent_id IS NULL;

SET @ai_vedio_parent_id := (
    SELECT menu_id FROM sys_menu WHERE parent_id = 0 AND path = 'aiVedio' LIMIT 1
);

SET @ai_vedio_project_menu_id := (
    SELECT menu_id FROM sys_menu WHERE parent_id = @ai_vedio_parent_id AND path = 'project' LIMIT 1
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '小说视频项目', @ai_vedio_parent_id, 1, 'project', 'aiVedio/project/index', '', 1, 0, 'C', '0', '0', 'aivideo:project:list', 'video-play', 'admin', NOW(), 'AI视频项目工作台'
WHERE @ai_vedio_project_menu_id IS NULL;

SET @ai_vedio_project_menu_id := (
    SELECT menu_id FROM sys_menu WHERE parent_id = @ai_vedio_parent_id AND path = 'project' LIMIT 1
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT x.menu_name, @ai_vedio_project_menu_id, x.order_num, '', '', '', 1, 0, 'F', '0', '0', x.perms, '#', 'admin', NOW(), x.remark
FROM (
    SELECT '项目查询' AS menu_name, 1 AS order_num, 'aivideo:project:query' AS perms, '查看AI视频项目' AS remark
    UNION ALL SELECT '项目新增', 2, 'aivideo:project:add', '新建AI视频项目'
    UNION ALL SELECT '项目修改', 3, 'aivideo:project:edit', '编辑AI视频项目和章节'
    UNION ALL SELECT '项目删除', 4, 'aivideo:project:remove', '删除AI视频项目'
    UNION ALL SELECT '资产查询', 5, 'aivideo:asset:list', '查看AI视频资产'
    UNION ALL SELECT '资产详情', 6, 'aivideo:asset:query', '查看AI视频资产详情'
) x
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu existing_menu WHERE existing_menu.perms = x.perms
);

SET @ai_vedio_task_menu_id := (
    SELECT menu_id FROM sys_menu WHERE parent_id = @ai_vedio_parent_id AND path = 'task' LIMIT 1
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '生成队列', @ai_vedio_parent_id, 2, 'task', 'aiVedio/task/index', '', 1, 0, 'C', '0', '0', 'aivideo:task:list', 'list', 'admin', NOW(), 'AI视频生成任务队列'
WHERE @ai_vedio_task_menu_id IS NULL;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id
FROM sys_menu
WHERE menu_id = @ai_vedio_parent_id
   OR parent_id = @ai_vedio_parent_id
   OR parent_id = @ai_vedio_project_menu_id
   OR parent_id = @ai_vedio_task_menu_id
ON DUPLICATE KEY UPDATE menu_id = VALUES(menu_id);

-- ============================================================
-- AI novel menus and permissions (from ai_novel_menu.sql)
-- ============================================================

-- AI 小说创作模块菜单与权限。
-- 该脚本仅向管理员角色（role_id = 1）授权；其他角色请在“角色管理”中按需分配。

SET @novel_parent_id := (
    SELECT menu_id FROM sys_menu WHERE parent_id = 0 AND path = 'novel' LIMIT 1
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 'AI小说创作', 0, 16, 'novel', NULL, '', 1, 0, 'M', '0', '0', '', 'edit', 'admin', NOW(), 'AI小说创作目录'
WHERE @novel_parent_id IS NULL;

SET @novel_parent_id := (
    SELECT menu_id FROM sys_menu WHERE parent_id = 0 AND path = 'novel' LIMIT 1
);

SET @novel_writing_menu_id := (
    SELECT menu_id FROM sys_menu WHERE parent_id = @novel_parent_id AND path = 'writing' LIMIT 1
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '小说写作台', @novel_parent_id, 1, 'writing', 'novel/index', '', 1, 0, 'C', '0', '0', 'novel:writing:list', 'edit', 'admin', NOW(), 'AI小说创作工作台（短篇/长篇）'
WHERE @novel_writing_menu_id IS NULL;

SET @novel_writing_menu_id := (
    SELECT menu_id FROM sys_menu WHERE parent_id = @novel_parent_id AND path = 'writing' LIMIT 1
);

INSERT INTO sys_menu
    (menu_name, parent_id, order_num, path, component, query, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT x.menu_name, @novel_writing_menu_id, x.order_num, '', '', '', 1, 0, 'F', '0', '0', x.perms, '#', 'admin', NOW(), x.remark
FROM (
    SELECT '作品查询' AS menu_name, 1 AS order_num, 'novel:work:list' AS perms, '查看作品列表' AS remark
    UNION ALL SELECT '作品新增', 2, 'novel:work:add', '新建作品'
    UNION ALL SELECT '作品修改', 3, 'novel:work:edit', '编辑作品信息与正文'
    UNION ALL SELECT '作品删除', 4, 'novel:work:remove', '删除作品'
) x
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu existing_menu WHERE existing_menu.perms = x.perms
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id
FROM sys_menu
WHERE menu_id = @novel_parent_id
   OR parent_id = @novel_parent_id
   OR parent_id = @novel_writing_menu_id
ON DUPLICATE KEY UPDATE menu_id = VALUES(menu_id);

-- AI 聊天会话附件元数据。文件本体保存在配置的 x-file-storage 平台（生产为阿里云 OSS）。
CREATE TABLE IF NOT EXISTS `tb_ai_chat_attachment` (
                                                       `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                                       `attachment_id` varchar(36) NOT NULL COMMENT '对外附件ID',
    `session_id` varchar(128) NOT NULL COMMENT '所属会话',
    `user_id` varchar(128) NOT NULL COMMENT '所属用户',
    `history_id` bigint DEFAULT NULL COMMENT '绑定的用户消息ID',
    `original_name` varchar(255) NOT NULL COMMENT '原始文件名',
    `storage_platform` varchar(64) NOT NULL COMMENT 'x-file-storage平台',
    `storage_path` varchar(512) NOT NULL COMMENT '对象路径',
    `storage_filename` varchar(255) NOT NULL COMMENT '对象文件名',
    `object_url` varchar(2048) DEFAULT NULL COMMENT '存储平台对象URL，仅用于对象删除元数据',
    `mime_type` varchar(128) NOT NULL COMMENT '服务端识别的MIME类型',
    `file_size` bigint NOT NULL COMMENT '文件字节数',
    `attachment_kind` varchar(16) NOT NULL COMMENT 'IMAGE或DOCUMENT',
    `extracted_text` mediumtext COMMENT '文档提取后的有界文本',
    `extract_truncated` tinyint(1) NOT NULL DEFAULT 0 COMMENT '提取文本是否被截断',
    `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING或USED',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_chat_attachment_id` (`attachment_id`),
    KEY `idx_ai_chat_attachment_owner` (`user_id`,`session_id`,`status`),
    KEY `idx_ai_chat_attachment_history` (`history_id`),
    KEY `idx_ai_chat_attachment_created` (`created_at`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI聊天会话附件';
-- ----------------------------
-- 系统安全配置
-- 将 application.yml 中的敏感配置项迁移到 sys_config 表，通过管理界面维护。
-- ----------------------------
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

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT 'Agent服务-API Key', 'security.agent.serviceApiKey', '', 'Y', 'admin', NOW(), '系统安全配置页面维护'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'security.agent.serviceApiKey');

INSERT INTO sys_config (config_name, config_key, config_value, config_type, create_by, create_time, remark)
SELECT '联网搜索-Tavily API Key', 'security.search.tavilyApiKey', '', 'Y', 'admin', NOW(), '系统安全配置页面维护'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'security.search.tavilyApiKey');
