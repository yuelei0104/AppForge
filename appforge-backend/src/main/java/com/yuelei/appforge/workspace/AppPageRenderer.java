package com.yuelei.appforge.workspace;

import org.jsoup.Jsoup;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** 沙箱内的模块请求没有同源身份，将工程或私有页面的本地脚本和样式嵌入响应。 */
public final class AppPageRenderer {
    private AppPageRenderer() {}

    public static String inlinePrivateAssets(Map<String, String> files) {
        var document = Jsoup.parse(files.getOrDefault("index.html", ""));
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
