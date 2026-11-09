package com.yuelei.appforge.core.handler;

import cn.hutool.json.JSONUtil;

import com.yuelei.appforge.ai.model.message.*;

import lombok.extern.slf4j.Slf4j;

import reactor.core.publisher.Flux;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** JSON 块已由适配器封装完整；工具参数分片不在这里解析。历史仍由应用服务统一落库。 */
@Slf4j
public final class JsonMessageStreamHandler {
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
                                            return selected.add(id)
                                                    ? "\n\n[选择工具] "
                                                            + label(request.getName())
                                                            + "\n\n"
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
                                            String path = "";
                                            try {
                                                var args =
                                                        JSONUtil.parseObj(
                                                                Objects.toString(
                                                                        result.getArguments(),
                                                                        "{}"));
                                                path =
                                                        args.getStr(
                                                                "path",
                                                                args.getStr(
                                                                        "relativeFilePath", ""));
                                            } catch (RuntimeException ignored) {
                                                /* 失败工具仍可展示结果，无需解析不完整参数。 */
                                            }
                                            path = Objects.toString(path, "");
                                            try {
                                                if (!path.isBlank())
                                                    com.yuelei.appforge.workspace.vue
                                                            .VueProjectFiles.sourcePath(path);
                                            } catch (RuntimeException invalid) {
                                                path = "";
                                            }
                                            output =
                                                    "\n\n["
                                                            + (result.isFailed() ? "工具失败" : "工具调用")
                                                            + "] "
                                                            + label(result.getName())
                                                            + (path.isBlank() ? "" : " " + path)
                                                            + "\n";
                                            if (!result.isFailed() && result.getContent() != null) {
                                                String fence = "```";
                                                var runs =
                                                        Pattern.compile("`{3,}")
                                                                .matcher(result.getContent());
                                                while (runs.find())
                                                    if (runs.group().length() >= fence.length())
                                                        fence =
                                                                "`"
                                                                        .repeat(
                                                                                runs.group()
                                                                                                .length()
                                                                                        + 1);
                                                output +=
                                                        "\n"
                                                                + fence
                                                                + language(path)
                                                                + "\n"
                                                                + result.getContent()
                                                                + "\n"
                                                                + fence
                                                                + "\n";
                                            }
                                        }
                                        if (!output.isEmpty()) history.accept(output);
                                        return output;
                                    })
                            .filter(value -> !value.isEmpty());
                });
    }

    private static String label(String name) {
        return switch (Objects.toString(name, "")) {
            case "readDirectory" -> "读取目录";
            case "readFile" -> "读取文件";
            case "writeFile" -> "写入文件";
            case "modifyFile" -> "修改文件";
            case "deleteFile" -> "删除文件";
            default -> "未知工具";
        };
    }

    private static String language(String path) {
        int dot = path.lastIndexOf('.');
        String suffix = dot < 0 ? "text" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (suffix) {
            case "js", "mjs" -> "javascript";
            case "md" -> "markdown";
            case "svg" -> "xml";
            default -> suffix;
        };
    }
}
