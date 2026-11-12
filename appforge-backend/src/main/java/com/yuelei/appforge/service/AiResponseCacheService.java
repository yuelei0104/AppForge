package com.yuelei.appforge.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import com.yuelei.appforge.model.enums.CodeGenTypeEnum;

import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 低成本的 AI 结果复用：智能路由按字符串/相似度命中，首次创建生成结果按完全匹配或高相似度命中。
 *
 * <p>缓存只用于“从零创建”的相同需求，避免把历史对话上下文或局部修改错误复用到其他应用。
 */
@Service
@Slf4j
public class AiResponseCacheService {

    private static final String ROUTE_KEY_PREFIX = "appforge:ai:route:v1:";
    private static final String ROUTE_INDEX_KEY = "appforge:ai:route:index";
    private static final String RESULT_KEY_PREFIX = "appforge:ai:result:v1:";
    private static final String RESULT_INDEX_PREFIX = "appforge:ai:result:index:";

    private static final Duration ROUTE_TTL = Duration.ofDays(7);
    private static final Duration RESULT_TTL = Duration.ofHours(24);
    private static final int INDEX_LIMIT = 200;
    private static final int MAX_RESULT_BYTES = 2 * 1024 * 1024;

    /** 路由只决定生成模式，允许更宽松的相似度。 */
    private static final double ROUTE_SIMILARITY = 0.88D;

    /** 生成结果会直接复用为应用源码，必须非常接近，避免把不同需求拼在一起。 */
    private static final double RESULT_SIMILARITY = 0.97D;

    @Resource(name = "stringRedisTemplate")
    private StringRedisTemplate redis;

    /** 返回 null 表示未命中，调用方继续走真实模型。 */
    public CodeGenTypeEnum findRoute(String prompt) {
        String normalized = normalize(prompt);
        if (normalized.isBlank() || redis == null) {
            return null;
        }
        try {
            String exact = redis.opsForValue().get(routeKey(normalized));
            CodeGenTypeEnum cached = parseType(exact);
            if (cached != null) {
                return cached;
            }
            for (IndexEntry entry : readIndex(ROUTE_INDEX_KEY)) {
                if (similarity(normalized, entry.prompt()) >= ROUTE_SIMILARITY) {
                    CodeGenTypeEnum similar = parseType(entry.value());
                    if (similar != null) {
                        // 回填精确键，后续同一句话直接命中。
                        redis.opsForValue()
                                .set(routeKey(normalized), similar.getValue(), ROUTE_TTL);
                        return similar;
                    }
                }
            }
        } catch (RuntimeException error) {
            log.debug("读取路由缓存失败，继续调用模型: {}", error.getMessage());
        }
        return null;
    }

    public void putRoute(String prompt, CodeGenTypeEnum type) {
        String normalized = normalize(prompt);
        if (normalized.isBlank() || type == null || redis == null) {
            return;
        }
        try {
            redis.opsForValue().set(routeKey(normalized), type.getValue(), ROUTE_TTL);
            pushIndex(ROUTE_INDEX_KEY, new IndexEntry(normalized, type.getValue()), ROUTE_TTL);
        } catch (RuntimeException error) {
            log.debug("写入路由缓存失败: {}", error.getMessage());
        }
    }

    /**
     * 返回缓存的应用源码；null 表示未命中。只有完全匹配或非常接近的创建请求才会复用，
     * 避免把“登录页”和“登录页加验证码”当成同一个结果。
     */
    public Map<String, String> findGeneration(String codeGenType, String prompt) {
        String normalized = normalize(prompt);
        if (normalized.isBlank() || redis == null || codeGenType == null) {
            return null;
        }
        try {
            String exactKey = resultKey(codeGenType, normalized);
            Map<String, String> exact = readFiles(redis.opsForValue().get(exactKey));
            if (exact != null) {
                return exact;
            }
            for (IndexEntry entry : readIndex(resultIndexKey(codeGenType))) {
                if (similarity(normalized, entry.prompt()) >= RESULT_SIMILARITY) {
                    Map<String, String> similar =
                            readFiles(redis.opsForValue().get(entry.value()));
                    if (similar != null) {
                        redis.opsForValue().set(exactKey, serializeFiles(normalized, similar), RESULT_TTL);
                        return similar;
                    }
                }
            }
        } catch (RuntimeException error) {
            log.debug("读取生成结果缓存失败，继续调用模型: {}", error.getMessage());
        }
        return null;
    }

    public void putGeneration(String codeGenType, String prompt, Map<String, String> files) {
        String normalized = normalize(prompt);
        if (normalized.isBlank()
                || codeGenType == null
                || files == null
                || files.isEmpty()
                || redis == null) {
            return;
        }
        try {
            String payload = serializeFiles(normalized, files);
            if (payload.getBytes(StandardCharsets.UTF_8).length > MAX_RESULT_BYTES) {
                return;
            }
            String exactKey = resultKey(codeGenType, normalized);
            redis.opsForValue().set(exactKey, payload, RESULT_TTL);
            pushIndex(
                    resultIndexKey(codeGenType),
                    new IndexEntry(normalized, exactKey),
                    RESULT_TTL);
        } catch (RuntimeException error) {
            log.debug("写入生成结果缓存失败: {}", error.getMessage());
        }
    }

    /** 去掉空白和标点，保留中英文与数字，降低排版差异造成的缓存未命中。 */
    static String normalize(String prompt) {
        if (prompt == null) {
            return "";
        }
        return prompt.trim().toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]+", "");
    }

    /** 字符二元组 Jaccard 相似度；短文本只接受完全一致。 */
    static double similarity(String left, String right) {
        if (left.equals(right)) {
            return 1D;
        }
        if (left.length() < 4 || right.length() < 4) {
            return 0D;
        }
        int shorter = Math.min(left.length(), right.length());
        int longer = Math.max(left.length(), right.length());
        if ((double) shorter / longer < 0.75D) {
            return 0D;
        }
        Set<String> a = bigrams(left);
        Set<String> b = bigrams(right);
        int intersection = 0;
        for (String item : a) {
            if (b.contains(item)) {
                intersection++;
            }
        }
        int union = a.size() + b.size() - intersection;
        return union == 0 ? 0D : (double) intersection / union;
    }

    private static Set<String> bigrams(String text) {
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i + 1 < text.length(); i++) {
            result.add(text.substring(i, i + 2));
        }
        return result;
    }

    private static String routeKey(String normalized) {
        return ROUTE_KEY_PREFIX + sha256(normalized);
    }

    private static String resultKey(String type, String normalized) {
        return RESULT_KEY_PREFIX + type + ":" + sha256(normalized);
    }

    private static String resultIndexKey(String type) {
        return RESULT_INDEX_PREFIX + type;
    }

    private static String sha256(String value) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(Character.forDigit((item >> 4) & 0xF, 16));
                hex.append(Character.forDigit(item & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private static CodeGenTypeEnum parseType(String value) {
        return value == null || value.isBlank() ? null : CodeGenTypeEnum.getEnumByValue(value);
    }

    private void pushIndex(String key, IndexEntry entry, Duration ttl) {
        redis.opsForList().leftPush(key, JSONUtil.toJsonStr(entry.toMap()));
        redis.opsForList().trim(key, 0, INDEX_LIMIT - 1);
        redis.expire(key, ttl);
    }

    private List<IndexEntry> readIndex(String key) {
        List<String> rows = redis.opsForList().range(key, 0, INDEX_LIMIT - 1);
        List<IndexEntry> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (String row : rows) {
            try {
                JSONObject object = JSONUtil.parseObj(row);
                String prompt = object.getStr("p");
                String value = object.getStr("v");
                if (prompt != null && value != null) {
                    result.add(new IndexEntry(prompt, value));
                }
            } catch (RuntimeException ignored) {
                // 单个损坏条目不能影响其他缓存命中。
            }
        }
        return result;
    }

    private static String serializeFiles(String normalized, Map<String, String> files) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("p", normalized);
        payload.put("files", files);
        return JSONUtil.toJsonStr(payload);
    }

    private static Map<String, String> readFiles(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        JSONObject payload = JSONUtil.parseObj(raw);
        JSONObject files = payload.getJSONObject("files");
        if (files == null || files.isEmpty()) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : files.keySet()) {
            result.put(key, files.getStr(key));
        }
        return result.isEmpty() ? null : result;
    }

    private record IndexEntry(String prompt, String value) {
        Map<String, String> toMap() {
            return Map.of("p", prompt, "v", value);
        }
    }
}
