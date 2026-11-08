package com.yuelei.appforge.workspace.vue;

import com.yuelei.appforge.exception.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** 固定的构建流水线在独立容器中执行，不运行模型提供的宿主机命令。 */
@Component
public class VueProjectBuilder {
    @Value("${appforge.vue-build.image:node:20.19.0-bookworm-slim}")
    private String image;

    protected Process start(List<String> command, Path log) throws IOException {
        return new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
    }

    public Map<String, String> build(ProjectTask task) {
        task.seal();
        phase(
                task,
                "prepare",
                "准备构建环境",
                180,
                "bridge",
                List.of(
                        "npm",
                        "install",
                        "--ignore-scripts",
                        "--no-audit",
                        "--no-fund",
                        "--package-lock=false"));
        phase(task, "build", "构建网站", 90, "none", List.of("npm", "run", "build"));
        return collect(task);
    }

    private void phase(
            ProjectTask task,
            String operation,
            String message,
            int seconds,
            String network,
            List<String> action) {
        task.check();
        String name = "appforge-vue-" + UUID.randomUUID().toString().replace("-", "");
        task.container.set(name);
        task.event(operation, operation, "", "RUNNING", message);
        List<String> command =
                new ArrayList<>(
                        List.of(
                                "docker",
                                "run",
                                "--rm",
                                "--name",
                                name,
                                "--network",
                                network,
                                "--memory",
                                "512m",
                                "--cpus",
                                "1",
                                "--pids-limit",
                                "128",
                                "--cap-drop",
                                "ALL",
                                "--security-opt",
                                "no-new-privileges",
                                "--read-only",
                                "--tmpfs",
                                "/tmp:rw,nosuid,size=128m",
                                "--mount",
                                "type=bind,source=" + task.root + ",target=/project",
                                "--workdir",
                                "/project",
                                "--env",
                                "HOME=/tmp",
                                "--env",
                                "npm_config_cache=/tmp/npm-cache",
                                image));
        String user = "1000:1000";
        try {
            user =
                    Files.getAttribute(task.root, "unix:uid")
                            + ":"
                            + Files.getAttribute(task.root, "unix:gid");
        } catch (Exception unsupported) {
            /* Windows 目录由 Docker Desktop 映射。 */
        }
        command.addAll(command.size() - 1, List.of("--user", user));
        command.addAll(action);
        try {
            task.check();
            Process process = start(command, task.root.resolve(".build-" + operation + ".log"));
            task.process.set(process);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
            while (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                task.check();
                ThrowUtils.throwIf(
                        System.nanoTime() > deadline, ErrorCode.OPERATION_ERROR, "工程构建超时");
            }
            task.check();
            ThrowUtils.throwIf(
                    process.exitValue() != 0, ErrorCode.OPERATION_ERROR, "工程构建未通过，请重新生成或修改后重试");
            task.event(operation, operation, "", "SUCCEEDED", message + "已完成");
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "工程构建服务暂不可用");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "工程构建已中断");
        } finally {
            Process p = task.process.getAndSet(null);
            if (p != null && p.isAlive()) p.destroyForcibly();
            removeContainer(task);
        }
    }

    public void removeContainer(ProjectTask task) {
        String name = task.container.getAndSet(null);
        if (name == null) return;
        try {
            Process p =
                    new ProcessBuilder("docker", "rm", "-f", name)
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .redirectError(ProcessBuilder.Redirect.DISCARD)
                            .start();
            if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly();
        } catch (IOException ignored) {
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected Map<String, String> collect(ProjectTask task) {
        task.check();
        Path dist = task.root.resolve("dist");
        Map<String, String> result = new LinkedHashMap<>();
        long size = 0;
        try (var stream = Files.walk(dist)) {
            for (Path path : stream.toList()) {
                task.check();
                ThrowUtils.throwIf(
                        Files.isSymbolicLink(path), ErrorCode.OPERATION_ERROR, "构建产物不能包含符号链接");
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                String name = "dist/" + dist.relativize(path).toString().replace('\\', '/');
                ThrowUtils.throwIf(
                        !VueProjectFiles.runtimePath(name),
                        ErrorCode.OPERATION_ERROR,
                        "构建产物包含不支持的文件");
                size += Files.size(path);
                ThrowUtils.throwIf(
                        size > 3000000 || result.size() >= 50, ErrorCode.OPERATION_ERROR, "构建产物过大");
                result.put(name, Files.readString(path, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "工程未生成有效的构建产物");
        }
        ThrowUtils.throwIf(
                result.getOrDefault("dist/index.html", "").isBlank(),
                ErrorCode.OPERATION_ERROR,
                "工程未生成首页");
        return result;
    }
}
