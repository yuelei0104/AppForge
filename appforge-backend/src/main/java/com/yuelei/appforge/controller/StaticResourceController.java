package com.yuelei.appforge.controller;

import com.yuelei.appforge.exception.BusinessException;
import com.yuelei.appforge.model.entity.*;
import com.yuelei.appforge.service.UserService;
import com.yuelei.appforge.workspace.*;
import com.yuelei.appforge.workspace.vue.VueProjectFiles;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/** 资源访问也执行可见性、上下线、软删除检查，不能通过文件 URL 绕过权限。 */
@RestController
public class StaticResourceController {
    @jakarta.annotation.Resource private AppWorkspaceService workspace;
    @jakarta.annotation.Resource private UserService userService;

    @GetMapping({"/static/{appId}", "/static/{appId}/"})
    public ResponseEntity<Resource> getPreviewIndex(
            @PathVariable long appId, HttpServletRequest request) {
        return getPreviewResource(appId, "index.html", request);
    }

    @GetMapping("/static/{appId}/{fileName:.+}")
    public ResponseEntity<Resource> getPreviewResource(
            @PathVariable long appId, @PathVariable String fileName, HttpServletRequest request) {
        try {
            App app = workspace.requireApp(appId);
            User user = viewer(request);
            if (!AppPolicy.canRead(app, user)) return ResponseEntity.notFound().build();
            boolean editor = "1".equals(request.getParameter("visualEditor")) && AppPolicy.isOwner(app, user);
            return file(workspace.previewFiles(app), fileName, !AppPolicy.isPublic(app), editor);
        } catch (BusinessException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping({"/deploy/{deployKey}", "/deploy/{deployKey}/"})
    public ResponseEntity<Resource> getDeployIndex(
            @PathVariable String deployKey, HttpServletRequest request) {
        return getDeployResource(deployKey, "index.html", request);
    }

    @GetMapping("/deploy/{deployKey}/{fileName:.+}")
    public ResponseEntity<Resource> getDeployResource(
            @PathVariable String deployKey,
            @PathVariable String fileName,
            HttpServletRequest request) {
        try {
            App app = workspace.byDeployKey(deployKey);
            // 部署链接本身是分享入口，deployKey 已经是不可猜测的访问凭证；
            // 不能再按应用私有可见性拦截，否则截图服务和访客都无法打开部署页。
            return file(workspace.deployedFiles(app), fileName, !AppPolicy.isPublic(app));
        } catch (BusinessException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/static/{appId}/assets/{asset:.+}")
    public ResponseEntity<Resource> previewAsset(
            @PathVariable long appId, @PathVariable String asset, HttpServletRequest request) {
        return getPreviewResource(appId, "assets/" + asset, request);
    }

    @GetMapping("/deploy/{deployKey}/assets/{asset:.+}")
    public ResponseEntity<Resource> deployAsset(
            @PathVariable String deployKey,
            @PathVariable String asset,
            HttpServletRequest request) {
        return getDeployResource(deployKey, "assets/" + asset, request);
    }

    private User viewer(HttpServletRequest request) {
        try {
            return userService.getLoginUser(request);
        } catch (BusinessException e) {
            return null;
        }
    }

    private ResponseEntity<Resource> file(
            java.util.Map<String, String> files, String name, boolean privatePage) {
        return file(files, name, privatePage, false);
    }

    private ResponseEntity<Resource> file(
            java.util.Map<String, String> files, String name, boolean privatePage, boolean editor) {
        boolean project = VueProjectFiles.isProject(files);
        if (project) {
            if (!VueProjectFiles.runtimePath("dist/" + name))
                return ResponseEntity.notFound().build();
            java.util.Map<String, String> runtime = new java.util.LinkedHashMap<>();
            files.forEach(
                    (path, value) -> {
                        if (path.startsWith("dist/")) runtime.put(path.substring(5), value);
                    });
            files = runtime;
        }
        String content = files.get(name);
        if ((privatePage || project) && "index.html".equals(name) && content != null)
            content = AppPageRenderer.inlinePrivateAssets(files);
        else if ("index.html".equals(name) && content != null)
            content = AppPageRenderer.prepareNativeAssets(files, false);
        if ((!project && !AppFileStore.FILES.contains(name)) || content == null)
            return ResponseEntity.notFound().build();
        if (editor && "index.html".equals(name)) content = AppPageRenderer.withVisualEditorBridge(content);
        return ResponseEntity.ok()
                .contentType(
                        new MediaType(
                                MediaTypeFactory.getMediaType(name).orElse(MediaType.TEXT_PLAIN),
                                StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox allow-scripts allow-forms")
                .body(new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
    }
}
