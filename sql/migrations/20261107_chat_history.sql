-- 对话历史模块：已有 chat_history 表时不会覆盖数据或修改表结构。
USE appforge;
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
