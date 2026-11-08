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
    @Resource private StreamingChatModel streamingChatModel;
    @Resource private VueProjectBuilder builder;

    public Flux<String> stream(
            long appId, long currentMessageId, String request, ProjectTask task) {
        return Flux.create(
                sink -> {
                    sink.onCancel(task::cancel);
                    sink.next("我会先安排页面结构，再逐个创建页面和组件，完成网站生成。\n\n");
                    try {
                        String prompt = facade.contextForProject(request, appId, currentMessageId);
                        var service =
                                factory.vueProjectService(
                                        new VueProjectTools(task),
                                        new BudgetedStreamingModel(streamingChatModel, task));
                        service.generate(prompt)
                                .onPartialResponse(
                                        text -> {
                                            /* 文件内容只经工具写入，不混入可见回复。 */
                                        })
                                .onToolExecuted(
                                        tool -> {
                                            if (!sink.isCancelled()) sink.next("");
                                        })
                                .onCompleteResponse(
                                        response ->
                                                Schedulers.boundedElastic()
                                                        .schedule(
                                                                () -> {
                                                                    try {
                                                                        task.check();
                                                                        var runtime =
                                                                                builder.build(task);
                                                                        task.complete(runtime);
                                                                        if (!sink.isCancelled()) {
                                                                            sink.next("网站已生成完成。");
                                                                            sink.complete();
                                                                        }
                                                                    } catch (Throwable error) {
                                                                        if (!sink.isCancelled())
                                                                            sink.error(error);
                                                                    }
                                                                }))
                                .onError(
                                        error -> {
                                            if (!sink.isCancelled()) sink.error(error);
                                        })
                                .start();
                    } catch (Throwable error) {
                        sink.error(error);
                    }
                });
    }

    public void close(ProjectTask task) {
        task.cancel();
        builder.removeContainer(task);
        task.close();
    }
}
