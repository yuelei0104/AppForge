package com.yuelei.appforge.ai.tools;

import cn.hutool.json.JSONObject;

import com.yuelei.appforge.workspace.vue.ProjectTask;

import dev.langchain4j.agent.tool.*;

import org.springframework.stereotype.Component;

@Component
public class FileDirReadTool extends BaseTool {
    public FileDirReadTool() {
        this(null);
    }

    private FileDirReadTool(ProjectTask task) {
        super(task);
    }

    @Override
    public String getToolName() {
        return "readDirectory";
    }

    @Override
    public String getDisplayName() {
        return "读取目录";
    }

    @Override
    public BaseTool bind(ProjectTask task) {
        return new FileDirReadTool(task);
    }

    @Tool("目录读取工具：递归列出工程允许操作的源码文件并提供平台固定的 package.json 和 vite.config.js；修改前必须调用")
    public String readDirectory() {
        return action("read_directory", "", task::directory);
    }

    @Override
    public String generateToolExecutedResult(JSONObject args) {
        String directory = args.getStr("relativeDirPath", "");
        return "[工具调用] "
                + getDisplayName()
                + " "
                + (directory == null || directory.isBlank()
                        ? "根目录"
                        : path(new JSONObject().set("path", directory)));
    }
}
