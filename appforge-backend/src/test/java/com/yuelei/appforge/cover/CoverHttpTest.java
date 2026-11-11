package com.yuelei.appforge.cover;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cn.hutool.json.JSONUtil;

import com.yuelei.appforge.controller.AppCoverController;
import com.yuelei.appforge.exception.*;
import com.yuelei.appforge.service.UserService;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.*;
import java.util.*;

class CoverHttpTest extends CoverTestDatabase {
    @Test
    void renderRequiresLiveTokenAndImageRequiresCurrentReadPermission() throws Exception {
        var users = mock(UserService.class);
        when(users.getLoginUser(any())).thenThrow(new BusinessException(ErrorCode.NOT_LOGIN_ERROR));
        var store = mock(CoverObjectStore.class);
        var worker = mock(AppCoverWorker.class);
        var mvc =
                MockMvcBuilders.standaloneSetup(new AppCoverController(tasks, worker, store, users))
                        .build();
        var a = claim();
        mvc.perform(get("/internal/cover/render/" + a.id())).andExpect(status().isNotFound());
        mvc.perform(get("/internal/cover/render/" + a.id()).param("token", "bad"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/internal/cover/render/" + a.id()).param("token", a.renderToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-AppForge-Cover", a.id() + ":" + a.execution()))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
        tasks.publish(a);
        mvc.perform(get("/app/cover/image").param("appId", "1")).andExpect(status().isNotFound());
        verify(store, never()).read(anyString());
        doReturn(owner).when(users).getLoginUser(any());
        when(store.read(a.objectKey())).thenReturn(new byte[] {1, 2, 3});
        mvc.perform(get("/app/cover/image").param("appId", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        jdbc.update("UPDATE app SET isDelete=1 WHERE id=1");
        mvc.perform(get("/app/cover/image").param("appId", "1")).andExpect(status().isNotFound());
    }

    @Test
    void renderEvidenceContainsRealCssScriptAndConstrainedSandbox() throws Exception {
        jdbc.update("DELETE FROM app_version");
        files(
                1,
                1,
                Map.of(
                        "index.html",
                        "<!doctype html><meta charset=utf-8><link rel=stylesheet"
                            + " href=style.css><h1>封面测试博客</h1><button>更改</button><script"
                            + " src=script.js></script>",
                        "style.css",
                        "body{background:#edf2ff;font-family:sans-serif}h1{color:#315cd6;font-size:54px}",
                        "script.js",
                        "document.querySelector('h1').dataset.ready='yes';document.querySelector('button').onclick=()=>document.querySelector('h1').textContent='交互成功';fetch('/private-probe').catch(()=>{});new"
                            + " Image().src='/image-probe';"));
        var a = claim();
        var rendered = tasks.render(a.id(), a.renderToken());
        assertTrue(rendered.csp().contains("connect-src 'none'"));
        assertFalse(rendered.csp().contains("allow-same-origin"));
        assertTrue(rendered.html().contains("history.replaceState"));
        Path target = Path.of("target/cover-render-fixture.json");
        Files.createDirectories(target.getParent());
        Files.writeString(
                target,
                JSONUtil.toJsonStr(
                        Map.of(
                                "html",
                                rendered.html(),
                                "csp",
                                rendered.csp(),
                                "marker",
                                rendered.marker())));
    }
}
