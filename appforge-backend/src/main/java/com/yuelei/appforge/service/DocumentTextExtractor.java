package com.yuelei.appforge.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** 文档/文本转纯文本；图片不走这里（第一版不承诺 OCR）。 */
@Component
@Slf4j
public class DocumentTextExtractor {

    public static final int MAX_EXTRACT_CHARS = 50_000;

    public String extract(File file, String mimeType) {
        if (file == null || !file.isFile()) {
            return "";
        }
        if (isPlainText(mimeType, file.getName())) {
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                return limit(new String(bytes, StandardCharsets.UTF_8));
            } catch (Exception e) {
                log.warn("读取文本附件失败: {}", file.getName(), e);
                return "";
            }
        }
        if (mimeType != null && mimeType.startsWith("image/")) {
            return "";
        }
        try (FileInputStream input = new FileInputStream(file)) {
            BodyContentHandler handler = new BodyContentHandler(MAX_EXTRACT_CHARS);
            AutoDetectParser parser = new AutoDetectParser();
            parser.parse(input, handler, new Metadata(), new ParseContext());
            return limit(handler.toString());
        } catch (Exception e) {
            log.warn("Tika 解析附件失败: {} ({})", file.getName(), e.getMessage());
            return "";
        }
    }

    private boolean isPlainText(String mimeType, String name) {
        if (mimeType != null
                && (mimeType.startsWith("text/")
                        || mimeType.equals("application/json")
                        || mimeType.equals("application/xml"))) {
            return true;
        }
        String lower = name == null ? "" : name.toLowerCase();
        return lower.endsWith(".txt")
                || lower.endsWith(".md")
                || lower.endsWith(".json")
                || lower.endsWith(".csv")
                || lower.endsWith(".xml")
                || lower.endsWith(".log")
                || lower.endsWith(".yml")
                || lower.endsWith(".yaml");
    }

    private String limit(String text) {
        String value = text == null ? "" : text.strip();
        if (value.length() <= MAX_EXTRACT_CHARS) {
            return value;
        }
        return value.substring(0, MAX_EXTRACT_CHARS) + "\n...(内容过长，已截断)";
    }
}
