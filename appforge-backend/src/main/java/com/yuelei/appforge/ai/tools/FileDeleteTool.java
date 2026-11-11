package com.yuelei.appforge.ai.tools;
import cn.hutool.json.JSONObject;
import com.yuelei.appforge.workspace.vue.ProjectTask;
import dev.langchain4j.agent.tool.*;
import org.springframework.stereotype.Component;
@Component
public class FileDeleteTool extends BaseTool {
    public FileDeleteTool(){this(null);}private FileDeleteTool(ProjectTask task){super(task);}
    @Override public String getToolName(){return "deleteFile";}
    @Override public String getDisplayName(){return "删除文件";}
    @Override public BaseTool bind(ProjectTask task){return new FileDeleteTool(task);}
    @Tool("文件删除工具：删除不再需要的源码文件，先读取目录和文件；不得删除入口及重要配置文件")
    public String deleteFile(@P("相对项目根目录的文件路径") String path){return action("delete_file",path,()->task.delete(path));}
    @Override public String generateToolExecutedResult(JSONObject args){return heading(args);}
}
