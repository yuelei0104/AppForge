package com.yuelei.appforge.workspace;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yuelei.appforge.ai.AiCodeGenTypeRoutingService;
import com.yuelei.appforge.controller.AppController;
import com.yuelei.appforge.mapper.AppMapper;
import com.yuelei.appforge.model.entity.App;
import com.yuelei.appforge.model.enums.CodeGenTypeEnum;
import com.yuelei.appforge.service.UserService;
import com.yuelei.appforge.service.impl.AppServiceImpl;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.util.UUID;

/** 真实 JSON 请求经 DTO、服务、MyBatis-Flex INSERT 和 SELECT 往返，验证模式不被数据库默认值覆盖。 */
class AppCreationModeTest {
    @TempDir Path root;

    @ParameterizedTest
    @ValueSource(strings = {"vue_project", "multi_file", "html", ""})
    void requestedModeSurvivesHttpOrmStorageAndAppResponse(String mode) throws Exception {
        String expected = mode.isEmpty() ? "multi_file" : mode;
        var f = new AppWorkspaceTest();
        f.root = root;
        f.setup();
        f.jdbc.execute("ALTER TABLE app ALTER COLUMN codeGenType SET DEFAULT 'html'");
        var bootstrap =
                new com.mybatisflex.core.MybatisFlexBootstrap()
                        .setEnvironmentId("mode-" + UUID.randomUUID())
                        .setDataSource(f.jdbc.getDataSource())
                        .addMapper(AppMapper.class)
                        .start();
        {
            var appMapper = bootstrap.getMapper(AppMapper.class);
            var service = new AppServiceImpl();
            ReflectionTestUtils.setField(service, "mapper", appMapper);
            var users = mock(UserService.class);
            when(users.getLoginUser(any())).thenReturn(f.owner);
            when(users.getById(any(java.io.Serializable.class))).thenReturn(f.owner);
            ReflectionTestUtils.setField(service, "workspace", f.workspace);
            ReflectionTestUtils.setField(service, "userService", users);
            var routing = mock(AiCodeGenTypeRoutingService.class);
            when(routing.routeCodeGenType(anyString()))
                    .thenReturn(
                            switch (expected) {
                                case "html" -> CodeGenTypeEnum.HTML;
                                case "vue_project" -> CodeGenTypeEnum.VUE_PROJECT;
                                default -> CodeGenTypeEnum.MULTI_FILE;
                            });
            ReflectionTestUtils.setField(service, "aiCodeGenTypeRoutingService", routing);
            var controller = new AppController();
            ReflectionTestUtils.setField(controller, "appService", service);
            ReflectionTestUtils.setField(controller, "userService", users);
            var mvc = MockMvcBuilders.standaloneSetup(controller).build();
            var response =
                    mvc.perform(
                                    post("/app/add")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(
                                                    "{\"appName\":\"模式验证\",\"initPrompt\":\"创建中文博客\",\"codeGenType\":\""
                                                            + mode
                                                            + "\"}"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.code").value(0))
                            .andReturn()
                            .getResponse();
            long id =
                    cn.hutool.json.JSONUtil.parseObj(response.getContentAsString()).getLong("data");
            App stored = appMapper.selectOneById(id);
            assertNotNull(stored);
            assertEquals(expected, stored.getCodeGenType());
            assertEquals(
                    expected,
                    f.jdbc.queryForObject(
                            "SELECT codeGenType FROM app WHERE id=?", String.class, id));
            mvc.perform(get("/app/get/vo").param("id", String.valueOf(id)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.codeGenType").value(expected))
                    .andExpect(jsonPath("$.data.currentVersion").value(0));
        }
    }
}
