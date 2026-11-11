package com.yuelei.appforge.ai.tools;
import cn.hutool.json.JSONObject;
import com.yuelei.appforge.workspace.vue.ProjectTask;
import dev.langchain4j.agent.tool.*;
import org.springframework.stereotype.Component;
@Component
public class FileModifyTool extends BaseTool {
    public FileModifyTool(){this(null);}private FileModifyTool(ProjectTask task){super(task);}
    @Override public String getToolName(){return "modifyFile";}
    @Override public String getDisplayName(){return "修改文件";}
    @Override public BaseTool bind(ProjectTask task){return new FileModifyTool(task);}
    @Tool("文件修改工具：仅将文件中唯一匹配的原文替换为新内容，先读取目录和该文件")
    public String modifyFile(@P("相对项目根目录的文件路径") String path,@P("唯一匹配的原文") String before,@P("替换后的内容") String after){return action("modify_file",path,()->task.modify(path,before,after));}
    @Override public String generateToolExecutedResult(JSONObject args){String before=args.getStr("before",args.getStr("oldContent"));String after=args.getStr("after",args.getStr("newContent"));if(before==null&&after==null)return heading(args)+code(args.getStr("content"),path(args));return heading(args)+"\n\n替换前：\n"+code(before,path(args))+"\n替换后：\n"+code(after,path(args));}
}
