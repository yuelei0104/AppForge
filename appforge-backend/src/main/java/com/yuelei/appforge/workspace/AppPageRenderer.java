package com.yuelei.appforge.workspace;

import com.yuelei.appforge.exception.ErrorCode;
import com.yuelei.appforge.exception.ThrowUtils;

import org.jsoup.Jsoup;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** 沙箱内的模块请求没有同源身份，将工程或私有页面的本地脚本和样式嵌入响应。 */
public final class AppPageRenderer {
    private AppPageRenderer() {}

    /** 将原生三文件模式的本地引用统一为真实保存的文件名，兼容已有版本的预览。 */
    public static String prepareNativeAssets(
            Map<String, String> files, boolean requireReferencedFiles) {
        var document = Jsoup.parse(files.getOrDefault("index.html", ""));
        document.outputSettings().prettyPrint(false);
        boolean stylesheet = false, script = false, changed = false;
        for (var element : document.select("link[rel~=stylesheet][href],script[src]")) {
            boolean css = element.tagName().equals("link");
            String attribute = css ? "href" : "src";
            String target = nativeAsset(element.attr(attribute), css);
            if (target == null) continue;
            String content = files.getOrDefault(target, "");
            ThrowUtils.throwIf(
                    requireReferencedFiles && content.isBlank(),
                    ErrorCode.OPERATION_ERROR,
                    "模型未返回引用的 " + target + "，未保存此次生成");
            if (!content.isBlank() && !element.attr(attribute).equals("./" + target)) {
                element.attr(attribute, "./" + target);
                changed = true;
            }
            if (css) stylesheet = true;
            else script = true;
        }
        if (!stylesheet && !files.getOrDefault("style.css", "").isBlank()) {
            document.head()
                    .appendElement("link")
                    .attr("rel", "stylesheet")
                    .attr("href", "./style.css");
            changed = true;
        }
        if (!script && !files.getOrDefault("script.js", "").isBlank()) {
            document.body().appendElement("script").attr("src", "./script.js");
            changed = true;
        }
        return changed ? document.outerHtml() : files.getOrDefault("index.html", "");
    }

    private static String nativeAsset(String reference, boolean css) {
        String path = reference.split("[?#]", 2)[0];
        if (css && path.matches("(?:\\./|/)?(?:css/)?styles?\\.css")) return "style.css";
        if (!css && path.matches("(?:\\./|/)?(?:js/)?(?:script|main|app)\\.js")) return "script.js";
        return null;
    }

    public static String inlinePrivateAssets(Map<String, String> files) {
        var document = Jsoup.parse(prepareNativeAssets(files, false));
        document.outputSettings().prettyPrint(false);
        document.select("link[rel=modulepreload]").remove();
        for (var element : document.select("link[href],script[src]")) {
            String attribute = element.tagName().equals("link") ? "href" : "src";
            String target = element.attr(attribute).split("[?#]", 2)[0];
            if (target.startsWith("./")) target = target.substring(2);
            String expected = attribute.equals("href") ? "style.css" : "script.js";
            boolean nativeAsset = expected.equals(target);
            boolean projectAsset = target.matches("assets/[A-Za-z0-9_.-]+\\.(?:css|js)");
            if ((!nativeAsset && !projectAsset) || !files.containsKey(target)) continue;
            String media = attribute.equals("href") ? "text/css" : "text/javascript";
            String encoded =
                    Base64.getEncoder()
                            .encodeToString(files.get(target).getBytes(StandardCharsets.UTF_8));
            element.attr(attribute, "data:" + media + ";charset=utf-8;base64," + encoded);
            element.removeAttr("integrity");
            element.removeAttr("crossorigin");
        }
        return document.outerHtml();
    }
}
