# 数据库初始化
# @author <a href="https://github.com/liyupi">程序员鱼皮</a>
# @from <a href="https://codefather.cn">编程导航学习圈</a>

-- 创建库
create database if not exists appforge;

-- 切换库
use appforge;

-- 用户表
create table if not exists user
(
  id           bigint auto_increment comment 'id' primary key,
  userAccount  varchar(256)                           not null comment '账号',
  userPassword varchar(512)                           not null comment '密码',
  userName     varchar(256)                           null comment '用户昵称',
  userAvatar   varchar(1024)                          null comment '用户头像',
  userProfile  varchar(512)                           null comment '用户简介',
  userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
  editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
  createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
  updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
  isDelete     tinyint      default 0                 not null comment '是否删除',
  UNIQUE KEY uk_userAccount (userAccount),
  INDEX idx_userName (userName)
  ) comment '用户' collate = utf8mb4_unicode_ci;



SHOW TABLES FROM appforge LIKE 'user';

UPDATE user SET userRole = 'admin' WHERE userAccount = '13111009837';

-- 应用表
create table if not exists app
(
  id           bigint auto_increment comment 'id' primary key,
  appName      varchar(256)                       null comment '应用名称',
  cover        varchar(512)                       null comment '应用封面',
    coverMode varchar(16) NOT NULL DEFAULT 'AUTO',
    coverStatus varchar(16) NOT NULL DEFAULT 'NONE',
    coverVersion int NOT NULL DEFAULT 0,
    coverRevision bigint NOT NULL DEFAULT 0,
    coverObjectKey varchar(512) NULL,
    coverTaskId bigint NULL,
  initPrompt   text                               null comment '应用初始化的 prompt',
  codeGenType  varchar(64)                        null comment '代码生成类型（枚举）',
  deployKey    varchar(64)                        null comment '部署标识',
  deployedTime datetime                           null comment '部署时间',
  description varchar(1000) NOT NULL DEFAULT '',
  tags varchar(256) NOT NULL DEFAULT '',
  visibility varchar(16) NOT NULL DEFAULT 'PRIVATE',
  generationStatus varchar(16) NOT NULL DEFAULT 'IDLE',
  generationError varchar(512) NOT NULL DEFAULT '',
  generationToken varchar(36) NOT NULL DEFAULT '',
  currentVersion int NOT NULL DEFAULT 0,
  deployedVersion int NOT NULL DEFAULT 0,
  deploymentStatus varchar(16) NOT NULL DEFAULT 'OFFLINE',
  featureStatus varchar(16) NOT NULL DEFAULT 'NONE',
  featureReason varchar(256) NOT NULL DEFAULT '',
  priority     int      default 0                 not null comment '优先级',
  userId       bigint                             not null comment '创建用户id',
  editTime     datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
  createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
  updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
  isDelete     tinyint  default 0                 not null comment '是否删除',
  UNIQUE KEY uk_deployKey (deployKey), -- 确保部署标识唯一
  INDEX idx_appName (appName),         -- 提升基于应用名称的查询性能
  INDEX idx_userId (userId),
  INDEX idx_app_discovery (visibility, isDelete, priority, createTime),
  INDEX idx_app_feature_review (featureStatus, isDelete)
) comment '应用' collate = utf8mb4_unicode_ci;


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

CREATE TABLE IF NOT EXISTS chat_history (
  id bigint NOT NULL PRIMARY KEY COMMENT '雪花 ID',
  message longtext NOT NULL COMMENT '消息内容',
  messageType varchar(16) NOT NULL COMMENT 'user/ai',
  appId bigint NOT NULL,
  userId bigint NOT NULL,
  createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  isDelete tinyint NOT NULL DEFAULT 0,
  KEY idx_chat_history_cursor (appId, isDelete, createTime, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS chat_memory_summary (
 appId bigint NOT NULL PRIMARY KEY,
 coveredThroughId bigint NOT NULL DEFAULT 0,
 coveredMessages bigint NOT NULL DEFAULT 0,
 summary longtext NOT NULL,
 revision bigint NOT NULL DEFAULT 0,
 updatedTime datetime NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE app_cover_task (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
 appId bigint NOT NULL,versionNo int NOT NULL,revision bigint NOT NULL,
 profile varchar(32) NOT NULL DEFAULT 'desktop-v1',requestId varchar(64) NULL,requestedBy bigint NULL,
 triggerType varchar(16) NOT NULL,status varchar(16) NOT NULL DEFAULT 'PENDING',attempts int NOT NULL DEFAULT 0,
 nextRunTime datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),executionToken varchar(64) NULL,
 workerId varchar(128) NULL,leaseUntil datetime(3) NULL,renderTokenHash varchar(64) NULL,
 renderTokenExpiresAt datetime(3) NULL,objectKey varchar(512) NULL,
 errorCode varchar(64) NOT NULL DEFAULT '',errorMessage varchar(512) NOT NULL DEFAULT '',
 createTime datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updateTime datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_cover_request(appId,versionNo,revision,profile),UNIQUE KEY uk_cover_manual(appId,requestId),
 KEY idx_cover_due(status,nextRunTime,id),KEY idx_cover_lease(status,leaseUntil),
 KEY idx_cover_app(appId,id),KEY idx_cover_user(requestedBy,createTime)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE app_cover_cleanup (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,objectKey varchar(512) NOT NULL,
 notBefore datetime(3) NOT NULL,nextRunTime datetime(3) NOT NULL,attempts int NOT NULL DEFAULT 0,
 createTime datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_cover_object(objectKey),KEY idx_cover_cleanup(nextRunTime,notBefore)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_attachment (
  id bigint NOT NULL PRIMARY KEY COMMENT '雪花 ID',
  appId bigint NULL COMMENT '关联应用；上传时可为空',
  userId bigint NOT NULL COMMENT '上传用户',
  chatHistoryId bigint NULL COMMENT '绑定的用户消息 ID；为空表示尚未发送',
  kind varchar(16) NOT NULL COMMENT 'image/text/document',
  originalName varchar(256) NOT NULL,
  mimeType varchar(128) NOT NULL,
  sizeBytes bigint NOT NULL,
  storageKey varchar(512) NOT NULL COMMENT 'cos:key 或 local:key',
  url varchar(1024) NOT NULL DEFAULT '',
  extractedText longtext NULL,
  createTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updateTime datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  isDelete tinyint NOT NULL DEFAULT 0,
  KEY idx_attachment_user (userId, isDelete, createTime),
  KEY idx_attachment_app (appId, isDelete, createTime),
  KEY idx_attachment_message (chatHistoryId, isDelete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
