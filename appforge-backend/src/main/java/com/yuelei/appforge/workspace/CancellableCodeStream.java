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
import java.net.ConnectException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

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

    /** 每个缺失文件独立请求一次，避免 CSS 与 JavaScript 共用一次输出额度。 */
    private Flux<String> completeMissingFiles(String message, String original) {
        var files = new MultiFileCodeParser().parseCode(original);
        if (files.getHtmlCode() == null || files.getHtmlCode().isBlank()) return Flux.empty();
        boolean cssMissing = files.getCssCode() == null || files.getCssCode().isBlank();
        boolean jsMissing = files.getJsCode() == null || files.getJsCode().isBlank();
        StringBuilder context = new StringBuilder(original);
        Flux<String> css = cssMissing ? completeFile(message, context, "style.css") : Flux.empty();
        Flux<String> js = jsMissing ? completeFile(message, context, "script.js") : Flux.empty();
        return css.concatWith(js);
    }

    private Flux<String> completeFile(String message, StringBuilder context, String name) {
        return Flux.defer(
                () -> {
                    boolean css = name.equals("style.css");
                    String language = css ? "css" : "javascript";
                    String repairRequest =
                            "本轮用户要求：\n"
                                    + message
                                    + "\n\n已生成的页面文件如下，仅作为需要保留的上下文：\n"
                                    + context
                                    + "\n\n本次唯一需要补齐的文件："
                                    + name
                                    + "（"
                                    + language
                                    + " 代码块）"
                                    + "\n只返回这个文件的完整代码，沿用 HTML 中的类名、ID、结构和功能要求。";
                    StringBuilder repair = new StringBuilder();
                    return Flux.concat(
                            Flux.just(
                                    css
                                            ? "\n\n正在补齐网页样式（style.css）。\n\n"
                                            : "\n\n正在补齐网页交互（script.js）。\n\n"),
                            request(
                                            repairRequest,
                                            "prompt/codegen-native-repair-system-prompt.txt",
                                            "补齐 " + name)
                                    .doOnNext(chunk -> appendBounded(repair, chunk))
                                    .thenMany(
                                            Flux.defer(
                                                    () -> {
                                                        var completed =
                                                                new MultiFileCodeParser()
                                                                        .parseCode(
                                                                                repair.toString());
                                                        String code =
                                                                css
                                                                        ? completed.getCssCode()
                                                                        : completed.getJsCode();
                                                        ThrowUtils.throwIf(
                                                                code == null || code.isBlank(),
                                                                ErrorCode.OPERATION_ERROR,
                                                                "模型补齐后仍缺少 "
                                                                        + name
                                                                        + "，未保存此次生成，请重试");
                                                        String result =
                                                                "\n```" + language + "\n" + code
                                                                        + "\n```\n";
                                                        appendBounded(context, result);
                                                        return Flux.just(result);
                                                    })));
                });
    }

    private static void appendBounded(StringBuilder output, String chunk) {
        ThrowUtils.throwIf(
                output.length() + chunk.length() > 2_000_000,
                ErrorCode.OPERATION_ERROR,
                "模型输出过长，已停止");
        output.append(chunk);
    }

    private Flux<String> request(String message, String resource) {
        return request(message, resource, "生成网页");
    }

    private Flux<String> request(String message, String resource, String stage) {
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
                                var future = sendWithConnectRetry(request);
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
                                                                                            stage
                                                                                                    + "时模型输出达到长度限制，本次不保存，请简化需求后重试");
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

    /** 建连超时通常是一次网络抖动；请求尚未发出，重试一次不会重复生成。 */
    private CompletableFuture<HttpResponse<InputStream>> sendWithConnectRetry(HttpRequest request) {
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .exceptionallyCompose(
                        error ->
                                isConnectFailure(error)
                                        ? client.sendAsync(
                                                request,
                                                HttpResponse.BodyHandlers.ofInputStream())
                                        : CompletableFuture.failedFuture(error));
    }

    private static boolean isConnectFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof HttpConnectTimeoutException
                    || current instanceof ConnectException) return true;
            current = current.getCause();
        }
        return false;
    }

    private static void close(InputStream input) {
        if (input != null)
            try {
                input.close();
            } catch (IOException ignored) {
            }
    }
}
