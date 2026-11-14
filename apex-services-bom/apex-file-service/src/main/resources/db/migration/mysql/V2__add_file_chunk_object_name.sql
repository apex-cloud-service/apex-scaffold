-- ============================================================
-- Apex Scaffold - 文件服务分片表对象名补列脚本（MySQL）
-- ============================================================
-- 【用途】
--   分片上传需要记录每个分片在对象存储中的实际对象键（object_name），
--   以便合并时按序下载并拼接。由于存储 SPI（AbstractStorageService）在上传时
--   统一以 UUID 重命名对象，客户端提交的原始对象名无法作为存储键使用，
--   因此必须将服务端返回的真实对象键持久化到 file_chunk。
--   bucket_name 一并冗余，便于跨桶合并时定位分片。
--
-- 【变更内容】
--   file_chunk 新增 object_name、bucket_name 两列。
--
-- 【方言约定】
--   MySQL 统一使用 DATETIME；字符集 utf8mb4。
--
-- @author Apex Team
-- @version 1.0.0
-- @since 1.0.0
-- ============================================================

ALTER TABLE `file_chunk`
    ADD COLUMN `object_name` VARCHAR(500) DEFAULT NULL COMMENT '分片在对象存储中的对象键' AFTER `md5`,
    ADD COLUMN `bucket_name` VARCHAR(100) DEFAULT NULL COMMENT '分片存储桶名称' AFTER `object_name`;
