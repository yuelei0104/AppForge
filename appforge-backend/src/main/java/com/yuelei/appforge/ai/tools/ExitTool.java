package com.yuelei.appforge.ai.tools;

import cn.hutool.json.JSONObject;
import com.yuelei.appforge.workspace.vue.ProjectTask;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExitTool extends BaseTool {

    // Spring 创建工具模板时使用
    public ExitTool() {
        this(null);
    }

    // 为当前工程任务创建独立的工具实例
    private ExitTool(ProjectTask task) {
        super(task);
    }

    @Override
    public String getToolName() {
        return "exit";
    }

    @Override
    public String getDisplayName() {
        return "退出工具调用";
    }

    @Override
    public BaseTool bind(ProjectTask task) {
        return new ExitTool(task);
    }

    /**
     * 当任务完成或无需继续使用工具时，提示模型结束工具调用。
     *
     * @return 退出确认信息
     */
    @Tool("当任务已完成或无需继续调用工具时，使用此工具结束操作，并输出最终结果")
    public String exit() {
        log.info("AI 请求退出工具调用");
        return "任务已结束，请停止调用工具并输出最终结果。";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        return "\n\n[执行结束]\n\n";
    }
}
