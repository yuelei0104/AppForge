package com.yuelei.appforge.controller;

import com.yuelei.appforge.common.BaseResponse;
import com.yuelei.appforge.common.ResultUtils;
import com.yuelei.appforge.exception.ErrorCode;
import com.yuelei.appforge.exception.ThrowUtils;
import com.yuelei.appforge.model.entity.AppAttachment;
import com.yuelei.appforge.model.vo.AttachmentVO;
import com.yuelei.appforge.service.AppService;
import com.yuelei.appforge.service.AttachmentService;
import com.yuelei.appforge.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/file")
public class FileController {

    @Resource private AttachmentService attachmentService;
    @Resource private AppService appService;
    @Resource private UserService userService;

    @PostMapping("/upload")
    public BaseResponse<AttachmentVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long appId,
            HttpServletRequest request) {
        var user = userService.getLoginUser(request);
        if (appId != null && appId > 0) {
            // 校验应用归属，避免把附件挂到别人的应用下。
            appService.getAppVOById(appId, user);
        }
        return ResultUtils.success(attachmentService.upload(file, appId, user));
    }

    @GetMapping("/content/{id}")
    public ResponseEntity<?> content(@PathVariable Long id, HttpServletRequest request) {
        AppAttachment attachment = attachmentService.getById(id);
        ThrowUtils.throwIf(attachment == null, ErrorCode.NOT_FOUND_ERROR, "附件不存在");
        var user = userService.getLoginUser(request);
        AttachmentVO vo = attachmentService.getOwnedVO(id, user);
        if (attachment.getUrl() != null && attachment.getUrl().startsWith("http")) {
            return ResponseEntity.status(302).location(URI.create(attachment.getUrl())).build();
        }
        org.springframework.core.io.Resource resource = attachmentService.loadLocalContent(id, user);
        ContentDisposition disposition =
                ContentDisposition.inline()
                        .filename(vo.name() == null ? "attachment" : vo.name(), StandardCharsets.UTF_8)
                        .build();
        MediaType mediaType =
                vo.mimeType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(vo.mimeType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }
}
