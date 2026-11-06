package com.yuelei.appforge.workspace;

import com.yuelei.appforge.ai.model.*;
import com.yuelei.appforge.core.AiCodeGeneratorFacade;
import com.yuelei.appforge.core.parser.CodeParserExecutor;
import com.yuelei.appforge.exception.*;
import com.yuelei.appforge.model.entity.*;
import com.yuelei.appforge.model.enums.CodeGenTypeEnum;

import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import reactor.core.publisher.*;
import reactor.core.scheduler.Schedulers;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class AppGenerationService {
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private AppWorkspaceService workspace;
    @Resource private AiCodeGeneratorFacade generator;
    private final Map<Long, Job> jobs = new ConcurrentHashMap<>();

    private static final class Job {
        final String token = UUID.randomUUID().toString();
        final Sinks.One<String> stop = Sinks.one();
        final AtomicBoolean stopped = new AtomicBoolean();
        final StringBuilder code = new StringBuilder();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        jdbcTemplate.update(
                "UPDATE app SET"
                    + " generationStatus='FAILED',generationError='服务重启中断了生成，请重新生成',generationToken=''"
                    + " WHERE generationStatus='GENERATING'");
    }

    public Flux<String> generate(App app, String message) {
        return Flux.defer(() -> begin(app, message)).subscribeOn(Schedulers.boundedElastic());
    }

    private Flux<String> begin(App app, String message) {
        long id = app.getId();
        workspace.ensureBaseline(id);
        Job job = new Job();
        ThrowUtils.throwIf(
                jobs.putIfAbsent(id, job) != null, ErrorCode.OPERATION_ERROR, "该应用正在生成，请先停止");
        final String request;
        try {
            int changed =
                    jdbcTemplate.update(
                            "UPDATE app SET"
                                + " generationStatus='GENERATING',generationToken=?,generationError='',updateTime=?"
                                + " WHERE id=? AND isDelete=0 AND generationStatus<>'GENERATING'",
                            job.token,
                            LocalDateTime.now(),
                            id);
            ThrowUtils.throwIf(changed != 1, ErrorCode.OPERATION_ERROR, "该应用正在生成，请刷新状态");
            // 先认领任务再读取上下文，阻止回退/发布与上下文读取之间发生竞争。
            request = context(workspace.requireApp(id), message);
        } catch (RuntimeException error) {
            jobs.remove(id, job);
            fail(id, job, error);
            throw error;
        }
        return Flux.defer(
                        () ->
                                generator.generateCodeStream(
                                        request,
                                        CodeGenTypeEnum.getEnumByValue(app.getCodeGenType())))
                .timeout(Duration.ofSeconds(120))
                .doOnNext(
                        chunk -> {
                            ThrowUtils.throwIf(
                                    job.code.length() + chunk.length() > 2_000_000,
                                    ErrorCode.OPERATION_ERROR,
                                    "模型输出过长，已停止");
                            job.code.append(chunk);
                        })
                .takeUntilOther(job.stop.asMono())
                .concatWith(Mono.defer(() -> finish(app, message, job)))
                .doOnError(error -> fail(id, job, error))
                .doOnCancel(() -> markStopped(id, job))
                .doFinally(signal -> jobs.remove(id, job));
    }

    private Mono<String> finish(App app, String message, Job job) {
        if (job.stopped.get()) return Mono.error(new GenerationStoppedException());
        workspace.complete(
                app.getId(), job.token, parse(job.code.toString(), app.getCodeGenType()), message);
        return Mono.empty();
    }

    private void fail(long id, Job job, Throwable error) {
        String state = job.stopped.get() ? "STOPPED" : "FAILED";
        String reason = job.stopped.get() ? "生成已停止" : safeError(error);
        jdbcTemplate.update(
                "UPDATE app SET"
                    + " generationStatus=?,generationError=?,generationToken='',updateTime=? WHERE"
                    + " id=? AND generationToken=? AND generationStatus='GENERATING'",
                state,
                reason,
                LocalDateTime.now(),
                id,
                job.token);
        if (!job.stopped.get()) log.warn("应用生成失败 appId={}", id, error);
    }

    private String context(App app, String message) {
        Map<String, String> files = workspace.previewFiles(app);
        if (files.isEmpty()) return message;
        StringBuilder context =
                new StringBuilder("在当前版本的代码上按用户要求修改。仍然返回完整的 HTML、CSS 和 JavaScript 代码块。当前文件：\n");
        files.forEach(
                (name, code) ->
                        context.append("\n文件 ")
                                .append(name)
                                .append("\n")
                                .append(code)
                                .append("\n"));
        ThrowUtils.throwIf(context.length() > 120000, ErrorCode.OPERATION_ERROR, "当前代码过大，暂不支持继续生成");
        return context.append("\n用户本轮要求：\n").append(message).toString();
    }

    public boolean stop(long id, User user) {
        App app = workspace.requireApp(id);
        AppPolicy.owner(app, user);
        Job job = jobs.get(id);
        if (job != null) {
            markStopped(id, job);
            job.stop.tryEmitValue("stop");
        } else
            jdbcTemplate.update(
                    "UPDATE app SET"
                        + " generationStatus='STOPPED',generationToken='',generationError='生成已停止'"
                        + " WHERE id=? AND generationStatus='GENERATING'",
                    id);
        return true;
    }

    private void markStopped(long id, Job job) {
        job.stopped.set(true);
        jdbcTemplate.update(
                "UPDATE app SET"
                    + " generationStatus='STOPPED',generationToken='',generationError='生成已停止',updateTime=?"
                    + " WHERE id=? AND generationToken=? AND generationStatus='GENERATING'",
                LocalDateTime.now(),
                id,
                job.token);
    }

    public static Map<String, String> parse(String text, String type) {
        CodeGenTypeEnum codeType = CodeGenTypeEnum.getEnumByValue(type);
        ThrowUtils.throwIf(codeType == null, ErrorCode.PARAMS_ERROR, "不支持的生成类型");
        Object parsed = CodeParserExecutor.executeParser(text, codeType);
        Map<String, String> files = new LinkedHashMap<>();
        if (parsed instanceof HtmlCodeResult html)
            files.put("index.html", Objects.toString(html.getHtmlCode(), ""));
        if (parsed instanceof MultiFileCodeResult multi) {
            files.put("index.html", Objects.toString(multi.getHtmlCode(), ""));
            files.put("style.css", Objects.toString(multi.getCssCode(), ""));
            files.put("script.js", Objects.toString(multi.getJsCode(), ""));
        }
        AppWorkspaceService.validateFiles(files);
        return files;
    }

    public static String safeError(Throwable error) {
        if (error instanceof BusinessException) return error.getMessage();
        for (Throwable e = error; e != null; e = e.getCause()) {
            if (e instanceof java.net.SocketTimeoutException
                    || e.getClass().getSimpleName().contains("Timeout"))
                return "连接模型服务超时，请检查后端网络后重试";
        }
        return "模型生成或版本保存失败，请稍后重试";
    }

    public static class GenerationStoppedException extends RuntimeException {
        public GenerationStoppedException() {
            super("生成已停止");
        }
    }
}
