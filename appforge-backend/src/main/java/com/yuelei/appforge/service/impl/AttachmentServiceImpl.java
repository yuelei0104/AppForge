package com.yuelei.appforge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yuelei.appforge.constant.AppConstant;
import com.yuelei.appforge.exception.ErrorCode;
import com.yuelei.appforge.exception.ThrowUtils;
import com.yuelei.appforge.mapper.AppAttachmentMapper;
import com.yuelei.appforge.manager.CosManager;
import com.yuelei.appforge.model.entity.AppAttachment;
import com.yuelei.appforge.model.entity.User;
import com.yuelei.appforge.model.vo.AttachmentVO;
import com.yuelei.appforge.service.AttachmentService;
import com.yuelei.appforge.service.DocumentTextExtractor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class AttachmentServiceImpl extends ServiceImpl<AppAttachmentMapper, AppAttachment>
        implements AttachmentService {

    private static final long IMAGE_MAX = 5L * 1024 * 1024;
    private static final long TEXT_MAX = 256L * 1024;
    private static final long DOCUMENT_MAX = 10L * 1024 * 1024;
    private static final int PROMPT_MAX = 50_000;

    private static final Map<String, String> TYPES =
            Map.ofEntries(
                    Map.entry("png", "image/png"),
                    Map.entry("jpg", "image/jpeg"),
                    Map.entry("jpeg", "image/jpeg"),
                    Map.entry("webp", "image/webp"),
                    Map.entry("gif", "image/gif"),
                    Map.entry("txt", "text/plain"),
                    Map.entry("md", "text/markdown"),
                    Map.entry("json", "application/json"),
                    Map.entry("csv", "text/csv"),
                    Map.entry("xml", "application/xml"),
                    Map.entry("log", "text/plain"),
                    Map.entry("yml", "text/plain"),
                    Map.entry("yaml", "text/plain"),
                    Map.entry("pdf", "application/pdf"),
                    Map.entry("doc", "application/msword"),
                    Map.entry("docx",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                    Map.entry("ppt", "application/vnd.ms-powerpoint"),
                    Map.entry("pptx",
                            "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
                    Map.entry("xls", "application/vnd.ms-excel"),
                    Map.entry("xlsx",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                    Map.entry("rtf", "application/rtf"),
                    Map.entry("odt", "application/vnd.oasis.opendocument.text"));

    @Resource private CosManager cosManager;
    @Resource private DocumentTextExtractor textExtractor;

    @Override
    public AttachmentVO upload(MultipartFile file, Long appId, User user) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "文件为空");
        ThrowUtils.throwIf(user == null || user.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        String name = safeName(file.getOriginalFilename());
        String ext = extension(name);
        String mime = TYPES.get(ext);
        ThrowUtils.throwIf(mime == null, ErrorCode.PARAMS_ERROR, "不支持的文件类型");
        ThrowUtils.throwIf("svg".equals(ext) || "html".equals(ext), ErrorCode.PARAMS_ERROR,
                "不支持的文件类型");
        String kind = mime.startsWith("image/") ? "image" : (isText(ext) ? "text" : "document");
        long max = "image".equals(kind) ? IMAGE_MAX : ("text".equals(kind) ? TEXT_MAX : DOCUMENT_MAX);
        ThrowUtils.throwIf(file.getSize() > max, ErrorCode.PARAMS_ERROR,
                "文件过大，图片最多 5MB，文本最多 256KB，文档最多 10MB");

        Path temp = null;
        try {
            temp = Files.createTempFile("appforge-upload-", ".tmp");
            file.transferTo(temp.toFile());
            if ("image".equals(kind)) {
                validateImage(temp, ext);
            }
            String text = "image".equals(kind) ? "" : textExtractor.extract(temp.toFile(), mime);
            String month = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
            String relative = month + "/" + UUID.randomUUID() + "." + ext;
            String storageKey = "cos:" + relative;
            String url = cosManager.uploadFile("appforge/uploads/" + relative, temp.toFile(), mime);
            if (StrUtil.isBlank(url)) {
                storageKey = "local:" + relative;
                Path local = Path.of(AppConstant.UPLOAD_ROOT_DIR, relative);
                Files.createDirectories(local.getParent());
                Files.move(temp, local, StandardCopyOption.REPLACE_EXISTING);
            }

            AppAttachment attachment =
                    AppAttachment.builder()
                            .appId(appId)
                            .userId(user.getId())
                            .kind(kind)
                            .originalName(name)
                            .mimeType(mime)
                            .sizeBytes(file.getSize())
                            .storageKey(storageKey)
                            .url(url == null ? "" : url)
                            .extractedText(text)
                            .createTime(LocalDateTime.now())
                            .updateTime(LocalDateTime.now())
                            .isDelete(0)
                            .build();
            ThrowUtils.throwIf(!save(attachment), ErrorCode.OPERATION_ERROR, "保存附件失败");
            if (StrUtil.isBlank(url)) {
                attachment.setUrl("/api/file/content/" + attachment.getId());
                updateById(attachment);
            }
            return AttachmentVO.from(attachment);
        } catch (Exception e) {
            log.warn("上传附件失败: {}", name, e);
            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new RuntimeException("上传附件失败", e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (Exception ignored) {
                    /* 临时文件清理失败不影响业务。 */
                }
            }
        }
    }

    @Override
    public List<AppAttachment> requireOwned(List<Long> ids, Long appId, User user) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> unique = new ArrayList<>(new LinkedHashSet<>(ids));
        unique.removeIf(id -> id == null || id <= 0);
        ThrowUtils.throwIf(unique.isEmpty(), ErrorCode.PARAMS_ERROR, "附件参数无效");
        QueryWrapper query =
                QueryWrapper.create()
                        .in("id", unique)
                        .eq("userId", user.getId());
        if (appId != null && appId > 0) {
            query.eq("appId", appId);
        }
        List<AppAttachment> list = list(query);
        ThrowUtils.throwIf(list.size() != unique.size(), ErrorCode.NO_AUTH_ERROR, "附件不存在或无权限");
        return unique.stream()
                .map(id -> list.stream().filter(a -> id.equals(a.getId())).findFirst().orElseThrow())
                .toList();
    }

    @Override
    public void bindToMessage(List<Long> ids, Long chatHistoryId, Long userId) {
        if (ids == null || ids.isEmpty() || chatHistoryId == null) {
            return;
        }
        for (Long id : new LinkedHashSet<>(ids)) {
            AppAttachment attachment = getById(id);
            if (attachment == null || !userId.equals(attachment.getUserId())) {
                continue;
            }
            if (attachment.getChatHistoryId() == null) {
                attachment.setChatHistoryId(chatHistoryId);
                attachment.setUpdateTime(LocalDateTime.now());
                updateById(attachment);
            }
        }
    }

    @Override
    public String buildPromptBlock(List<AppAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return "";
        }
        StringBuilder prompt = new StringBuilder();
        prompt.append("\n\n<attachments>\n");
        for (AppAttachment attachment : attachments) {
            if (prompt.length() > PROMPT_MAX) {
                prompt.append("<note>附件内容过多，剩余部分已省略</note>\n");
                break;
            }
            String name = escape(attachment.getOriginalName());
            if ("image".equals(attachment.getKind())) {
                prompt.append("<image name=\"").append(name).append("\">")
                        .append(escape(attachment.getUrl())).append("</image>\n");
            } else if (StrUtil.isNotBlank(attachment.getExtractedText())) {
                String text = attachment.getExtractedText();
                int remain = Math.max(0, PROMPT_MAX - prompt.length() - 200);
                if (text.length() > remain) {
                    text = text.substring(0, remain) + "\n...(已截断)";
                }
                prompt.append("<document name=\"").append(name).append("\">\n")
                        .append(escape(text)).append("\n</document>\n");
            } else {
                prompt.append("<document name=\"").append(name)
                        .append("\" note=\"未能提取文本\"/>\n");
            }
        }
        prompt.append("</attachments>");
        return prompt.toString();
    }

    @Override
    public AttachmentVO getOwnedVO(Long id, User user) {
        AppAttachment attachment = getById(id);
        ThrowUtils.throwIf(
                attachment == null || !user.getId().equals(attachment.getUserId()),
                ErrorCode.NO_AUTH_ERROR,
                "附件不存在或无权限");
        return AttachmentVO.from(attachment);
    }

    @Override
    public org.springframework.core.io.Resource loadLocalContent(Long id, User user) {
        AppAttachment attachment = getById(id);
        ThrowUtils.throwIf(
                attachment == null || !user.getId().equals(attachment.getUserId()),
                ErrorCode.NO_AUTH_ERROR,
                "附件不存在或无权限");
        String key = attachment.getStorageKey();
        ThrowUtils.throwIf(key == null || !key.startsWith("local:"), ErrorCode.NOT_FOUND_ERROR,
                "附件不在本地");
        Path root = Path.of(AppConstant.UPLOAD_ROOT_DIR).toAbsolutePath().normalize();
        Path path = root.resolve(key.substring("local:".length())).normalize();
        ThrowUtils.throwIf(!path.startsWith(root) || !Files.isRegularFile(path),
                ErrorCode.NOT_FOUND_ERROR, "附件文件不存在");
        return new FileSystemResource(path);
    }

    /** 不能只信扩展名：伪装成图片的 HTML/SVG 不应被内联返回。 */
    private void validateImage(Path file, String ext) {
        byte[] head = new byte[12];
        int read;
        try (var in = Files.newInputStream(file)) {
            read = in.readNBytes(head, 0, head.length);
        } catch (Exception e) {
            throw new RuntimeException("读取图片失败", e);
        }
        boolean valid =
                switch (ext) {
                    case "png" ->
                            read >= 8
                                    && (head[0] & 0xff) == 0x89
                                    && head[1] == 'P'
                                    && head[2] == 'N'
                                    && head[3] == 'G'
                                    && head[4] == 0x0d
                                    && head[5] == 0x0a
                                    && head[6] == 0x1a
                                    && head[7] == 0x0a;
                    case "jpg", "jpeg" ->
                            read >= 3
                                    && (head[0] & 0xff) == 0xff
                                    && (head[1] & 0xff) == 0xd8
                                    && (head[2] & 0xff) == 0xff;
                    case "gif" ->
                            read >= 6
                                    && head[0] == 'G'
                                    && head[1] == 'I'
                                    && head[2] == 'F'
                                    && head[3] == '8';
                    case "webp" ->
                            read >= 12
                                    && head[0] == 'R'
                                    && head[1] == 'I'
                                    && head[2] == 'F'
                                    && head[3] == 'F'
                                    && head[8] == 'W'
                                    && head[9] == 'E'
                                    && head[10] == 'B'
                                    && head[11] == 'P';
                    default -> false;
                };
        ThrowUtils.throwIf(!valid, ErrorCode.PARAMS_ERROR, "图片内容与扩展名不匹配");
    }

    private boolean isText(String ext) {
        return TYPES.get(ext).startsWith("text/")
                || "application/json".equals(TYPES.get(ext))
                || "application/xml".equals(TYPES.get(ext));
    }

    private String safeName(String original) {
        String name = StringUtils.cleanPath(original == null ? "file" : original);
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\r\\n\"]", "_").strip();
        ThrowUtils.throwIf(name.isBlank() || name.length() > 200, ErrorCode.PARAMS_ERROR, "文件名无效");
        return name;
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        ThrowUtils.throwIf(dot < 0 || dot == name.length() - 1, ErrorCode.PARAMS_ERROR, "文件缺少扩展名");
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
