package com.yuelei.appforge.ai.tools;

import cn.hutool.json.JSONObject;
import com.yuelei.appforge.workspace.vue.ProjectTask;
import com.yuelei.appforge.workspace.vue.VueProjectFiles;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Document tool contract; execution is bound to one existing isolated ProjectTask. */
public abstract class BaseTool {
    protected final ProjectTask task;
    protected BaseTool(ProjectTask task){this.task=task;}
    public abstract String getToolName();
    public abstract String getDisplayName();
    public abstract BaseTool bind(ProjectTask task);
    public String generateToolRequestResponse(){return "\n\n[选择工具] "+getDisplayName()+"\n\n";}
    public abstract String generateToolExecutedResult(JSONObject arguments);
    public String generateFailureResponse(JSONObject args){return "[工具失败] "+getDisplayName()+suffix(path(args));}
    protected String heading(JSONObject args){return "[工具调用] "+getDisplayName()+suffix(path(args));}
    private static String suffix(String path){return path.isBlank()?"":" "+path;}
    protected static String path(JSONObject args){String path=Objects.toString(args.getStr("path",args.getStr("relativeFilePath","")),"");try{if(!path.isBlank())VueProjectFiles.sourcePath(path);return path;}catch(RuntimeException invalid){return "";}}
    protected static String code(String content,String path){
        if(content==null)return "";String fence="```";var matcher=Pattern.compile("`{3,}").matcher(content);while(matcher.find())if(matcher.group().length()>=fence.length())fence="`".repeat(matcher.group().length()+1);
        int dot=path.lastIndexOf('.');String language=dot<0?"text":path.substring(dot+1).toLowerCase(Locale.ROOT);
        language=switch(language){case "js","mjs"->"javascript";case "md"->"markdown";case "svg"->"xml";default->language;};
        return "\n"+fence+language+"\n"+content+"\n"+fence+"\n";
    }
    protected String action(String operation,String path,Supplier<String> work){
        if(task==null)throw new IllegalStateException("工具尚未绑定工程任务");String id=UUID.randomUUID().toString();task.event(id,operation,path,"RUNNING","正在处理");
        try{String result=work.get();task.event(id,operation,path,"SUCCEEDED","已完成");return result;}catch(RuntimeException error){task.check();String text=error instanceof com.yuelei.appforge.exception.BusinessException?error.getMessage():"文件操作失败";task.event(id,operation,path,"FAILED",text);return "操作未完成："+text;}
    }
}
