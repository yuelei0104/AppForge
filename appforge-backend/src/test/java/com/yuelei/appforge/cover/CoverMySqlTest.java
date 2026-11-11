package com.yuelei.appforge.cover;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.concurrent.*;

import javax.sql.DataSource;

/** Opt-in destructive fixture, restricted to a dedicated loopback-only test database. */
@EnabledIfEnvironmentVariable(named = "APPFORGE_COVER_MYSQL_TEST", matches = "true")
class CoverMySqlTest extends CoverTestDatabase {
    @Override
    DataSource dataSource() {
        String url = System.getenv("APPFORGE_COVER_MYSQL_URL");
        if (url == null
                || !url.matches(
                        "jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/appforge_cover_test(?:\\?.*)?"))
            throw new IllegalStateException(
                    "Only the dedicated local cover test database is permitted");
        var ds =
                new DriverManagerDataSource(
                        url, "root", System.getenv("APPFORGE_COVER_MYSQL_PASSWORD"));
        var db = new JdbcTemplate(ds);
        for (String table :
                List.of("app_cover_cleanup", "app_cover_task", "app_version", "app", "user"))
            db.execute("DROP TABLE IF EXISTS `" + table + "`");
        return ds;
    }

    @Test
    void mysqlMigrationDatesLeasesAndFencedPublication() {
        tasks.validateConfiguration();
        var first = claim();
        assertNotNull(tasks.render(first.id(), first.renderToken()));
        advance(121);
        tasks.recover();
        var second = tasks.claim(taskId(), "recovered");
        assertNotNull(second);
        assertFalse(tasks.publish(first));
        assertTrue(tasks.publish(second));
        assertEquals("SUCCEEDED", tasks.status(1, owner).get("status"));
    }

    @Test
    void mysqlRowLockAllowsOnlyOneClaim() throws Exception {
        tasks.enqueue(1, 1, "GENERATE");
        long id = taskId();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var latch = new CountDownLatch(1);
            var a =
                    pool.submit(
                            () -> {
                                latch.await();
                                return tasks.claim(id, "a");
                            });
            var b =
                    pool.submit(
                            () -> {
                                latch.await();
                                return tasks.claim(id, "b");
                            });
            latch.countDown();
            assertEquals(1, (a.get() == null ? 0 : 1) + (b.get() == null ? 0 : 1));
        }
    }
}
