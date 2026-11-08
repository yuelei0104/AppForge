package com.yuelei.appforge.workspace;

import com.yuelei.appforge.constant.AppConstant;
import com.yuelei.appforge.exception.*;
import com.yuelei.appforge.model.enums.CodeGenTypeEnum;
import com.yuelei.appforge.workspace.vue.VueProjectFiles;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** 应用和版本目录由服务端标识构造，原生文件和工程路径分别校验。 */
@Component
public class AppFileStore {
    public static final Set<String> FILES = Set.of("index.html", "style.css", "script.js");

    @Value("${appforge.workspace-root:}")
    private String configuredRoot;

    private Path root() {
        return configuredRoot == null || configuredRoot.isBlank()
                ? Path.of(AppConstant.PROJECT_ROOT_DIR, "tmp")
                : Path.of(configuredRoot);
    }

    public Path versionDir(long appId, int version) {
        return root().resolve("code_versions")
                .resolve(String.valueOf(appId))
                .resolve("v" + version);
    }

    public Map<String, String> legacyFiles(long appId, String type, String deployKey) {
        if (CodeGenTypeEnum.getEnumByValue(type) == null) return Map.of();
        Path dir =
                deployKey == null
                        ? root().resolve("code_output").resolve(type + "_" + appId)
                        : deploymentDir(deployKey);
        Map<String, String> files = new LinkedHashMap<>();
        try {
            for (String name : FILES) {
                Path file = dir.resolve(name);
                if (Files.isRegularFile(file) && !Files.isSymbolicLink(file)) {
                    ThrowUtils.throwIf(
                            Files.size(file) > 2_000_000, ErrorCode.OPERATION_ERROR, "代码文件过大");
                    files.put(name, Files.readString(file));
                }
            }
            return files;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取代码失败");
        }
    }

    public void writeVersion(long appId, int version, Map<String, String> files) {
        Path target = versionDir(appId, version);
        try {
            Files.createDirectories(target);
            for (var entry : files.entrySet()) {
                if (VueProjectFiles.isProject(files)) {
                    if (!VueProjectFiles.runtimePath(entry.getKey()))
                        VueProjectFiles.sourcePath(entry.getKey());
                } else
                    ThrowUtils.throwIf(
                            !FILES.contains(entry.getKey()), ErrorCode.PARAMS_ERROR, "不支持的代码文件");
                Files.createDirectories(target.resolve(entry.getKey()).getParent());
                Files.writeString(
                        target.resolve(entry.getKey()),
                        entry.getValue(),
                        StandardOpenOption.CREATE_NEW);
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存版本文件失败");
        }
    }

    public void removeVersion(long appId, int version) {
        removeTree(versionDir(appId, version));
    }

    private Path deploymentDir(String key) {
        ThrowUtils.throwIf(
                key == null || !key.matches("[A-Za-z0-9]{1,64}"), ErrorCode.PARAMS_ERROR, "部署标识无效");
        return root().resolve("code_deploy").resolve(key);
    }

    public void cleanup(long appId, String type, String deployKey) {
        removeTree(root().resolve("code_versions").resolve(String.valueOf(appId)));
        for (CodeGenTypeEnum value : CodeGenTypeEnum.values())
            removeTree(root().resolve("code_output").resolve(value.getValue() + "_" + appId));
        if (deployKey != null && !deployKey.isBlank()) removeTree(deploymentDir(deployKey));
    }

    private void removeTree(Path dir) {
        if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件清理失败，将自动重试");
        }
    }
}
