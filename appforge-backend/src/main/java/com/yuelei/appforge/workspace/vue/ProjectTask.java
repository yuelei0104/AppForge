package com.yuelei.appforge.workspace.vue;

import com.yuelei.appforge.exception.*;
import com.yuelei.appforge.workspace.FileChangeEvent;
import com.yuelei.appforge.workspace.FileOperation;
import com.yuelei.appforge.workspace.GenerationMode;
import com.yuelei.appforge.workspace.GenerationProgress;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** 每次调用持有独立工作目录，模型只能操作其源码，不能接触其他应用或构建目录。 */
public final class ProjectTask implements AutoCloseable {
    /** 单个文件事件的内容上限；超过后由前端在 done 时从版本文件补全。 */
    public static final int MAX_FILE_EVENT_BYTES = 128 * 1024;

    final Path root;
    final AtomicBoolean cancelled = new AtomicBoolean();
    final AtomicReference<Process> process = new AtomicReference<>();
    final AtomicReference<String> container = new AtomicReference<>();
    private final Map<String, String> files = new LinkedHashMap<>();
    private final Set<String> read = new HashSet<>();
    private final Instant deadline = Instant.now().plus(Duration.ofMinutes(8));
    private final Consumer<GenerationProgress> progress;
    private final Consumer<FileChangeEvent> onFileChange;
    private final GenerationMode mode;
    private boolean listed, sealed;
    private int writes;
    private Map<String, String> result;

    public ProjectTask(Map<String, String> previous, Consumer<GenerationProgress> progress) {
        this(previous, progress, event -> {}, GenerationMode.EDIT);
    }

    public ProjectTask(
            Map<String, String> previous,
            Consumer<GenerationProgress> progress,
            Consumer<FileChangeEvent> onFileChange,
            GenerationMode mode) {
        this.progress = progress;
        this.onFileChange = onFileChange == null ? event -> {} : onFileChange;
        this.mode = mode == null ? GenerationMode.EDIT : mode;
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
        if (mode == GenerationMode.CREATE) {
            ThrowUtils.throwIf(
                    files.containsKey(name),
                    ErrorCode.PARAMS_ERROR,
                    "创建模式不能覆盖已有工程文件");
            return;
        }
        ThrowUtils.throwIf(
                files.containsKey(name) && (!listed || !read.contains(name)),
                ErrorCode.PARAMS_ERROR,
                "修改现有文件前必须读取目录和该文件");
    }

    public synchronized String write(String name, String content) {
        editing(name);
        boolean exists = files.containsKey(name);
        String text = VueProjectFiles.normalize(name, content);
        store(name, text);
        emitFile(name, exists ? FileOperation.UPDATE : FileOperation.CREATE, text);
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
        String text = VueProjectFiles.normalize(name, old.replace(before, after));
        store(name, text);
        emitFile(name, FileOperation.UPDATE, text);
        return "文件已保存";
    }

    public synchronized String delete(String name) {
        editing(name);
        ThrowUtils.throwIf(mode == GenerationMode.CREATE, ErrorCode.PARAMS_ERROR, "创建模式不能删除文件");
        ThrowUtils.throwIf(!files.containsKey(name), ErrorCode.PARAMS_ERROR, "文件不存在");
        String fileName = Path.of(name).getFileName().toString().toLowerCase(Locale.ROOT);
        ThrowUtils.throwIf(
                Set.of(
                                "package.json",
                                "package-lock.json",
                                "yarn.lock",
                                "pnpm-lock.yaml",
                                "vite.config.js",
                                "vite.config.ts",
                                "vue.config.js",
                                "tsconfig.json",
                                "tsconfig.app.json",
                                "tsconfig.node.json",
                                "index.html",
                                "main.js",
                                "main.ts",
                                "app.vue",
                                ".gitignore",
                                "readme.md")
                        .contains(fileName),
                ErrorCode.PARAMS_ERROR,
                "不允许删除重要文件");
        try {
            Files.delete(root.resolve(name));
            files.remove(name);
            writes++;
            emitFile(name, FileOperation.DELETE, null);
            return "文件已删除";
        } catch (java.io.IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "删除文件失败");
        }
    }

    /** 统一做预算校验、落盘和记账，避免 modify 复用 write 时重复发送事件。 */
    private void store(String name, String text) {
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
    }

    private void emitFile(String name, FileOperation operation, String content) {
        if (cancelled.get()) return;
        boolean truncated =
                content != null
                        && content.getBytes(StandardCharsets.UTF_8).length
                                > MAX_FILE_EVENT_BYTES;
        onFileChange.accept(
                new FileChangeEvent(name, operation, truncated ? null : content, truncated));
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
