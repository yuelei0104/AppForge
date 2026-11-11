package com.yuelei.appforge.ai.tools;

import cn.hutool.json.JSONObject;

import com.yuelei.appforge.workspace.vue.ProjectTask;

import dev.langchain4j.agent.tool.*;

import org.springframework.stereotype.Component;

@Component
public class FileReadTool extends BaseTool {
    public FileReadTool() {
        this(null);
    }

    private FileReadTool(ProjectTask task) {
        super(task);
    }

    @Override
    public String getToolName() {
        return "readFile";
    }

    @Override
    public String getDisplayName() {
        return "读取文件";
    }

    @Override
    public BaseTool bind(ProjectTask task) {
        return new FileReadTool(task);
    }

    @Tool("文件读取工具：读取相对路径下的现有源码，修改或删除前必须调用")
    public String readFile(@P("相对项目根目录的文件路径") String path) {
        return action("read_file", path, () -> task.read(path));
    }

    @Override
    public String generateToolExecutedResult(JSONObject args) {
        return heading(args);
    }
}
