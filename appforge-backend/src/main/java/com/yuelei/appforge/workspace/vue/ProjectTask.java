package com.yuelei.appforge.workspace.vue;

import com.yuelei.appforge.exception.*;
import com.yuelei.appforge.workspace.GenerationProgress;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** 每次调用持有独立工作目录，模型只能操作其源码，不能接触其他应用或构建目录。 */
public final class ProjectTask implements AutoCloseable {
    final Path root;
    final AtomicBoolean cancelled = new AtomicBoolean();
    final AtomicReference<Process> process = new AtomicReference<>();
    final AtomicReference<String> container = new AtomicReference<>();
    private final Map<String, String> files = new LinkedHashMap<>();
    private final Set<String> read = new HashSet<>();
    private final Instant deadline = Instant.now().plus(Duration.ofMinutes(8));
    private final Consumer<GenerationProgress> progress;
    private boolean listed, sealed;
    private int writes;
    private Map<String, String> result;

    public ProjectTask(Map<String, String> previous, Consumer<GenerationProgress> progress) {
        this.progress = progress;
        root = createRoot();
        try {
            for (var e : VueProjectFiles.sources(previous).entrySet()) {
                String name = VueProjectFiles.sourcePath(e.getKey());
                String text = VueProjectFiles.normalize(name, e.getValue());
                files.put(name, text);
                writeDisk(name, text);
            }
        } catch (Exception error) {
            close();
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "无法创建工程工作目录");
        }
    }

    private static Path createRoot() {
        try {
            return Files.createTempDirectory("appforge-vue-").toAbsolutePath();
        } catch (java.io.IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "无法创建工程工作目录");
        }
    }

    public void check() {
        if (cancelled.get())
            throw new com.yuelei.appforge.workspace.AppGenerationService
                    .GenerationStoppedException();
        ThrowUtils.throwIf(Instant.now().isAfter(deadline), ErrorCode.OPERATION_ERROR, "工程生成超时");
    }

    private final AtomicReference<dev.langchain4j.model.chat.response.StreamingHandle> streaming =
            new AtomicReference<>();

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void streaming(dev.langchain4j.model.chat.response.StreamingHandle handle) {
        streaming.set(handle);
        if (cancelled.get()) {
            var active = streaming.getAndSet(null);
            if (active != null) active.cancel();
        }
    }

    public synchronized String source(String path) {
        check();
        VueProjectFiles.sourcePath(path);
        return files.get(path);
    }

    public void event(String id, String operation, String path, String state, String message) {
        check();
        progress.accept(new GenerationProgress(id, operation, path, state, message));
    }

    public synchronized String directory() {
        check();
        listed = true;
        return cn.hutool.json.JSONUtil.toJsonStr(
                Map.of(
                        "files",
                        files.keySet(),
                        "packageJson",
                        VueProjectFiles.PACKAGE,
                        "viteConfig",
                        VueProjectFiles.CONFIG));
    }

    public synchronized String read(String name) {
        check();
        VueProjectFiles.sourcePath(name);
        ThrowUtils.throwIf(!files.containsKey(name), ErrorCode.PARAMS_ERROR, "文件不存在");
        read.add(name);
        return files.get(name);
    }

    private void editing(String name) {
        check();
        VueProjectFiles.sourcePath(name);
        ThrowUtils.throwIf(sealed, ErrorCode.OPERATION_ERROR, "工程已进入构建阶段");
        ThrowUtils.throwIf(
                files.containsKey(name) && (!listed || !read.contains(name)),
                ErrorCode.PARAMS_ERROR,
                "修改现有文件前必须读取目录和该文件");
    }

    public synchronized String write(String name, String content) {
        editing(name);
        String text = VueProjectFiles.normalize(name, content);
        int count = files.size() + (files.containsKey(name) ? 0 : 1);
        long size =
                files.values().stream().mapToLong(String::length).sum()
                        - files.getOrDefault(name, "").length()
                        + text.length();
        ThrowUtils.throwIf(
                count > VueProjectFiles.MAX_FILES || size > VueProjectFiles.MAX_SOURCE,
                ErrorCode.PARAMS_ERROR,
                "工程超出文件数量或内容预算");
        writeDisk(name, text);
        files.put(name, text);
        writes++;
        return "文件已保存";
    }

    public synchronized String modify(String name, String before, String after) {
        editing(name);
        String old = files.get(name);
        ThrowUtils.throwIf(
                old == null
                        || before == null
                        || before.isEmpty()
                        || after == null
                        || old.indexOf(before) < 0
                        || old.indexOf(before) != old.lastIndexOf(before),
                ErrorCode.PARAMS_ERROR,
                "待替换文本必须在文件中唯一匹配");
        return write(name, old.replace(before, after));
    }

    public synchronized String delete(String name) {
        editing(name);
        ThrowUtils.throwIf(!files.containsKey(name), ErrorCode.PARAMS_ERROR, "文件不存在");
        String fileName = Path.of(name).getFileName().toString().toLowerCase(Locale.ROOT);
        ThrowUtils.throwIf(Set.of("package.json", "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "vite.config.js", "vite.config.ts", "vue.config.js", "tsconfig.json", "tsconfig.app.json", "tsconfig.node.json", "index.html", "main.js", "main.ts", "app.vue", ".gitignore", "readme.md").contains(fileName), ErrorCode.PARAMS_ERROR, "不允许删除重要文件");
        try {
            Files.delete(root.resolve(name));
            files.remove(name);
            writes++;
            return "文件已删除";
        } catch (java.io.IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "删除文件失败");
        }
    }

    private void writeDisk(String name, String text) {
        Path target = root.resolve(name).normalize();
        ThrowUtils.throwIf(!target.startsWith(root), ErrorCode.PARAMS_ERROR, "文件路径无效");
        try {
            Files.createDirectories(target.getParent());
            for (Path p = target; p != null && p.startsWith(root); p = p.getParent())
                ThrowUtils.throwIf(Files.isSymbolicLink(p), ErrorCode.PARAMS_ERROR, "不允许符号链接");
            Files.writeString(target, text);
        } catch (java.io.IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "写入文件失败");
        }
    }

    public synchronized Map<String, String> seal() {
        check();
        ThrowUtils.throwIf(writes == 0, ErrorCode.OPERATION_ERROR, "模型未通过文件工具保存工程");
        VueProjectFiles.validateSources(files);
        sealed = true;
        return Map.copyOf(files);
    }

    public synchronized void complete(Map<String, String> runtime) {
        check();
        Map<String, String> combined = new LinkedHashMap<>(files);
        combined.putAll(runtime);
        VueProjectFiles.validate(combined);
        result = Map.copyOf(combined);
    }

    public synchronized Map<String, String> result() {
        check();
        ThrowUtils.throwIf(result == null, ErrorCode.OPERATION_ERROR, "工程尚未构建完成");
        return result;
    }

    public void cancel() {
        cancelled.set(true);
        var handle = streaming.getAndSet(null);
        if (handle != null) handle.cancel();
        Process active = process.get();
        if (active != null) active.destroyForcibly();
    }

    @Override
    public synchronized void close() {
        cancel();
        try (var paths = Files.walk(root)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (java.io.IOException ignored) {
            /* 已停止的临时构建目录不作为预览或发布来源。 */
        }
    }
}
