package com.yuelei.appforge.cover;

import static org.junit.jupiter.api.Assertions.*;

import cn.hutool.json.JSONUtil;

import com.yuelei.appforge.exception.BusinessException;
import com.yuelei.appforge.model.entity.User;
import com.yuelei.appforge.model.vo.AppVO;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import javax.sql.DataSource;

abstract class CoverTestDatabase {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    CoverProperties config;
    AppCoverTasks tasks;
    User owner, other;

    DataSource dataSource() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        return ds;
    }

    @BeforeEach
    void database() throws Exception {
        DataSource ds = dataSource();
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute(
                "CREATE TABLE app(id bigint primary key,userId bigint,visibility varchar(16)"
                        + " default 'PRIVATE',priority int default 0,isDelete int default"
                        + " 0,currentVersion int default 1,cover varchar(512))");
        jdbc.execute("CREATE TABLE `user`(id bigint primary key)");
        jdbc.update("INSERT INTO `user` VALUES(100),(200)");
        jdbc.execute(
                "CREATE TABLE app_version(appId bigint,versionNo int,codeFiles text,primary"
                        + " key(appId,versionNo))");
        String migration =
                Files.readString(Path.of("../sql/migrations/20261110_app_cover.sql"))
                        .replace(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "");
        for (String sql : migration.split(";")) if (!sql.isBlank()) jdbc.execute(sql);
        config = new CoverProperties();
        config.setEnabled(true);
        tasks = new AppCoverTasks(jdbc, tx, config);
        tasks.clock = Clock.fixed(Instant.parse("2026-11-10T01:00:00Z"), ZoneOffset.UTC);
        owner = new User();
        owner.setId(100L);
        owner.setUserRole("user");
        other = new User();
        other.setId(200L);
        other.setUserRole("user");
        addApp(1);
    }

    void addApp(long id) {
        jdbc.update(
                "INSERT INTO app(id,userId,cover) VALUES(?,100,'https://old.example/cover.jpg')",
                id);
        files(
                id,
                1,
                Map.of("index.html", "<!doctype html><meta charset=utf-8><h1>应用 " + id + "</h1>"));
    }

    void files(long id, int version, Map<String, String> files) {
        jdbc.update(
                "INSERT INTO app_version VALUES(?,?,?)", id, version, JSONUtil.toJsonStr(files));
    }

    long taskId() {
        return jdbc.queryForObject("SELECT coverTaskId FROM app WHERE id=1", Long.class);
    }

    Map<String, Object> task() {
        return jdbc.queryForMap("SELECT * FROM app_cover_task WHERE id=?", taskId());
    }

    void advance(long seconds) {
        tasks.clock = Clock.offset(tasks.clock, Duration.ofSeconds(seconds));
    }

    AppCoverTasks.Attempt claim() {
        tasks.enqueue(1, 1, "GENERATE");
        return tasks.claim(taskId(), "test");
    }
}

class CoverTaskTest extends CoverTestDatabase {
    @Test
    void deduplicatesGenerationAndDeployAndReusesSuccess() {
        tasks.enqueue(1, 1, "GENERATE");
        long id = taskId();
        tasks.enqueue(1, 1, "DEPLOY");
        assertEquals(id, taskId());
        assertTrue(tasks.publish(tasks.claim(id, "a")));
        tasks.enqueue(1, 1, "DEPLOY");
        assertEquals(id, taskId());
        assertEquals("SUCCEEDED", tasks.status(1, owner).get("status"));
    }

    @Test
    void queueInsertRollsBackWithOuterTransaction() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        tx.executeWithoutResult(
                                s -> {
                                    tasks.enqueue(1, 1, "GENERATE");
                                    throw new IllegalStateException();
                                }));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM app_cover_task", Integer.class));
    }

    @Test
    void concurrentClaimsHaveOneWinner() throws Exception {
        tasks.enqueue(1, 1, "GENERATE");
        long id = taskId();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var futures =
                    List.of(
                            executor.submit(
                                    () -> {
                                        start.await();
                                        return tasks.claim(id, "a");
                                    }),
                            executor.submit(
                                    () -> {
                                        start.await();
                                        return tasks.claim(id, "b");
                                    }));
            start.countDown();
            int winners = 0;
            for (var f : futures) if (f.get() != null) winners++;
            assertEquals(1, winners);
        }
    }

    @Test
    void newerVersionFencesOldPublicationAndToken() {
        var old = claim();
        jdbc.update("UPDATE app SET currentVersion=2 WHERE id=1");
        files(1, 2, Map.of("index.html", "<h1>new</h1>"));
        tasks.enqueue(1, 2, "GENERATE");
        assertFalse(tasks.publish(old));
        assertThrows(BusinessException.class, () -> tasks.render(old.id(), old.renderToken()));
        assertTrue(tasks.publish(tasks.claim(taskId(), "new")));
        assertEquals(2, tasks.status(1, owner).get("version"));
    }

    @Test
    void manualModeFencesActiveAttemptAndRetainsCustomCover() {
        var a = claim();
        tasks.setMode(1, "MANUAL", "https://example.com/manual.jpg", owner);
        assertFalse(tasks.publish(a));
        assertThrows(BusinessException.class, () -> tasks.render(a.id(), a.renderToken()));
        tasks.enqueue(1, 1, "DEPLOY");
        assertEquals(
                "https://example.com/manual.jpg",
                jdbc.queryForObject("SELECT cover FROM app WHERE id=1", String.class));
    }

    @Test
    void modeAndRegenerationRequireOwnerAndPrivateImageIsProtected() {
        assertThrows(BusinessException.class, () -> tasks.regenerate(1, 1, "request-123", other));
        assertThrows(BusinessException.class, () -> tasks.status(1, other));
        assertThrows(BusinessException.class, () -> tasks.setMode(1, "MANUAL", "", other));
        var a = claim();
        tasks.publish(a);
        assertThrows(BusinessException.class, () -> tasks.readableKey(1, null));
        assertEquals(a.objectKey(), tasks.readableKey(1, owner));
        jdbc.update("UPDATE app SET visibility='PUBLIC' WHERE id=1");
        assertEquals(a.objectKey(), tasks.readableKey(1, null));
    }

    @Test
    void rendererTokenIsHashedAndExpires() {
        var a = claim();
        assertEquals(AppCoverTasks.hash(a.renderToken()), task().get("renderTokenHash"));
        assertNotEquals(a.renderToken(), task().get("renderTokenHash"));
        assertTrue(tasks.render(a.id(), a.renderToken()).html().contains("cover-content"));
        advance(121);
        assertThrows(BusinessException.class, () -> tasks.render(a.id(), a.renderToken()));
    }

    @Test
    void retriesAreBoundedAndKeepOldCover() {
        var a = claim();
        tasks.failed(a, "NETWORK", true);
        assertEquals("RETRY_WAIT", task().get("status"));
        assertNull(tasks.claim(taskId(), "early"));
        advance(30);
        tasks.failed(tasks.claim(taskId(), "second"), "NETWORK", true);
        advance(120);
        tasks.failed(tasks.claim(taskId(), "third"), "NETWORK", true);
        assertEquals("FAILED", task().get("status"));
        assertEquals(3, ((Number) task().get("attempts")).intValue());
        assertEquals(
                "https://old.example/cover.jpg",
                jdbc.queryForObject("SELECT cover FROM app WHERE id=1", String.class));
    }

    @Test
    void permanentFailureDoesNotRetry() {
        tasks.failed(claim(), "COS_403", false);
        advance(999);
        assertTrue(tasks.due(20).isEmpty());
        assertEquals("FAILED", task().get("status"));
    }

    @Test
    void expiredLeaseCannotPublishAndIsRecovered() {
        var old = claim();
        advance(121);
        tasks.recover();
        assertEquals("RETRY_WAIT", task().get("status"));
        var fresh = tasks.claim(taskId(), "new");
        assertFalse(tasks.publish(old));
        assertFalse(tasks.renew(old));
        assertTrue(tasks.publish(fresh));
    }

    @Test
    void manualRequestIsIdempotentAndRateLimited() {
        var first = tasks.regenerate(1, 1, "request-abc", owner);
        assertEquals(first, tasks.regenerate(1, 1, "request-abc", owner));
        assertThrows(BusinessException.class, () -> tasks.regenerate(1, 1, "request-def", owner));
        advance(31);
        assertNotEquals(first, tasks.regenerate(1, 1, "request-def", owner));
    }

    @Test
    void deleteCancelsAndNeverResurrects() {
        var a = claim();
        tx.executeWithoutResult(
                s -> {
                    tasks.deleted(1);
                    jdbc.update("UPDATE app SET isDelete=1 WHERE id=1");
                });
        assertFalse(tasks.publish(a));
        assertThrows(BusinessException.class, () -> tasks.render(a.id(), a.renderToken()));
        assertEquals("CANCELLED", task().get("status"));
    }

    @Test
    void candidateCleanupIsDurableAndActiveObjectsAreProtected() {
        var a = claim();
        assertTrue(tasks.referenced(a.objectKey()));
        assertEquals(
                1, jdbc.queryForObject("SELECT COUNT(*) FROM app_cover_cleanup", Integer.class));
        tasks.failed(a, "FAILED", false);
        advance(86401);
        assertFalse(tasks.referenced(a.objectKey()));
        assertEquals(1, tasks.cleanupDue().size());
    }

    @Test
    void missingTasksAreRepairedButFailedTasksDoNotLoop() {
        tasks.repairMissing();
        long id = taskId();
        tasks.failed(tasks.claim(id, "test"), "COS_403", false);
        tasks.repairMissing();
        assertEquals(id, taskId());
        assertEquals("FAILED", task().get("status"));
    }

    @Test
    void imageUrlChangesOnlyAfterPublishAndIsIgnoredByMetadataRoundTrip() {
        var a = claim();
        tasks.publish(a);
        AppVO vo = new AppVO();
        vo.setId(1L);
        tasks.decorate(List.of(vo));
        String url = vo.getCover();
        assertTrue(tasks.isCurrentImageUrl(1, url));
        assertFalse(tasks.isCurrentImageUrl(2, url));
        tasks.regenerate(1, 1, "request-abc", owner);
        tasks.decorate(List.of(vo));
        assertEquals(url, vo.getCover());
        tasks.publish(tasks.claim(taskId(), "second"));
        tasks.decorate(List.of(vo));
        assertNotEquals(url, vo.getCover());
    }

    @Test
    void disabledFeatureDoesNotRequireNewTables() {
        config.setEnabled(false);
        jdbc.execute("DROP TABLE app_cover_task");
        tasks.validateConfiguration();
        tasks.enqueue(1, 1, "GENERATE");
        tasks.decorate(List.of(new AppVO()));
        assertEquals(false, tasks.status(1, owner).get("enabled"));
    }

    @Test
    void badConfigurationFailsAtStartup() {
        config.setLeaseSeconds(1);
        assertThrows(IllegalStateException.class, tasks::validateConfiguration);
    }

    @Test
    void switchingToAutomaticIsIdempotentAndDoesNotBypassManualRateLimit() {
        var a = claim();
        tasks.setMode(1, "AUTO", null, owner);
        assertEquals(a.id(), taskId());
        tasks.setMode(1, "MANUAL", "", owner);
        tasks.setMode(1, "AUTO", null, owner);
        long next = taskId();
        tasks.setMode(1, "AUTO", null, owner);
        assertEquals(next, taskId());
        tasks.setMode(1, "MANUAL", "", owner);
        assertThrows(BusinessException.class, () -> tasks.setMode(1, "AUTO", null, owner));
        assertEquals("MANUAL", tasks.status(1, owner).get("mode"));
        advance(31);
        tasks.setMode(1, "AUTO", null, owner);
        assertNotEquals(next, taskId());
    }
}
