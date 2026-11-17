package com.yuelei.workspace;

import com.yuelei.ai.AiCodeGeneratorServiceFactory;
import com.yuelei.ai.model.message.AiResponseMessage;
import com.yuelei.core.AiCodeGeneratorFacade;
import com.yuelei.enums.CodeGenTypeEnum;
import com.yuelei.workspace.GenerationMode;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Service
public class VueProjectRunner {
    @Resource private AiCodeGeneratorServiceFactory factory;
    @Resource private AiCodeGeneratorFacade facade;

    @Resource private VueProjectBuilder builder;
    @Resource private com.yuelei.ai.tools.ToolManager toolManager;

    public Flux<String> stream(
            long appId,
            long currentMessageId,
            String request,
            ProjectTask task) {
        return stream(appId, currentMessageId, request, task, GenerationMode.EDIT);
    }

    public Flux<String> stream(
            long appId,
            long currentMessageId,
            String request,
            ProjectTask task,
            GenerationMode mode) {
        return Flux.defer(
                () -> {
                    String prompt = facade.contextForProject(request, appId, currentMessageId);
                    var manager =
                            toolManager == null
                                    ? com.yuelei.ai.tools.ToolManager.defaults()
                                    : toolManager;
                    var service =
                            factory.vueProjectService(
                                    manager.getTools(task, mode),
                                    new BudgetedStreamingModel(
                                            factory.getStreamingChatModel(
                                                    CodeGenTypeEnum.VUE_PROJECT),
                                            task));
                    var stream =
                            mode == GenerationMode.CREATE
                                    ? service.create(prompt)
                                    : service.edit(prompt);
                    return com.yuelei.core.adapter.TokenStreamFluxAdapter.adapt(
                                    stream, task)
                            .concatWith(
                                    Flux.defer(
                                            () -> {
                                                task.check();
                                                return reactor.core.publisher.Mono.fromCallable(
                                                                () -> {
                                                                    task.check();
                                                                    task.complete(
                                                                            builder.build(task));
                                                                    return cn.hutool.json.JSONUtil.toJsonStr(new AiResponseMessage("\n" + "网站已生成完成。"));
                                                                })
                                                        .subscribeOn(Schedulers.boundedElastic());
                                            }))
                            .doOnCancel(task::cancel);
                });
    }

    public void close(ProjectTask task) {
        task.cancel();
        builder.removeContainer(task);
        task.close();
    }
}
