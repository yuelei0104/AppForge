package com.yuelei.appforge.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 聊天附件元数据；二进制放在 COS 或本地 uploads，消息里只保存引用。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("app_attachment")
public class AppAttachment implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    @Column("appId")
    private Long appId;

    @Column("userId")
    private Long userId;

    /** 绑定的用户消息 id；未发送时为空，避免未使用附件污染历史。 */
    @Column("chatHistoryId")
    private Long chatHistoryId;

    /** image / text / document */
    private String kind;

    @Column("originalName")
    private String originalName;

    @Column("mimeType")
    private String mimeType;

    @Column("sizeBytes")
    private Long sizeBytes;

    @Column("storageKey")
    private String storageKey;

    private String url;

    /** 文本/文档提取出的文字；图片未启用 OCR 时为空。 */
    @Column("extractedText")
    private String extractedText;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
