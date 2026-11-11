package com.yuelei.appforge.cover;

import com.qcloud.cos.exception.CosServiceException;

import jakarta.annotation.PreDestroy;

import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Durable database queue plus bounded, single-browser execution. */
@Component
@Slf4j
public class AppCoverWorker implements AutoCloseable {
    private final AppCoverTasks tasks;
    private final CoverProperties config;
    private final CoverRenderer renderer;
    private final CoverObjectStore objects;
    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService supervisor =
            Executors.newScheduledThreadPool(2, r -> daemon(r, "cover-supervisor"));
    private final ExecutorService maintenance =
            Executors.newSingleThreadExecutor(r -> daemon(r, "cover-maintenance"));
    private final AtomicBoolean maintaining = new AtomicBoolean();
    private final Set<Long> queued = ConcurrentHashMap.newKeySet();
    private final Set<CoverRenderer.Session> sessions = ConcurrentHashMap.newKeySet();
    private final String workerId = UUID.randomUUID().toString();
    private volatile boolean paused, closed;

    public AppCoverWorker(
            AppCoverTasks tasks,
            CoverProperties config,
            CoverRenderer renderer,
            CoverObjectStore objects) {
        this.tasks = tasks;
        this.config = config;
        this.renderer = renderer;
        this.objects = objects;
        executor =
                new ThreadPoolExecutor(
                        1,
                        1,
                        0,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(Math.clamp(config.getQueueCapacity(), 1, 200)),
                        r -> daemon(r, "cover-worker"),
                        new ThreadPoolExecutor.AbortPolicy());
    }

    private static Thread daemon(Runnable r, String name) {
        Thread thread = new Thread(r, name);
        thread.setDaemon(true);
        return thread;
    }

    public boolean paused() {
        return paused;
    }

    public void resume() {
        paused = false;
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    public void dispatch() {
        if (!tasks.enabled() || paused || closed) return;
        for (long id : tasks.due(config.getQueueCapacity() + 1)) {
            if (!queued.add(id)) continue;
            try {
                executor.execute(
                        () -> {
                            try {
                                execute(id);
                            } finally {
                                queued.remove(id);
                            }
                        });
            } catch (RejectedExecutionException e) {
                queued.remove(id);
                break;
            }
        }
    }

    void execute(long id) {
        var attempt = tasks.claim(id, workerId);
        if (attempt == null) return;
        var session = new CoverRenderer.Session();
        sessions.add(session);
        AtomicBoolean expired = new AtomicBoolean();
        Path directory = null;
        ScheduledFuture<?> timeout = null, heartbeat = null;
        try {
            timeout =
                    supervisor.schedule(
                            () -> {
                                expired.set(true);
                                session.close();
                                tasks.failed(attempt, "TIMEOUT", true);
                            },
                            config.getAttemptTimeoutSeconds(),
                            TimeUnit.SECONDS);
            heartbeat =
                    supervisor.scheduleAtFixedRate(
                            () -> {
                                try {
                                    if (!tasks.renew(attempt)) {
                                        expired.set(true);
                                        session.close();
                                    }
                                } catch (RuntimeException e) {
                                    expired.set(true);
                                    session.close();
                                }
                            },
                            15,
                            15,
                            TimeUnit.SECONDS);
            Path root = Path.of(config.getTempRoot()).toAbsolutePath().normalize();
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root)) throw new IllegalStateException("截图临时目录不能是链接");
            directory = Files.createDirectory(root.resolve(UUID.randomUUID().toString()));
            Files.writeString(directory.resolve(".appforge-cover-task"), Long.toString(id));
            URI uri =
                    URI.create(
                            config.getBaseUrl().replaceAll("/+$", "")
                                    + "/internal/cover/render/"
                                    + id
                                    + "?token="
                                    + attempt.renderToken());
            Path image =
                    renderer.render(
                            uri, id + ":" + attempt.execution(), directory, config, session);
            session.close();
            if (expired.get()) return;
            objects.upload(attempt.objectKey(), image);
            if (!expired.get()) tasks.publish(attempt);
        } catch (Exception e) {
            boolean retryable = true;
            String code = "SCREENSHOT_FAILED";
            if (e instanceof CosServiceException cos) {
                code = "COS_" + cos.getStatusCode();
                retryable = cos.getStatusCode() >= 500;
                if (cos.getStatusCode() == 403) paused = true;
            }
            if (!expired.get()) tasks.failed(attempt, code, retryable);
            log.warn(
                    "封面任务失败 taskId={}, appId={}, version={}, code={}",
                    id,
                    attempt.appId(),
                    attempt.version(),
                    code);
        } finally {
            if (timeout != null) timeout.cancel(false);
            if (heartbeat != null) heartbeat.cancel(false);
            session.close();
            sessions.remove(session);
            if (directory != null) removeDirectory(directory);
        }
    }

    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    public void recover() {
        if (tasks.enabled() && !closed) tasks.recover();
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 15000)
    public void maintain() {
        if (!tasks.enabled() || closed || !maintaining.compareAndSet(false, true)) return;
        try {
            maintenance.execute(
                    () -> {
                        try {
                            maintainOnce();
                        } catch (RuntimeException e) {
                            log.warn("封面维护暂时失败");
                        } finally {
                            maintaining.set(false);
                        }
                    });
        } catch (RejectedExecutionException e) {
            maintaining.set(false);
        }
    }

    void maintainOnce() {
        if (!paused) tasks.repairMissing();
        for (var row : tasks.cleanupDue()) {
            long id = AppCoverTasks.n(row, "id");
            String key = (String) row.get("objectKey");
            try {
                if (tasks.referenced(key)) {
                    tasks.cleanupFailed(id);
                    continue;
                }
                objects.delete(key);
                tasks.cleaned(id);
            } catch (RuntimeException e) {
                tasks.cleanupFailed(id);
            }
        }
        tasks.retainHistory();
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Shanghai")
    public void cleanupTemp() {
        if (!tasks.enabled() || !sessions.isEmpty()) return;
        Path root = Path.of(config.getTempRoot()).toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root)) return;
        try (var paths = Files.list(root)) {
            paths.filter(p -> p.getFileName().toString().matches("[a-f0-9-]{36}"))
                    .filter(
                            p ->
                                    !Files.isSymbolicLink(p)
                                            && Files.isRegularFile(
                                                    p.resolve(".appforge-cover-task"),
                                                    LinkOption.NOFOLLOW_LINKS))
                    .filter(
                            p -> {
                                try {
                                    return Files.getLastModifiedTime(p)
                                            .toInstant()
                                            .isBefore(Instant.now().minus(Duration.ofHours(24)));
                                } catch (Exception e) {
                                    return false;
                                }
                            })
                    .forEach(AppCoverWorker::removeDirectory);
        } catch (Exception e) {
            log.warn("清理截图临时目录失败");
        }
    }

    static void removeDirectory(Path root) {
        if (Files.isSymbolicLink(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(
                            p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (Exception ignored) {
                                }
                            });
        } catch (Exception ignored) {
        }
    }

    @Override
    @PreDestroy
    public void close() {
        closed = true;
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        sessions.forEach(CoverRenderer.Session::close);
        executor.shutdownNow();
        supervisor.shutdownNow();
        maintenance.shutdownNow();
    }
}
