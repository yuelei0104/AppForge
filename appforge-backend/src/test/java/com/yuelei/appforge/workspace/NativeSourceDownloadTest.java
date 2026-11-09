package com.yuelei.appforge.workspace;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.yuelei.appforge.controller.AppController;
import com.yuelei.appforge.controller.StaticResourceController;
import com.yuelei.appforge.exception.BusinessException;
import com.yuelei.appforge.service.UserService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;

class NativeSourceDownloadTest {
    @TempDir Path root;

    Map<String, String> unzip(byte[] bytes) throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        try (var zip =
                new ZipInputStream(
                        new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null)
                files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
        }
        return files;
    }

    AppWorkspaceTest fixture(String mode, Map<String, String> files) {
        var f = new AppWorkspaceTest();
        f.root = root.resolve(UUID.randomUUID().toString());
        f.setup();
        f.jdbc.update(
                "UPDATE app SET codeGenType=?,generationStatus='GENERATING',generationToken='one'"
                    + " WHERE id=?",
                mode,
                f.id);
        f.workspace.complete(f.id, "one", files, "网站");
        return f;
    }

    AppController controller(AppWorkspaceTest f, UserService users) {
        var c = new AppController();
        ReflectionTestUtils.setField(c, "workspace", f.workspace);
        ReflectionTestUtils.setField(c, "userService", users);
        return c;
    }

    @Test
    void ownerAndAdminCanDownloadNativeSourceWithoutConvertingTheApp() throws Exception {
        for (String mode : List.of("html", "multi_file")) {
            var source =
                    mode.equals("html")
                            ? Map.of("index.html", "<h1>中文网站</h1>")
                            : NativeAssetsTest.files();
            var f = fixture(mode, source);
            var users = mock(UserService.class);
            var c = controller(f, users);
            for (var user : List.of(f.owner, f.admin)) {
                when(users.getLoginUser(any())).thenReturn(user);
                var response = c.downloadSource(f.id, new MockHttpServletRequest());
                var bytes = response.getBody().getInputStream().readAllBytes();
                var exported = unzip(bytes);
                assertEquals(source.keySet(), exported.keySet());
                assertEquals("application/zip", response.getHeaders().getContentType().toString());
                assertFalse(exported.containsKey("package.json"));
                if (mode.equals("multi_file")) {
                    assertTrue(exported.get("index.html").contains("./style.css"));
                    assertTrue(exported.get("index.html").contains("./script.js"));
                    Path out = Path.of("target/native-source-fixture.zip");
                    Files.write(out, bytes);
                }
                assertEquals(source, f.workspace.files(f.id, 1));
                assertEquals(mode, f.workspace.requireApp(f.id).getCodeGenType());
            }
            when(users.getLoginUser(any())).thenReturn(f.other);
            assertThrows(
                    BusinessException.class,
                    () -> c.downloadSource(f.id, new MockHttpServletRequest()));
        }
    }

    @Test
    void noSuccessfulVersionHasNoDownloadAndUnsafeArchivePathsAreRejected() {
        var f = new AppWorkspaceTest();
        f.root = root;
        f.setup();
        var users = mock(UserService.class);
        when(users.getLoginUser(any())).thenReturn(f.owner);
        var c = controller(f, users);
        assertThrows(
                BusinessException.class,
                () -> c.downloadSource(f.id, new MockHttpServletRequest()));
        f.jdbc.update(
                "UPDATE app SET generationStatus='GENERATING',generationToken='one' WHERE id=?",
                f.id);
        f.workspace.complete(f.id, "one", NativeAssetsTest.files(), "网站");
        f.jdbc.update(
                "UPDATE app_version SET codeFiles=? WHERE appId=?",
                "{\"index.html\":\"<h1>网站</h1>\",\"../secret.txt\":\"secret\"}",
                f.id);
        assertThrows(
                BusinessException.class,
                () -> c.downloadSource(f.id, new MockHttpServletRequest()));
    }

    @Test
    void publicPreviewAlsoUsesTheCanonicalLocalAssets() throws Exception {
        var f = fixture("multi_file", NativeAssetsTest.files());
        f.jdbc.update("UPDATE app SET visibility='PUBLIC' WHERE id=?", f.id);
        var users = mock(UserService.class);
        var c = new StaticResourceController();
        ReflectionTestUtils.setField(c, "workspace", f.workspace);
        ReflectionTestUtils.setField(c, "userService", users);
        String html =
                new String(
                        c.getPreviewIndex(f.id, new MockHttpServletRequest())
                                .getBody()
                                .getInputStream()
                                .readAllBytes(),
                        StandardCharsets.UTF_8);
        assertTrue(html.contains("./style.css"));
        assertTrue(html.contains("./script.js"));
    }
}
