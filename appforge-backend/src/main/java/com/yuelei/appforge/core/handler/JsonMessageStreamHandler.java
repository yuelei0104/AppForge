package com.yuelei.appforge.core.handler;

import cn.hutool.json.JSONUtil;

import com.yuelei.appforge.ai.model.message.*;

import lombok.extern.slf4j.Slf4j;

import reactor.core.publisher.Flux;

import java.util.*;
import java.util.function.Consumer;

/** JSON 块已由适配器封装完整；工具参数分片不在这里解析。历史仍由应用服务统一落库。 */
@Slf4j
public final class JsonMessageStreamHandler {
    private final com.yuelei.appforge.ai.tools.ToolManager tools;

    public JsonMessageStreamHandler() {
        this(com.yuelei.appforge.ai.tools.ToolManager.defaults());
    }

    public JsonMessageStreamHandler(com.yuelei.appforge.ai.tools.ToolManager tools) {
        this.tools = tools;
    }

    public Flux<String> handle(Flux<String> source, Consumer<String> history) {
        return Flux.defer(
                () -> {
                    Set<String> selected = new HashSet<>(), executed = new HashSet<>();
                    return source.map(
                                    chunk -> {
                                        var message = JSONUtil.parseObj(chunk);
                                        var type =
                                                StreamMessageTypeEnum.getEnumByValue(
                                                        message.getStr("type"));
                                        if (type == null) {
                                            log.debug("忽略未知工具流消息类型");
                                            return "";
                                        }
                                        if (type == StreamMessageTypeEnum.TOOL_REQUEST) {
                                            var request = message.toBean(ToolRequestMessage.class);
                                            String id =
                                                    Objects.toString(
                                                            request.getId(),
                                                            request.getName()
                                                                    + ":"
                                                                    + request.getIndex());
                                            var tool = tools.getTool(request.getName());
                                            return selected.add(id)
                                                    ? tool == null
                                                            ? "\n\n[选择工具] 未知工具\n\n"
                                                            : tool.generateToolRequestResponse()
                                                    : "";
                                        }
                                        String output;
                                        if (type == StreamMessageTypeEnum.AI_RESPONSE)
                                            output = Objects.toString(message.getStr("data"), "");
                                        else {
                                            var result = message.toBean(ToolExecutedMessage.class);
                                            String id =
                                                    Objects.toString(
                                                            result.getId(),
                                                            result.getName()
                                                                    + ":"
                                                                    + result.getArguments());
                                            if (!executed.add(id)) return "";
                                            cn.hutool.json.JSONObject args;
                                            try {
                                                args =
                                                        JSONUtil.parseObj(
                                                                Objects.toString(
                                                                        result.getArguments(),
                                                                        "{}"));
                                            } catch (RuntimeException ignored) {
                                                args = new cn.hutool.json.JSONObject();
                                            }
                                            var tool = tools.getTool(result.getName());
                                            if (tool == null) output = "\n\n[工具失败] 未知工具\n\n";
                                            else {
                                                args.set("content", result.getContent());
                                                output =
                                                        "\n\n"
                                                                + (result.isFailed()
                                                                        ? tool
                                                                                .generateFailureResponse(
                                                                                        args)
                                                                        : tool
                                                                                .generateToolExecutedResult(
                                                                                        args))
                                                                + "\n\n";
                                            }
                                        }
                                        if (!output.isEmpty()) history.accept(output);
                                        return output;
                                    })
                            .filter(value -> !value.isEmpty());
                });
    }
}
