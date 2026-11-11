package com.yuelei.appforge.model.vo;

import com.yuelei.appforge.model.entity.AppAttachment;

/** 上传响应与会话附件展示共用的公开字段，不暴露 storageKey/extractedText。 */
public record AttachmentVO(
        Long id, String kind, String name, String mimeType, long size, String url) {

    public static AttachmentVO from(AppAttachment attachment) {
        return new AttachmentVO(
                attachment.getId(),
                attachment.getKind(),
                attachment.getOriginalName(),
                attachment.getMimeType(),
                attachment.getSizeBytes() == null ? 0 : attachment.getSizeBytes(),
                attachment.getUrl());
    }
}
