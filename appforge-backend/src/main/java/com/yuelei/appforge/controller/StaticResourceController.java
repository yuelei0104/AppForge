package com.yuelei.appforge.controller;

import cn.hutool.core.util.StrUtil;
import com.yuelei.appforge.constant.AppConstant;
import com.yuelei.appforge.model.entity.App;
import com.yuelei.appforge.service.AppService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 静态资源控制器。
 *
 * <p>用于访问生成应用的预览文件，以及部署后的应用文件。</p>
 *
 * @author yuelei
 */
@RestController
public class StaticResourceController {

    /**
     * 应用生成根目录（用于浏览）
     */
    private static final String PREVIEW_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /**
     * 应用部署根目录
     */
    private static final String DEPLOY_ROOT_DIR = AppConstant.CODE_DEPLOY_ROOT_DIR;

    @jakarta.annotation.Resource
    private AppService appService;

    /**
     * 预览应用首页
     *
     * @param appId 应用 id
     * @return 首页文件
     */
    @GetMapping({"/static/{appId}", "/static/{appId}/"})
    public ResponseEntity<Resource> getPreviewIndex(@PathVariable Long appId) {
        return getPreviewResource(appId, "index.html");
    }

    /**
     * 预览应用静态资源
     *
     * @param appId    应用 id
     * @param fileName 文件名
     * @return 静态资源
     */
    @GetMapping("/static/{appId}/{fileName:.+}")
    public ResponseEntity<Resource> getPreviewResource(@PathVariable Long appId,
                                                       @PathVariable String fileName) {
        App app = appService.getById(appId);
        if (app == null || StrUtil.isBlank(app.getCodeGenType())) {
            return ResponseEntity.notFound().build();
        }
        String appDirName = app.getCodeGenType() + "_" + appId;
        Path appDir = Paths.get(PREVIEW_ROOT_DIR, appDirName).toAbsolutePath().normalize();
        return serveFile(appDir, fileName);
    }

    /**
     * 访问部署应用首页
     *
     * @param deployKey 部署标识
     * @return 首页文件
     */
    @GetMapping({"/deploy/{deployKey}", "/deploy/{deployKey}/"})
    public ResponseEntity<Resource> getDeployIndex(@PathVariable String deployKey) {
        return getDeployResource(deployKey, "index.html");
    }

    /**
     * 访问部署应用静态资源
     *
     * @param deployKey 部署标识
     * @param fileName  文件名
     * @return 静态资源
     */
    @GetMapping("/deploy/{deployKey}/{fileName:.+}")
    public ResponseEntity<Resource> getDeployResource(@PathVariable String deployKey,
                                                      @PathVariable String fileName) {
        // 部署标识只允许大小写字母和数字，防止路径穿越
        if (!deployKey.matches("[A-Za-z0-9]+")) {
            return ResponseEntity.notFound().build();
        }
        Path deployDir = Paths.get(DEPLOY_ROOT_DIR, deployKey).toAbsolutePath().normalize();
        return serveFile(deployDir, fileName);
    }

    /**
     * 安全地读取并返回文件
     *
     * @param rootDir  根目录
     * @param fileName 文件名
     * @return 文件响应
     */
    private ResponseEntity<Resource> serveFile(Path rootDir, String fileName) {
        Path filePath = rootDir.resolve(fileName).normalize();
        // 防止通过 ../ 访问根目录外的文件
        if (!filePath.startsWith(rootDir) || !Files.isRegularFile(filePath)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(filePath);
        MediaType mediaType = MediaTypeFactory.getMediaType(resource)
            .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
            .contentType(mediaType)
            .body(resource);
    }
}
