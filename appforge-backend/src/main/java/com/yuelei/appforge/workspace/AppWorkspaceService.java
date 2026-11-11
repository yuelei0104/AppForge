package com.yuelei.appforge.workspace;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;

import com.yuelei.appforge.exception.*;
import com.yuelei.appforge.model.entity.*;
import com.yuelei.appforge.workspace.vue.VueProjectFiles;

import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;

/** 单应用行锁串行化版本、发布、回退和删除；数据库版本为访问内容的权威来源。 */
@Service
@Slf4j
public class AppWorkspaceService {
    @Resource private com.yuelei.appforge.cover.AppCoverTasks coverTasks;
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private TransactionTemplate transactionTemplate;
    @Resource private AppFileStore fileStore;
    private final RowMapper<App> appMapper = BeanPropertyRowMapper.newInstance(App.class);

    public App requireApp(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 无效");
        var rows = jdbcTemplate.query("SELECT * FROM app WHERE id=? AND isDelete=0", appMapper, id);
        ThrowUtils.throwIf(rows.isEmpty(), ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        return rows.get(0);
    }

    private App lock(long id) {
        var rows =
                jdbcTemplate.query(
                        "SELECT * FROM app WHERE id=? AND isDelete=0 FOR UPDATE", appMapper, id);
        ThrowUtils.throwIf(rows.isEmpty(), ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        return rows.get(0);
    }

    public Map<String, String> files(long id, int version) {
        var rows =
                jdbcTemplate.queryForList(
                        "SELECT codeFiles FROM app_version WHERE appId=? AND versionNo=?",
                        String.class,
                        id,
                        version);
        ThrowUtils.throwIf(rows.isEmpty(), ErrorCode.NOT_FOUND_ERROR, "版本不存在");
        return JSONUtil.toBean(rows.get(0), new TypeReference<Map<String, String>>() {}, false);
    }

    public int ensureBaseline(long id) {
        return Objects.requireNonNull(
                transactionTemplate.execute(
                        tx -> {
                            App app = lock(id);
                            if (app.getCurrentVersion() != null && app.getCurrentVersion() > 0)
                                return app.getCurrentVersion();
                            Map<String, String> legacy =
                                    fileStore.legacyFiles(id, app.getCodeGenType(), null);
                            if (!legacy.containsKey("index.html")
                                    || legacy.get("index.html").isBlank()) return 0;
                            return append(app, legacy, "IMPORT", null, "导入升级前的代码");
                        }));
    }

    public List<AppVersionVO> versions(long id, User user) {
        AppPolicy.manage(requireApp(id), user);
        ensureBaseline(id);
        return jdbcTemplate.query(
                "SELECT versionNo,kind,sourceVersion,summary,createTime FROM app_version WHERE"
                        + " appId=? ORDER BY versionNo DESC LIMIT 100",
                (rs, n) ->
                        new AppVersionVO(
                                rs.getInt(1),
                                rs.getString(2),
                                (Integer) rs.getObject(3),
                                rs.getString(4),
                                rs.getTimestamp(5).toLocalDateTime()),
                id);
    }

    public Map<String, String> versionFiles(long id, int version, User user) {
        AppPolicy.manage(requireApp(id), user);
        Map<String, String> files = files(id, version);
        return VueProjectFiles.isProject(files) ? VueProjectFiles.sources(files) : files;
    }

    private int append(
            App app, Map<String, String> files, String kind, Integer source, String summary) {
        ThrowUtils.throwIf(
                "vue_project".equals(app.getCodeGenType()) != VueProjectFiles.isProject(files),
                ErrorCode.OPERATION_ERROR,
                "版本类型与应用不一致");
        validateFiles(files);
        Integer latest =
                jdbcTemplate.queryForObject(
                        "SELECT COALESCE(MAX(versionNo),0) FROM app_version WHERE appId=?",
                        Integer.class,
                        app.getId());
        int next = (latest == null ? 0 : latest) + 1;
        try {
            // 数据库没有 next 版本，磁盘同名目录只能是此前事务失败留下的未提交快照。
            fileStore.removeVersion(app.getId(), next);
            fileStore.writeVersion(app.getId(), next, files);
            jdbcTemplate.update(
                    "INSERT INTO"
                        + " app_version(appId,versionNo,kind,sourceVersion,summary,codeFiles,createTime)"
                        + " VALUES(?,?,?,?,?,?,?)",
                    app.getId(),
                    next,
                    kind,
                    source,
                    summary.substring(0, Math.min(summary.length(), 256)),
                    JSONUtil.toJsonStr(files),
                    LocalDateTime.now());
            jdbcTemplate.update(
                    "UPDATE app SET"
                        + " currentVersion=?,generationStatus='SUCCEEDED',generationError='',generationToken='',updateTime=?"
                        + " WHERE id=? AND isDelete=0",
                    next,
                    LocalDateTime.now(),
                    app.getId());
            if (coverTasks != null)
                coverTasks.enqueue(
                        app.getId(), next, "ROLLBACK".equals(kind) ? "RESTORE" : "GENERATE");
            return next;
        } catch (RuntimeException e) {
            try {
                fileStore.removeVersion(app.getId(), next);
            } catch (RuntimeException cleanup) {
                log.error("清理未提交版本失败 appId={}", app.getId(), cleanup);
            }
            throw e;
        }
    }

    public int complete(long id, String token, Map<String, String> files, String prompt) {
        return Objects.requireNonNull(
                transactionTemplate.execute(
                        tx -> {
                            App app = lock(id);
                            ThrowUtils.throwIf(
                                    !"GENERATING".equals(app.getGenerationStatus())
                                            || !token.equals(app.getGenerationToken()),
                                    ErrorCode.OPERATION_ERROR,
                                    "生成已停止，未保存不完整代码");
                            return append(app, files, "GENERATE", null, prompt);
                        }));
    }

    public int rollback(long id, int version, User user) {
        return Objects.requireNonNull(
                transactionTemplate.execute(
                        tx -> {
                            App app = lock(id);
                            AppPolicy.manage(app, user);
                            AppPolicy.idle(app);
                            return append(
                                    app,
                                    files(id, version),
                                    "ROLLBACK",
                                    version,
                                    "从 v" + version + " 恢复；已部署的网站保持不变");
                        }));
    }

    public String deploy(long id, User user) {
        App app = requireApp(id);
        AppPolicy.owner(app, user);
        AppPolicy.idle(app);
        ensureBaseline(id);
        return transactionTemplate.execute(
                tx -> {
                    App current = lock(id);
                    AppPolicy.owner(current, user);
                    AppPolicy.idle(current);
                    int version =
                            current.getCurrentVersion() == null ? 0 : current.getCurrentVersion();
                    ThrowUtils.throwIf(version <= 0, ErrorCode.OPERATION_ERROR, "请先成功生成代码，再部署");
                    validateFiles(files(id, version));
                    String key = current.getDeployKey();
                    if (key == null || key.isBlank())
                        key = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                    jdbcTemplate.update(
                            "UPDATE app SET"
                                + " deployKey=?,deployedVersion=?,deploymentStatus='ONLINE',deployedTime=?,updateTime=?"
                                + " WHERE id=? AND isDelete=0",
                            key,
                            version,
                            LocalDateTime.now(),
                            LocalDateTime.now(),
                            id);
                    if (coverTasks != null) coverTasks.enqueue(id, version, "DEPLOY");
                    return key;
                });
    }

    public boolean offline(long id, User user) {
        return Boolean.TRUE.equals(
                transactionTemplate.execute(
                        tx -> {
                            App app = lock(id);
                            AppPolicy.manage(app, user);
                            AppPolicy.idle(app);
                            jdbcTemplate.update(
                                    "UPDATE app SET deploymentStatus='OFFLINE',updateTime=? WHERE"
                                            + " id=?",
                                    LocalDateTime.now(),
                                    id);
                            return true;
                        }));
    }

    public boolean feature(long id, User user, Boolean approved, String reason) {
        return Boolean.TRUE.equals(
                transactionTemplate.execute(
                        tx -> {
                            App app = lock(id);
                            if (approved == null) {
                                AppPolicy.owner(app, user);
                                ThrowUtils.throwIf(
                                        !AppPolicy.isPublic(app),
                                        ErrorCode.PARAMS_ERROR,
                                        "请先将应用设为公开");
                                ThrowUtils.throwIf(
                                        "PENDING".equals(app.getFeatureStatus())
                                                || "APPROVED".equals(app.getFeatureStatus()),
                                        ErrorCode.OPERATION_ERROR,
                                        "应用已申请或已入选精选");
                                ensureBaseline(id);
                                ThrowUtils.throwIf(
                                        requireApp(id).getCurrentVersion() == 0,
                                        ErrorCode.OPERATION_ERROR,
                                        "请先成功生成应用");
                                jdbcTemplate.update(
                                        "UPDATE app SET"
                                            + " featureStatus='PENDING',featureReason='',updateTime=?"
                                            + " WHERE id=?",
                                        LocalDateTime.now(),
                                        id);
                            } else {
                                ThrowUtils.throwIf(
                                        !AppPolicy.isAdmin(user), ErrorCode.NO_AUTH_ERROR);
                                ThrowUtils.throwIf(
                                        !"PENDING".equals(app.getFeatureStatus()),
                                        ErrorCode.OPERATION_ERROR,
                                        "该申请已处理，请刷新列表");
                                ThrowUtils.throwIf(
                                        approved && !AppPolicy.isPublic(app),
                                        ErrorCode.PARAMS_ERROR,
                                        "私有应用不能入选精选");
                                String text = reason == null ? "" : reason.trim();
                                ThrowUtils.throwIf(
                                        text.length() > 256,
                                        ErrorCode.PARAMS_ERROR,
                                        "审核说明最多 256 字");
                                jdbcTemplate.update(
                                        "UPDATE app SET"
                                            + " featureStatus=?,featureReason=?,priority=?,updateTime=?"
                                            + " WHERE id=?",
                                        approved ? "APPROVED" : "REJECTED",
                                        text,
                                        approved ? 99 : 0,
                                        LocalDateTime.now(),
                                        id);
                            }
                            return true;
                        }));
    }

    public boolean delete(long id, User user) {
        boolean result =
                Boolean.TRUE.equals(
                        transactionTemplate.execute(
                                tx -> {
                                    App app = lock(id);
                                    AppPolicy.manage(app, user);
                                    AppPolicy.idle(app);
                                    jdbcTemplate.update(
                                            "INSERT INTO"
                                                + " app_file_cleanup(appId,codeGenType,deployKey)"
                                                + " VALUES(?,?,?)",
                                            id,
                                            app.getCodeGenType() == null
                                                    ? "html"
                                                    : app.getCodeGenType(),
                                            app.getDeployKey());
                                    if (coverTasks != null) coverTasks.deleted(id);
                                    jdbcTemplate.update(
                                            "DELETE FROM app_version WHERE appId=?", id);
                                    jdbcTemplate.update(
                                            "DELETE FROM chat_memory_summary WHERE appId=?", id);
                                    jdbcTemplate.update(
                                            "UPDATE app SET"
                                                + " isDelete=1,deploymentStatus='OFFLINE',generationToken='',updateTime=?"
                                                + " WHERE id=?",
                                            LocalDateTime.now(),
                                            id);
                                    return true;
                                }));
        cleanFiles();
        return result;
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void cleanFiles() {
        for (var row :
                jdbcTemplate.queryForList(
                        "SELECT appId,codeGenType,deployKey FROM app_file_cleanup LIMIT 20")) {
            long id = ((Number) row.get("appId")).longValue();
            try {
                fileStore.cleanup(
                        id, (String) row.get("codeGenType"), (String) row.get("deployKey"));
                jdbcTemplate.update("DELETE FROM app_file_cleanup WHERE appId=?", id);
            } catch (RuntimeException e) {
                log.warn("应用已删除，文件将在下次任务重试清理 appId={}", id, e);
            }
        }
    }

    public Map<String, String> previewFiles(App app) {
        if (app.getCurrentVersion() != null && app.getCurrentVersion() > 0)
            return files(app.getId(), app.getCurrentVersion());
        return fileStore.legacyFiles(app.getId(), app.getCodeGenType(), null);
    }

    public Map<String, String> deployedFiles(App app) {
        if (app.getDeployedVersion() != null && app.getDeployedVersion() > 0)
            return files(app.getId(), app.getDeployedVersion());
        return fileStore.legacyFiles(app.getId(), app.getCodeGenType(), app.getDeployKey());
    }

    public App byDeployKey(String key) {
        ThrowUtils.throwIf(
                key == null || !key.matches("[A-Za-z0-9]{1,64}"), ErrorCode.NOT_FOUND_ERROR);
        var list =
                jdbcTemplate.query(
                        "SELECT * FROM app WHERE deployKey=? AND deploymentStatus='ONLINE' AND"
                                + " isDelete=0",
                        appMapper,
                        key);
        ThrowUtils.throwIf(list.isEmpty(), ErrorCode.NOT_FOUND_ERROR, "应用已下线或不存在");
        return list.get(0);
    }

    public static void validateFiles(Map<String, String> files) {
        if (files != null && VueProjectFiles.isProject(files)) {
            VueProjectFiles.validate(files);
            return;
        }
        ThrowUtils.throwIf(
                files == null
                        || files.getOrDefault("index.html", "").isBlank()
                        || !files.getOrDefault("index.html", "")
                                .matches("(?s).*(?:<[a-zA-Z][^>]*>|<!DOCTYPE[^>]*>).*"),
                ErrorCode.OPERATION_ERROR,
                "模型未返回完整 HTML，未保存此次生成");
        long length = files.values().stream().mapToLong(String::length).sum();
        ThrowUtils.throwIf(
                length > 2_000_000 || !AppFileStore.FILES.containsAll(files.keySet()),
                ErrorCode.OPERATION_ERROR,
                "生成代码过大或包含不支持的文件");
    }
}
