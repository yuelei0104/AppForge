-- 已有数据库升级：先备份，再执行一次。无需重新运行 create_table.sql。
USE appforge;
ALTER TABLE app
  ADD COLUMN description varchar(1000) NOT NULL DEFAULT '',
  ADD COLUMN tags varchar(256) NOT NULL DEFAULT '',
  ADD COLUMN visibility varchar(16) NOT NULL DEFAULT 'PRIVATE',
  ADD COLUMN generationStatus varchar(16) NOT NULL DEFAULT 'IDLE',
  ADD COLUMN generationError varchar(512) NOT NULL DEFAULT '',
  ADD COLUMN generationToken varchar(36) NOT NULL DEFAULT '',
  ADD COLUMN currentVersion int NOT NULL DEFAULT 0,
  ADD COLUMN deployedVersion int NOT NULL DEFAULT 0,
  ADD COLUMN deploymentStatus varchar(16) NOT NULL DEFAULT 'OFFLINE',
  ADD COLUMN featureStatus varchar(16) NOT NULL DEFAULT 'NONE',
  ADD COLUMN featureReason varchar(256) NOT NULL DEFAULT '';
-- 只将已精选作品迁为公开，其余已有作品默认私有。
UPDATE app SET visibility='PUBLIC', featureStatus='APPROVED' WHERE priority >= 99 AND isDelete=0;
UPDATE app SET deploymentStatus='ONLINE' WHERE deployKey IS NOT NULL AND deployKey<>'' AND isDelete=0;
CREATE INDEX idx_app_discovery ON app(visibility, isDelete, priority, createTime);
CREATE INDEX idx_app_feature_review ON app(featureStatus, isDelete);
CREATE TABLE app_version (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  appId bigint NOT NULL,
  versionNo int NOT NULL,
  kind varchar(16) NOT NULL,
  sourceVersion int NULL,
  summary varchar(256) NOT NULL DEFAULT '',
  codeFiles longtext NOT NULL,
  createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_app_version(appId,versionNo)
) COLLATE=utf8mb4_unicode_ci;
CREATE TABLE app_file_cleanup (
  appId bigint PRIMARY KEY,
  codeGenType varchar(64) NOT NULL,
  deployKey varchar(64) NULL,
  createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
) COLLATE=utf8mb4_unicode_ci;
