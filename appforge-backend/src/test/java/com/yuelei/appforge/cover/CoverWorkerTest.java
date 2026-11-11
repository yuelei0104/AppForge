package com.yuelei.appforge.cover;

import static org.junit.jupiter.api.Assertions.*;

import com.qcloud.cos.exception.CosServiceException;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class CoverWorkerTest extends CoverTestDatabase {
    @TempDir Path directory;

    @Test
    void boundedQueueRunsOneBrowserAndDoesNotLoseDatabaseTasks() throws Exception {
        for (int i = 2; i <= 4; i++) addApp(i);
        for (int i = 1; i <= 4; i++) tasks.enqueue(i, 1, "GENERATE");
        config.setQueueCapacity(1);
        config.setTempRoot(directory.toString());
        AtomicInteger active = new AtomicInteger(),
                maximum = new AtomicInteger(),
                uploads = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        var renderer =
                new CoverRenderer() {
                    @Override
                    public Path render(
                            URI uri, String marker, Path dir, CoverProperties p, Session session)
                            throws Exception {
                        assertEquals("cover-worker", Thread.currentThread().getName());
                        maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                        entered.countDown();
                        release.await(5, TimeUnit.SECONDS);
                        Path image = dir.resolve("cover.jpg");
                        Files.write(image, new byte[] {1});
                        active.decrementAndGet();
                        return image;
                    }
                };
        var store =
                new CoverObjectStore() {
                    public void upload(String k, Path p) {
                        uploads.incrementAndGet();
                    }

                    public byte[] read(String k) {
                        return new byte[0];
                    }

                    public void delete(String k) {}
                };
        try (var worker = new AppCoverWorker(tasks, config, renderer, store)) {
            worker.dispatch();
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            worker.dispatch();
            assertEquals(1, maximum.get());
            assertTrue(tasks.due(20).size() >= 2);
            release.countDown();
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (uploads.get() < 4 && System.nanoTime() < end) {
                worker.dispatch();
                Thread.sleep(20);
            }
            assertEquals(4, uploads.get());
            assertEquals(1, maximum.get());
        }
        try (var paths = Files.list(directory)) {
            assertEquals(0, paths.count());
        }
    }

    @Test
    void cosPermissionErrorPausesQueueAndAllowsExplicitResume() throws Exception {
        config.setTempRoot(directory.toString());
        tasks.enqueue(1, 1, "GENERATE");
        var renderer =
                new CoverRenderer() {
                    public Path render(URI u, String m, Path d, CoverProperties p, Session s)
                            throws Exception {
                        Path image = d.resolve("cover.jpg");
                        Files.write(image, new byte[] {1});
                        return image;
                    }
                };
        var store =
                new CoverObjectStore() {
                    public void upload(String k, Path p) {
                        var error = new CosServiceException("denied");
                        error.setStatusCode(403);
                        throw error;
                    }

                    public byte[] read(String k) {
                        return null;
                    }

                    public void delete(String k) {}
                };
        try (var worker = new AppCoverWorker(tasks, config, renderer, store)) {
            worker.execute(taskId());
            assertTrue(worker.paused());
            assertEquals("FAILED", task().get("status"));
            assertEquals(
                    "https://old.example/cover.jpg",
                    jdbc.queryForObject("SELECT cover FROM app WHERE id=1", String.class));
            worker.resume();
            assertFalse(worker.paused());
        }
    }

    @Test
    void failedObjectDeletionRemainsDurable() {
        var a = claim();
        tasks.failed(a, "FAILED", false);
        advance(86401);
        var store =
                new CoverObjectStore() {
                    public void upload(String k, Path p) {}

                    public byte[] read(String k) {
                        return null;
                    }

                    public void delete(String k) {
                        throw new IllegalStateException();
                    }
                };
        try (var worker = new AppCoverWorker(tasks, config, new CoverRenderer(), store)) {
            worker.maintainOnce();
            assertEquals(
                    1,
                    jdbc.queryForObject("SELECT attempts FROM app_cover_cleanup", Integer.class));
            assertTrue(tasks.cleanupDue().isEmpty());
        }
    }
}
