package com.yuelei.appforge.workspace;

import org.jsoup.Jsoup;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** 私有页面在 sandbox 下的子资源不携带登录 Cookie，将三个本地代码文件嵌入响应。 */
public final class AppPageRenderer {
    private AppPageRenderer() {}

    public static String inlinePrivateAssets(Map<String, String> files) {
        var document = Jsoup.parse(files.getOrDefault("index.html", ""));
        document.outputSettings().prettyPrint(false);
        for (var element : document.select("link[href],script[src]")) {
            String attribute = element.tagName().equals("link") ? "href" : "src";
            String target = element.attr(attribute).split("[?#]", 2)[0];
            if (target.startsWith("./")) target = target.substring(2);
            String expected = attribute.equals("href") ? "style.css" : "script.js";
            if (!expected.equals(target) || !files.containsKey(target)) continue;
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
