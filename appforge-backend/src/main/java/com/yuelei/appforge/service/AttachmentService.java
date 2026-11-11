package com.yuelei.appforge.service;

import com.mybatisflex.core.service.IService;
import com.yuelei.appforge.model.entity.AppAttachment;
import com.yuelei.appforge.model.entity.User;
import com.yuelei.appforge.model.vo.AttachmentVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AttachmentService extends IService<AppAttachment> {

    AttachmentVO upload(MultipartFile file, Long appId, User user);

    /**
     * 读取当前用户拥有、且属于指定应用（appId 可为空）的附件；缺失即报错，避免越权引用。
     */
    List<AppAttachment> requireOwned(List<Long> ids, Long appId, User user);

    void bindToMessage(List<Long> ids, Long chatHistoryId, Long userId);

    /** 把附件整理成模型可读的 XML 片段；图片只给 URL，文本/文档给提取文字。 */
    String buildPromptBlock(List<AppAttachment> attachments);

    AttachmentVO getOwnedVO(Long id, User user);

    Resource loadLocalContent(Long id, User user);
}
