package com.yuelei.appforge.core;

import com.yuelei.appforge.ai.AiCodeGeneratorService;
import com.yuelei.appforge.core.parser.CodeParserExecutor;
import com.yuelei.appforge.core.saver.CodeFileSaverExecutor;
import com.yuelei.appforge.exception.*;
import com.yuelei.appforge.model.enums.CodeGenTypeEnum;
import com.yuelei.appforge.workspace.CancellableCodeStream;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;

import reactor.core.publisher.*;

import java.io.File;

/** 模型生成入口。应用工作台使用原始可取消流，由版本服务统一保存成功结果。 */
@Service
public class AiCodeGeneratorFacade {
    @Resource private AiCodeGeneratorService aiCodeGeneratorService;
    @Resource private CancellableCodeStream cancellableCodeStream;

    public Flux<String> generateCodeStream(String message, CodeGenTypeEnum type) {
        if (type == null)
            return Flux.error(new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的代码类型"));
        return cancellableCodeStream.stream(message, type);
    }

    public File generateAndSaveCode(String message, CodeGenTypeEnum type, Long appId) {
        ThrowUtils.throwIf(type == null, ErrorCode.PARAMS_ERROR, "不支持的代码类型");
        Object result =
                type == CodeGenTypeEnum.HTML
                        ? aiCodeGeneratorService.generateHtmlCode(message)
                        : aiCodeGeneratorService.generateMultiFileCode(message);
        return CodeFileSaverExecutor.executeSaver(result, type, appId);
    }

    /** 兼容独立的生成/保存调用；保存失败必须传递为错误，不能假报完成。 */
    public Flux<String> generateAndSaveCodeStream(
            String message, CodeGenTypeEnum type, Long appId) {
        return Flux.defer(
                () -> {
                    StringBuilder content = new StringBuilder();
                    return generateCodeStream(message, type)
                            .doOnNext(content::append)
                            .concatWith(
                                    Mono.fromRunnable(
                                                    () -> {
                                                        Object result =
                                                                CodeParserExecutor.executeParser(
                                                                        content.toString(), type);
                                                        CodeFileSaverExecutor.executeSaver(
                                                                result, type, appId);
                                                    })
                                            .then(Mono.empty()));
                });
    }
}
