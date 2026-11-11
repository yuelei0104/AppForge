package com.yuelei.appforge.workspace.vue;

import com.yuelei.appforge.ai.AiCodeGeneratorServiceFactory;
import com.yuelei.appforge.core.AiCodeGeneratorFacade;

import dev.langchain4j.model.chat.StreamingChatModel;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Service
public class VueProjectRunner {
    @Resource private AiCodeGeneratorServiceFactory factory;
    @Resource private AiCodeGeneratorFacade facade;

    @Resource(name = "reasoningStreamingChatModel")
    private StreamingChatModel streamingChatModel;

    @Resource private VueProjectBuilder builder;
    @Resource private com.yuelei.appforge.ai.tools.ToolManager toolManager;

    public Flux<String> stream(
            long appId, long currentMessageId, String request, ProjectTask task) {
        return Flux.defer(
                () -> {
                    String prompt = facade.contextForProject(request, appId, currentMessageId);
                    var service =
                            factory.vueProjectService(
                                    (toolManager == null
                                                    ? com.yuelei.appforge.ai.tools.ToolManager
                                                            .defaults()
                                                    : toolManager)
                                            .getAllTools(task),
                                    new BudgetedStreamingModel(streamingChatModel, task));
                    return com.yuelei.appforge.core.adapter.TokenStreamFluxAdapter.adapt(
                                    service.generate(prompt), task)
                            .concatWith(
                                    Flux.defer(
                                            () -> {
                                                task.check();
                                                return reactor.core.publisher.Mono.fromCallable(
                                                                () -> {
                                                                    task.check();
                                                                    task.complete(
                                                                            builder.build(task));
                                                                    return cn.hutool.json.JSONUtil
                                                                            .toJsonStr(
                                                                                    new com.yuelei
                                                                                            .appforge
                                                                                            .ai
                                                                                            .model
                                                                                            .message
                                                                                            .AiResponseMessage(
                                                                                            "\n"
                                                                                                + "网站已生成完成。"));
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
