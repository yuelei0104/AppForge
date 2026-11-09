package com.yuelei.appforge.workspace;

import cn.hutool.json.JSONUtil;

import com.yuelei.appforge.core.parser.MultiFileCodeParser;
import com.yuelei.appforge.exception.*;
import com.yuelei.appforge.model.enums.CodeGenTypeEnum;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** LangChain4j 1.1 的 Flux 适配器没有取消传输接口，用可关闭的 HTTP SSE 流实现中断。 */
@Component
public class CancellableCodeStream {
    @Value("${langchain4j.open-ai.streaming-chat-model.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.streaming-chat-model.api-key:}")
    private String apiKey;

    @Value("${langchain4j.open-ai.streaming-chat-model.model-name:deepseek-flash}")
    private String model;

    @Value("${langchain4j.open-ai.streaming-chat-model.max-tokens:8192}")
    private int maxTokens;

    @Value("${langchain4j.open-ai.streaming-chat-model.reasoning-effort:none}")
    private String reasoningEffort;

    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public Flux<String> stream(String message, CodeGenTypeEnum type) {
        String resource =
                type == CodeGenTypeEnum.HTML
                        ? "prompt/codegen-html-system-prompt.txt"
                        : "prompt/codegen-multi-file-system-prompt.txt";
        if (type != CodeGenTypeEnum.MULTI_FILE) return request(message, resource);
        return Flux.defer(
                () -> {
                    StringBuilder original = new StringBuilder();
                    return request(message, resource)
                            .doOnNext(chunk -> appendBounded(original, chunk))
                            .concatWith(
                                    Flux.defer(
                                            () ->
                                                    completeMissingFiles(
                                                            message, original.toString())));
                });
    }

    /** 每次订阅最多补齐一次；已有文件保持模型首次返回的内容。 */
    private Flux<String> completeMissingFiles(String message, String original) {
        var parser = new MultiFileCodeParser();
        var files = parser.parseCode(original);
        if (files.getHtmlCode() == null || files.getHtmlCode().isBlank()) return Flux.empty();
        boolean cssMissing = files.getCssCode() == null || files.getCssCode().isBlank();
        boolean jsMissing = files.getJsCode() == null || files.getJsCode().isBlank();
        if (!cssMissing && !jsMissing) return Flux.empty();
        String missing =
                (cssMissing ? "style.css（css 代码块） " : "")
                        + (jsMissing ? "script.js（javascript 代码块）" : "");
        String repairRequest =
                "本轮用户要求：\n"
                        + message
                        + "\n\n首次输出如下，仅作为需要保留的页面上下文：\n"
                        + original
                        + "\n\n需要补齐的文件："
                        + missing
                        + "\n请仅输出这些缺失文件的完整代码，沿用 HTML 中的类名、ID、结构和功能要求。";
        StringBuilder repair = new StringBuilder();
        return Flux.concat(
                Flux.just("\n\n正在补齐网页样式与交互。\n\n"),
                request(repairRequest, "prompt/codegen-native-repair-system-prompt.txt")
                        .doOnNext(chunk -> appendBounded(repair, chunk))
                        .thenMany(
                                Flux.defer(
                                        () -> {
                                            var completed = parser.parseCode(repair.toString());
                                            ThrowUtils.throwIf(
                                                    cssMissing
                                                                    && (completed.getCssCode()
                                                                                    == null
                                                                            || completed
                                                                                    .getCssCode()
                                                                                    .isBlank())
                                                            || jsMissing
                                                                    && (completed.getJsCode()
                                                                                    == null
                                                                            || completed
                                                                                    .getJsCode()
                                                                                    .isBlank()),
                                                    ErrorCode.OPERATION_ERROR,
                                                    "模型补齐后仍缺少 " + missing + "，未保存此次生成，请重试");
                                            String result =
                                                    (cssMissing
                                                                    ? "\n```css\n"
                                                                            + completed.getCssCode()
                                                                            + "\n```\n"
                                                                    : "")
                                                            + (jsMissing
                                                                    ? "\n```javascript\n"
                                                                            + completed.getJsCode()
                                                                            + "\n```\n"
                                                                    : "");
                                            return Flux.just(result);
                                        })));
    }

    private static void appendBounded(StringBuilder output, String chunk) {
        ThrowUtils.throwIf(
                output.length() + chunk.length() > 2_000_000,
                ErrorCode.OPERATION_ERROR,
                "模型输出过长，已停止");
        output.append(chunk);
    }

    private Flux<String> request(String message, String resource) {
        return Flux.defer(
                () -> {
                    ThrowUtils.throwIf(
                            apiKey == null || apiKey.isBlank(),
                            ErrorCode.OPERATION_ERROR,
                            "请配置后端模型 API Key");
                    String system;
                    try (var input = new ClassPathResource(resource).getInputStream()) {
                        system = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (IOException error) {
                        return Flux.error(error);
                    }
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("model", model);
                    body.put("stream", true);
                    body.put("max_tokens", maxTokens);
                    body.put(
                            "messages",
                            List.of(
                                    Map.of("role", "system", "content", system),
                                    Map.of("role", "user", "content", message)));
                    if (reasoningEffort != null && !reasoningEffort.isBlank())
                        body.put("reasoning_effort", reasoningEffort);
                    HttpRequest request =
                            HttpRequest.newBuilder(
                                            URI.create(
                                                    baseUrl.replaceAll("/+$", "")
                                                            + "/chat/completions"))
                                    .timeout(Duration.ofMinutes(5))
                                    .header("Authorization", "Bearer " + apiKey)
                                    .header("Content-Type", "application/json")
                                    .POST(
                                            HttpRequest.BodyPublishers.ofString(
                                                    JSONUtil.toJsonStr(body)))
                                    .build();
                    return Flux.<String>create(
                            sink -> {
                                AtomicReference<InputStream> input = new AtomicReference<>();
                                var future =
                                        client.sendAsync(
                                                request, HttpResponse.BodyHandlers.ofInputStream());
                                sink.onCancel(
                                        () -> {
                                            future.cancel(true);
                                            close(input.get());
                                        });
                                future.whenComplete(
                                        (response, error) -> {
                                            if (error != null) {
                                                if (!sink.isCancelled()) sink.error(error);
                                                return;
                                            }
                                            input.set(response.body());
                                            if (sink.isCancelled()) {
                                                close(response.body());
                                                return;
                                            }
                                            Schedulers.boundedElastic()
                                                    .schedule(
                                                            () -> {
                                                                try (var reader =
                                                                        new BufferedReader(
                                                                                new InputStreamReader(
                                                                                        response
                                                                                                .body(),
                                                                                        StandardCharsets
                                                                                                .UTF_8))) {
                                                                    if (response.statusCode()
                                                                            != 200) {
                                                                        String reason =
                                                                                response
                                                                                                        .statusCode()
                                                                                                == 401
                                                                                        ? "模型服务认证失败，请检查后端"
                                                                                              + " API Key"
                                                                                        : "模型服务请求失败（HTTP"
                                                                                              + " "
                                                                                                + response
                                                                                                        .statusCode()
                                                                                                + "）";
                                                                        throw new BusinessException(
                                                                                ErrorCode
                                                                                        .OPERATION_ERROR,
                                                                                reason);
                                                                    }
                                                                    boolean completed = false;
                                                                    StringBuilder event =
                                                                            new StringBuilder();
                                                                    String line;
                                                                    while (!sink.isCancelled()
                                                                            && (line =
                                                                                            reader
                                                                                                    .readLine())
                                                                                    != null) {
                                                                        if (line.startsWith(
                                                                                "data:"))
                                                                            event.append(
                                                                                            line.substring(
                                                                                                            5)
                                                                                                    .stripLeading())
                                                                                    .append('\n');
                                                                        else if (line.isEmpty()
                                                                                && event.length()
                                                                                        > 0) {
                                                                            String data =
                                                                                    event.toString()
                                                                                            .trim();
                                                                            event.setLength(0);
                                                                            if ("[DONE]"
                                                                                    .equals(data)) {
                                                                                completed = true;
                                                                                break;
                                                                            }
                                                                            var json =
                                                                                    JSONUtil
                                                                                            .parseObj(
                                                                                                    data);
                                                                            if (json.containsKey(
                                                                                    "error"))
                                                                                throw new BusinessException(
                                                                                        ErrorCode
                                                                                                .OPERATION_ERROR,
                                                                                        "模型服务返回错误，请检查模型配置");
                                                                            var choices =
                                                                                    json
                                                                                            .getJSONArray(
                                                                                                    "choices");
                                                                            if (choices != null
                                                                                    && !choices
                                                                                            .isEmpty()) {
                                                                                var choice =
                                                                                        choices
                                                                                                .getJSONObject(
                                                                                                        0);
                                                                                var delta =
                                                                                        choice
                                                                                                .getJSONObject(
                                                                                                        "delta");
                                                                                if (delta != null) {
                                                                                    String text =
                                                                                            delta
                                                                                                    .getStr(
                                                                                                            "content",
                                                                                                            "");
                                                                                    if (!text
                                                                                            .isEmpty())
                                                                                        sink.next(
                                                                                                text);
                                                                                }
                                                                                String finish =
                                                                                        choice
                                                                                                .getStr(
                                                                                                        "finish_reason",
                                                                                                        "");
                                                                                if ("length"
                                                                                        .equals(
                                                                                                finish))
                                                                                    throw new BusinessException(
                                                                                            ErrorCode
                                                                                                    .OPERATION_ERROR,
                                                                                            "模型输出达到长度限制，本次不保存，请简化需求后重试");
                                                                                if (!finish
                                                                                                .isBlank()
                                                                                        && !"stop"
                                                                                                .equals(
                                                                                                        finish))
                                                                                    throw new BusinessException(
                                                                                            ErrorCode
                                                                                                    .OPERATION_ERROR,
                                                                                            "模型未正常完成，本次不保存");
                                                                            }
                                                                        }
                                                                    }
                                                                    if (!sink.isCancelled()) {
                                                                        if (!completed)
                                                                            sink.error(
                                                                                    new IOException(
                                                                                            "模型流提前中断"));
                                                                        else sink.complete();
                                                                    }
                                                                } catch (Throwable failure) {
                                                                    if (!sink.isCancelled())
                                                                        sink.error(failure);
                                                                } finally {
                                                                    close(input.get());
                                                                }
                                                            });
                                        });
                            });
                });
    }

    private static void close(InputStream input) {
        if (input != null)
            try {
                input.close();
            } catch (IOException ignored) {
            }
    }
}
