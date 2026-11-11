-- Run once after database backup, before APP_COVER_ENABLED=true.
ALTER TABLE app ADD COLUMN coverMode varchar(16) NOT NULL DEFAULT 'AUTO';
ALTER TABLE app ADD COLUMN coverStatus varchar(16) NOT NULL DEFAULT 'NONE';
ALTER TABLE app ADD COLUMN coverVersion int NOT NULL DEFAULT 0;
ALTER TABLE app ADD COLUMN coverRevision bigint NOT NULL DEFAULT 0;
ALTER TABLE app ADD COLUMN coverObjectKey varchar(512) NULL;
ALTER TABLE app ADD COLUMN coverTaskId bigint NULL;
UPDATE app SET coverMode='MANUAL' WHERE cover IS NOT NULL AND cover<>'';
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
