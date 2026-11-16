-- 已有库升级。新安装直接用 ruoyi-vue-pro.sql，不要再执行本文件。
-- 示例配置里的云密钥不在这里复制。

ALTER TABLE `infra_file`
  MODIFY COLUMN `size` bigint NOT NULL COMMENT '文件大小',
  ADD COLUMN `hash` varchar(128) NULL DEFAULT NULL COMMENT '客户端声明的哈希，服务端核对大小和文件头' AFTER `size`,
  ADD COLUMN `status` tinyint NULL DEFAULT NULL COMMENT '状态，1 正常' AFTER `hash`;

CREATE TABLE IF NOT EXISTS `infra_file_upload_task`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `config_id` bigint NOT NULL COMMENT '配置编号',
  `owner_user_id` bigint NOT NULL COMMENT '上传人',
  `name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '原文件名',
  `path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '对象路径',
  `type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文件类型',
  `size` bigint NOT NULL COMMENT '声明大小',
  `hash` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '客户端声明的哈希',
  `status` tinyint NOT NULL COMMENT '0 初始化 1 上传中 2 已完成 3 已取消 4 已过期 5 失败',
  `multipart` bit(1) NOT NULL COMMENT '是否分片',
  `upload_id` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '分片上传编号',
  `file_id` bigint NULL DEFAULT NULL COMMENT '完成后的文件编号',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_infra_file_upload_task_owner`(`owner_user_id`) USING BTREE,
  INDEX `idx_infra_file_upload_task_expire`(`status`, `expire_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文件上传任务';

CREATE TABLE IF NOT EXISTS `infra_file_relation`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `file_id` bigint NOT NULL COMMENT '文件编号',
  `biz_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务类型',
  `biz_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务编号',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_infra_file_relation_file`(`file_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文件引用';

CREATE TABLE IF NOT EXISTS `infra_file_operation_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `task_id` bigint NULL DEFAULT NULL COMMENT '上传任务编号',
  `file_id` bigint NULL DEFAULT NULL COMMENT '文件编号',
  `action` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '动作',
  `success` bit(1) NOT NULL COMMENT '是否成功',
  `message` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '说明，不含密钥和预签名',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_infra_file_operation_log_task`(`task_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文件操作日志';

INSERT INTO `infra_job` (`id`, `name`, `status`, `handler_name`, `handler_param`, `cron_expression`, `retry_count`, `retry_interval`, `monitor_timeout`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 42, '文件上传清理 Job', 2, 'fileUploadCleanJob', '', '0 0/10 * * * ?', 0, 0, 0, '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `infra_job` WHERE `handler_name` = 'fileUploadCleanJob' AND `deleted` = b'0');

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 14001, '文件上传', 'infra:file:create', 3, 2, 95, '', '', '', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE `permission` = 'infra:file:create' AND `deleted` = b'0');
