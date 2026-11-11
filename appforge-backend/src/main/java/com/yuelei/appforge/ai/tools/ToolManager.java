package com.yuelei.appforge.ai.tools;
import com.yuelei.appforge.workspace.vue.ProjectTask;
import org.springframework.stereotype.Component;
import java.util.*;
/** Registered Spring templates provide formatting; bound arrays isolate concurrent app executions. */
@Component
public class ToolManager {
    private final Map<String,BaseTool> tools=new LinkedHashMap<>();
    public ToolManager(BaseTool[] all){for(BaseTool tool:all)if(tools.putIfAbsent(tool.getToolName(),tool)!=null)throw new IllegalStateException("工具名称重复："+tool.getToolName());}
    public BaseTool getTool(String name){return tools.get("readDir".equals(name)?"readDirectory":name);}
    public BaseTool[] getAllTools(){return tools.values().toArray(BaseTool[]::new);}
    public BaseTool[] getAllTools(ProjectTask task){return tools.values().stream().map(tool->tool.bind(task)).toArray(BaseTool[]::new);}
    public static ToolManager defaults(){return new ToolManager(new BaseTool[]{new FileWriteTool(),new FileReadTool(),new FileModifyTool(),new FileDeleteTool(),new FileDirReadTool()});}
}
