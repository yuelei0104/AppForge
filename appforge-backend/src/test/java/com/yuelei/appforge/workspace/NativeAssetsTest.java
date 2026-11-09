package com.yuelei.appforge.workspace;

import static org.junit.jupiter.api.Assertions.*;

import com.yuelei.appforge.exception.BusinessException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import reactor.core.publisher.Flux;

import java.nio.file.*;
import java.util.*;

class NativeAssetsTest {
    @TempDir Path root;

    static Map<String, String> files() {
        return Map.of(
                "index.html",
                "<!doctype html><html><head><meta charset='utf-8'><link rel='stylesheet'"
                    + " href='/css/styles.css?v=1'></head><body><h1>原生网页预览</h1><button"
                    + " id='add'>增加</button><p id='count'>0</p><script"
                    + " src='/js/main.js'></script></body></html>",
                "style.css",
                "body{background:rgb(238, 245,"
                    + " 255);font-family:sans-serif;padding:24px}h1{color:rgb(30, 64,"
                    + " 175)}button{padding:12px 24px}",
                "script.js",
                "document.querySelector('#add').addEventListener('click',()=>{document.querySelector('#count').textContent=String(Number(document.querySelector('#count').textContent)+1)})");
    }

    @Test
    void parsesFileNamesOnFenceHeadersAndUsesTheSavedResourceNames() {
        String text =
                "```html index.html\n"
                        + files().get("index.html")
                        + "\n```\n```css styles.css\n"
                        + files().get("style.css")
                        + "\n```\n```javascript main.js\n"
                        + files().get("script.js")
                        + "\n```";
        var result = AppGenerationService.parse(text, "multi_file");
        assertEquals(files().get("style.css"), result.get("style.css"));
        assertEquals(files().get("script.js"), result.get("script.js"));
        assertTrue(result.get("index.html").contains("./style.css"));
        assertTrue(result.get("index.html").contains("./script.js"));
    }

    @Test
    void refusesACompletedModelReplyWithMissingReferencedFiles() {
        for (String name : List.of("style.css", "script.js")) {
            String html =
                    name.equals("style.css")
                            ? "<link rel='stylesheet' href='style.css'><h1>网站</h1>"
                            : "<h1>网站</h1><script src='script.js'></script>";
            var error =
                    assertThrows(
                            BusinessException.class,
                            () ->
                                    AppGenerationService.parse(
                                            "```html\n" + html + "\n```", "multi_file"));
            assertTrue(error.getMessage().contains(name));
        }
    }

    @Test
    void rejectsIncompleteAssetsWithoutReplacingTheLastSuccessfulVersion() {
        var f = new AppWorkspaceTest();
        f.root = root;
        f.setup();
        f.snapshot("one", "<h1>原版本</h1>");
        var before = f.workspace.files(f.id, 1);
        var generation =
                f.generation(
                        Flux.just(
                                "```html\n"
                                    + "<link rel='stylesheet' href='style.css'><h1>缺少样式</h1>\n"
                                    + "```"));
        assertThrows(
                BusinessException.class,
                () -> generation.generate(f.workspace.requireApp(f.id), "更新").blockLast());
        assertEquals(1, f.workspace.requireApp(f.id).getCurrentVersion());
        assertEquals(before, f.workspace.files(f.id, 1));
        assertEquals("FAILED", f.workspace.requireApp(f.id).getGenerationStatus());
    }

    @Test
    void insertsMissingReferencesWhenTheModelDidReturnSeparateFiles() {
        var map = new HashMap<>(files());
        map.put("index.html", "<h1>网站</h1>");
        String html = AppPageRenderer.prepareNativeAssets(map, true);
        assertTrue(html.contains("./style.css"));
        assertTrue(html.contains("./script.js"));
    }

    @Test
    void repairsPrivateAliasesWithoutChangingExternalReferences() {
        var map = new HashMap<>(files());
        map.put(
                "index.html",
                map.get("index.html")
                        .replace(
                                "</head>",
                                "<link rel='stylesheet'"
                                    + " href='https://example.com/style.css'></head>"));
        var original = new HashMap<>(map);
        String html = AppPageRenderer.inlinePrivateAssets(map);
        assertTrue(html.contains("data:text/css"));
        assertTrue(html.contains("data:text/javascript"));
        assertTrue(html.contains("https://example.com/style.css"));
        assertFalse(html.contains("/css/styles.css"));
        assertFalse(html.contains("/js/main.js"));
        assertEquals(original, map);
    }

    @Test
    void plainHtmlWithoutExternalAssetsRemainsUnchanged() {
        assertEquals(
                "<h1>中文页面</h1>",
                AppPageRenderer.prepareNativeAssets(Map.of("index.html", "<h1>中文页面</h1>"), true));
        assertEquals(
                "<h1>页面</h1>",
                AppGenerationService.parse("```html index.html\n<h1>页面</h1>\n```", "html")
                        .get("index.html"));
    }

    @Test
    void writesActualRenderedFixtureForBrowserValidation() throws Exception {
        Path output = Path.of("target/native-preview-fixture/index.html");
        Files.createDirectories(output.getParent());
        Files.writeString(output, AppPageRenderer.inlinePrivateAssets(files()));
    }
}
