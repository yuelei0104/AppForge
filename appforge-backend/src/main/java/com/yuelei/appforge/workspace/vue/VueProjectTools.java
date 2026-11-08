package com.yuelei.appforge.workspace.vue;

import dev.langchain4j.agent.tool.*;

import java.util.UUID;
import java.util.function.Supplier;

/** 只提供文件操作，不提供任意命令执行工具。 */
public final class VueProjectTools {
    private final ProjectTask task;

    public VueProjectTools(ProjectTask task) {
        this.task = task;
    }

    private String action(String operation, String path, Supplier<String> work) {
        String id = UUID.randomUUID().toString();
        task.event(id, operation, path, "RUNNING", "正在处理");
        try {
            String result = work.get();
            task.event(id, operation, path, "SUCCEEDED", "已完成");
            return result;
        } catch (RuntimeException error) {
            task.check();
            String text =
                    error instanceof com.yuelei.appforge.exception.BusinessException
                            ? error.getMessage()
                            : "文件操作失败";
            task.event(id, operation, path, "FAILED", text);
            return "操作未完成：" + text;
        }
    }

    @Tool("目录读取工具：返回工程源码目录和平台固定的 package.json、vite.config.js 范本，开始修改前必须调用")
    public String readDirectory() {
        return action("read_directory", "", task::directory);
    }

    @Tool("文件读取工具：读取相对路径下的当前源码，修改或删除已有文件前必须调用")
    public String readFile(@P("相对项目根目录的文件路径") String path) {
        return action("read_file", path, () -> task.read(path));
    }

    @Tool("文件写入工具：依次创建新文件或完整重写文件。package.json 和 vite.config.js 使用目录工具返回的平台固定配置")
    public String writeFile(@P("相对项目根目录的文件路径") String path, @P("完整 UTF-8 文件内容") String content) {
        return action("write_file", path, () -> task.write(path, content));
    }

    @Tool("文件修改工具：将文件中唯一匹配的原文替换为新内容，必须先读取目录和文件")
    public String modifyFile(
            @P("文件路径") String path, @P("唯一匹配的原文") String before, @P("替换后的内容") String after) {
        return action("modify_file", path, () -> task.modify(path, before, after));
    }

    @Tool("文件删除工具：删除不需要的源码文件，必须先读取目录和文件")
    public String deleteFile(@P("文件路径") String path) {
        return action("delete_file", path, () -> task.delete(path));
    }
}
