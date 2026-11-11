package com.yuelei.appforge.ai.tools;

import cn.hutool.json.JSONObject;

import com.yuelei.appforge.workspace.vue.ProjectTask;

import dev.langchain4j.agent.tool.*;

import org.springframework.stereotype.Component;

@Component
public class FileWriteTool extends BaseTool {
    public FileWriteTool() {
        this(null);
    }

    private FileWriteTool(ProjectTask task) {
        super(task);
    }

    @Override
    public String getToolName() {
        return "writeFile";
    }

    @Override
    public String getDisplayName() {
        return "写入文件";
    }

    @Override
    public BaseTool bind(ProjectTask task) {
        return new FileWriteTool(task);
    }

    @Tool("文件写入工具：创建文件或完整重写文件。已有文件必须先读取目录和文件；平台配置采用目录工具给出的固定范本")
    public String writeFile(@P("相对项目根目录的文件路径") String path, @P("完整 UTF-8 文件内容") String content) {
        return action("write_file", path, () -> task.write(path, content));
    }

    @Override
    public String generateToolExecutedResult(JSONObject args) {
        return heading(args) + code(args.getStr("content"), path(args));
    }
}
