-- 在已有 appforge 库执行；独立摘要表，不修改 chat_history 原文。
USE appforge;
CREATE TABLE IF NOT EXISTS chat_memory_summary (
 appId bigint NOT NULL PRIMARY KEY,
 coveredThroughId bigint NOT NULL DEFAULT 0,
 coveredMessages bigint NOT NULL DEFAULT 0,
 summary longtext NOT NULL,
 revision bigint NOT NULL DEFAULT 0,
 updatedTime datetime NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
