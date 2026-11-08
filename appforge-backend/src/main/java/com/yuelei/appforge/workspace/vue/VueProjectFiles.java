package com.yuelei.appforge.workspace.vue;

import cn.hutool.json.JSONUtil;

import com.yuelei.appforge.exception.*;

import java.util.*;

/** 工程约定同时用于工具写入、版本入库及源码下载。 */
public final class VueProjectFiles {
    private VueProjectFiles() {}

    public static final int MAX_FILES = 29;
    public static final int MAX_SOURCE = 100000;
    public static final String PACKAGE =
            """
            {"name":"generated-vue-app","version":"1.0.0","private":true,"type":"module",
            "engines":{"node":">=18"},"scripts":{"dev":"vite","build":"vite build"},
            "dependencies":{"vue":"3.3.4","vue-router":"4.2.4"},
            "devDependencies":{"@vitejs/plugin-vue":"4.2.3","vite":"4.4.5"}}
            """;
    public static final String CONFIG =
            """
            import { defineConfig } from 'vite'
            import vue from '@vitejs/plugin-vue'
            import { fileURLToPath, URL } from 'node:url'
            export default defineConfig({
              base: './', plugins: [vue()],
              resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
              build: { assetsInlineLimit: 2000000, cssCodeSplit: false,
                rollupOptions: { output: { inlineDynamicImports: true } } }
            })
            """;

    public static boolean isProject(Map<String, String> files) {
        return files.containsKey("package.json");
    }

    public static String sourcePath(String path) {
        ThrowUtils.throwIf(
                path == null
                        || path.length() > 160
                        || !path.matches("[A-Za-z0-9_./-]+")
                        || path.startsWith("/")
                        || Arrays.stream(path.split("/", -1))
                                .anyMatch(
                                        p ->
                                                p.isEmpty()
                                                        || p.equals(".")
                                                        || p.equals("..")
                                                        || p.startsWith(".")),
                ErrorCode.PARAMS_ERROR,
                "文件路径无效");
        for (String part : path.split("/")) {
            String stem = part.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
            ThrowUtils.throwIf(
                    stem.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"),
                    ErrorCode.PARAMS_ERROR,
                    "文件名无效");
        }
        boolean allowed =
                Set.of("index.html", "package.json", "vite.config.js").contains(path)
                        || path.matches(
                                "src/(?:[A-Za-z0-9_-]+/)*[A-Za-z0-9_.-]+\\.(?:vue|js|css|json|svg)");
        ThrowUtils.throwIf(!allowed, ErrorCode.PARAMS_ERROR, "该文件不属于可生成的工程文件");
        return path;
    }

    public static boolean runtimePath(String name) {
        return name.equals("dist/index.html")
                || name.matches("dist/assets/[A-Za-z0-9_.-]+\\.(?:js|css|svg)")
                || name.matches("dist/[A-Za-z0-9_-]+\\.(?:svg|json|txt)");
    }

    public static String normalize(String name, String text) {
        sourcePath(name);
        ThrowUtils.throwIf(
                text == null || text.length() > MAX_SOURCE || text.indexOf('\0') >= 0,
                ErrorCode.PARAMS_ERROR,
                "文件内容无效或过长");
        // 依赖和构建入口由平台固定，模型不能新增生命周期脚本或执行服务器命令。
        if (name.equals("package.json")) {
            JSONUtil.parseObj(text);
            return PACKAGE;
        }
        return name.equals("vite.config.js") ? CONFIG : text;
    }

    public static Map<String, String> sources(Map<String, String> files) {
        Map<String, String> result = new LinkedHashMap<>();
        files.forEach(
                (name, text) -> {
                    if (!name.startsWith("dist/")) result.put(name, text);
                });
        return result;
    }

    public static void validateSources(Map<String, String> files) {
        ThrowUtils.throwIf(
                files.size() >= 30
                        || files.values().stream().mapToLong(String::length).sum() > MAX_SOURCE,
                ErrorCode.OPERATION_ERROR,
                "工程超出文件数量或内容预算");
        files.keySet().forEach(VueProjectFiles::sourcePath);
        for (String required :
                List.of(
                        "index.html",
                        "package.json",
                        "vite.config.js",
                        "src/main.js",
                        "src/App.vue",
                        "src/router/index.js"))
            ThrowUtils.throwIf(
                    files.getOrDefault(required, "").isBlank(),
                    ErrorCode.OPERATION_ERROR,
                    "工程缺少必要文件：" + required);
        ThrowUtils.throwIf(
                !PACKAGE.equals(files.get("package.json"))
                        || !CONFIG.equals(files.get("vite.config.js")),
                ErrorCode.OPERATION_ERROR,
                "工程构建配置无效");
        String router = files.get("src/router/index.js");
        ThrowUtils.throwIf(
                !router.matches("(?s).*createWebHashHistory\\s*\\(.*")
                        || router.matches("(?s).*createWebHistory\\s*\\(.*"),
                ErrorCode.OPERATION_ERROR,
                "工程必须使用 hash 路由");
    }

    public static void validate(Map<String, String> files) {
        validateSources(sources(files));
        ThrowUtils.throwIf(
                files.getOrDefault("dist/index.html", "").isBlank(),
                ErrorCode.OPERATION_ERROR,
                "工程尚未构建成功");
        long bytes = 0;
        for (var e : files.entrySet())
            if (e.getKey().startsWith("dist/")) {
                ThrowUtils.throwIf(
                        !runtimePath(e.getKey()), ErrorCode.OPERATION_ERROR, "构建产物包含不支持的文件");
                bytes += e.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            }
        ThrowUtils.throwIf(
                bytes > 3000000 || files.size() > 80, ErrorCode.OPERATION_ERROR, "构建产物过大");
    }
}
