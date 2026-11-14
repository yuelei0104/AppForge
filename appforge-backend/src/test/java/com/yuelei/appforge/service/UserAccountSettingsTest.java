package com.yuelei.appforge.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.mybatisflex.core.MybatisFlexBootstrap;
import com.yuelei.appforge.config.JsonConfig;
import com.yuelei.appforge.controller.UserController;
import com.yuelei.appforge.exception.BusinessException;
import com.yuelei.appforge.exception.ErrorCode;
import com.yuelei.appforge.exception.GlobalExceptionHandler;
import com.yuelei.appforge.mapper.UserMapper;
import com.yuelei.appforge.model.entity.User;
import com.yuelei.appforge.service.impl.UserServiceImpl;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

/** 实际 HTTP、服务、MyBatis-Flex 和 H2 往返，不模拟身份、密码校验或数据库写入。 */
class UserAccountSettingsTest {
    private UserServiceImpl service;
    private UserMapper mapper;
    private MockMvc mvc;
    private JdbcTemplate jdbc;
    private final com.fasterxml.jackson.databind.ObjectMapper json =
            new JsonConfig().jacksonObjectMapper(new Jackson2ObjectMapperBuilder());
    private long ownerId;
    private long otherId;

    @BeforeEach
    void setup() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL(
                "jdbc:h2:mem:"
                        + UUID.randomUUID()
                        + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute(
                "CREATE TABLE user(id bigint primary key,userAccount varchar(256)"
                    + " unique,userPassword varchar(512),userName varchar(256),userAvatar"
                    + " varchar(1024),userProfile varchar(512),userRole varchar(256),editTime"
                    + " timestamp,createTime timestamp default current_timestamp,updateTime"
                    + " timestamp default current_timestamp,isDelete int default 0)");
        mapper =
                new MybatisFlexBootstrap()
                        .setEnvironmentId("account-" + UUID.randomUUID())
                        .setDataSource(ds)
                        .addMapper(UserMapper.class)
                        .start()
                        .getMapper(UserMapper.class);
        service = new UserServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ownerId = service.userRegister("owner", "Old-Test-123!", "Old-Test-123!", "原来昵称");
        otherId = service.userRegister("other", "Other-Test-123!", "Other-Test-123!", "另一用户");
        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", service);
        mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                        .build();
    }

    private MockHttpSession login() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        service.userLogin("owner", "Old-Test-123!", request);
        return (MockHttpSession) request.getSession();
    }

    private String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    @Test
    void profileUpdateIgnoresForgedIdentityAndPersistsAcrossSessions() throws Exception {
        MockHttpSession session = login();
        mvc.perform(
                        post("/user/profile/update")
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        body(
                                                Map.of(
                                                        "userName",
                                                        "  岳蕾的创作室  ",
                                                        "id",
                                                        otherId,
                                                        "userRole",
                                                        "admin",
                                                        "userAccount",
                                                        "forged",
                                                        "userPassword",
                                                        "forged"))))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(String.valueOf(ownerId)))
                .andExpect(jsonPath("$.data.userName").value("岳蕾的创作室"))
                .andExpect(jsonPath("$.data.userRole").value("user"))
                .andExpect(jsonPath("$.data.userPassword").doesNotExist());
        assertEquals(
                "岳蕾的创作室",
                jdbc.queryForObject("SELECT userName FROM user WHERE id=?", String.class, ownerId));
        assertEquals("另一用户", mapper.selectOneById(otherId).getUserName());
        assertEquals("owner", mapper.selectOneById(ownerId).getUserAccount());
        assertEquals(
                service.getEncryptPassword("Old-Test-123!"),
                mapper.selectOneById(ownerId).getUserPassword());
        mvc.perform(get("/user/get/login").session(session))
                .andExpect(jsonPath("$.data.userName").value("岳蕾的创作室"));
        mvc.perform(get("/user/get/login").session(login()))
                .andExpect(jsonPath("$.data.userName").value("岳蕾的创作室"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "名", "昵称\n含换行", "名名名名名名名名名名名名名名名名名名名名名名名名名"})
    void rejectsInvalidNicknameWithoutWriting(String name) throws Exception {
        mvc.perform(
                        post("/user/profile/update")
                                .session(login())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(Map.of("userName", name))))
                .andExpect(jsonPath("$.code").value(40000));
        assertEquals("原来昵称", mapper.selectOneById(ownerId).getUserName());
    }

    @Test
    void missingNicknameIsRejected() throws Exception {
        mvc.perform(
                        post("/user/profile/update")
                                .session(login())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void anonymousCannotChangeProfileOrPassword() throws Exception {
        for (String path : new String[] {"profile", "password"}) {
            mvc.perform(
                            post("/user/" + path + "/update")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}"))
                    .andExpect(jsonPath("$.code").value(40100));
        }
    }

    @Test
    void passwordChangePersistsHashAndRevokesOldSessions() throws Exception {
        MockHttpSession first = login();
        MockHttpSession second = login();
        mvc.perform(
                        post("/user/password/update")
                                .session(first)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        body(
                                                Map.of(
                                                        "oldPassword",
                                                        "Old-Test-123!",
                                                        "newPassword",
                                                        "New-Test-456!",
                                                        "checkPassword",
                                                        "New-Test-456!",
                                                        "id",
                                                        otherId))))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));
        assertTrue(first.isInvalid());
        assertEquals(
                service.getEncryptPassword("New-Test-456!"),
                mapper.selectOneById(ownerId).getUserPassword());
        assertEquals(
                service.getEncryptPassword("Other-Test-123!"),
                mapper.selectOneById(otherId).getUserPassword());
        mvc.perform(get("/user/get/login").session(second))
                .andExpect(jsonPath("$.code").value(40100));
        assertTrue(second.isInvalid());
        assertThrows(
                BusinessException.class,
                () -> service.userLogin("owner", "Old-Test-123!", new MockHttpServletRequest()));
        assertEquals(
                ownerId,
                service.userLogin("owner", "New-Test-456!", new MockHttpServletRequest()).getId());
    }

    @Test
    void badPasswordInputsNeverWriteOrLogout() throws Exception {
        String[][] invalid = {
            {"wrong-old", "New-Test-456!", "New-Test-456!"},
            {"Old-Test-123!", "short", "short"},
            {"Old-Test-123!", "x".repeat(65), "x".repeat(65)},
            {"Old-Test-123!", "New-Test-456!", "different"},
            {"Old-Test-123!", "Old-Test-123!", "Old-Test-123!"},
            {"", "New-Test-456!", "New-Test-456!"}
        };
        MockHttpSession session = login();
        for (String[] row : invalid) {
            mvc.perform(
                            post("/user/password/update")
                                    .session(session)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            body(
                                                    Map.of(
                                                            "oldPassword",
                                                            row[0],
                                                            "newPassword",
                                                            row[1],
                                                            "checkPassword",
                                                            row[2]))))
                    .andExpect(jsonPath("$.code").value(40000));
            assertFalse(session.isInvalid());
            assertEquals(
                    service.getEncryptPassword("Old-Test-123!"),
                    mapper.selectOneById(ownerId).getUserPassword());
        }
    }

    @Test
    void concurrentChangeCannotOverwriteTheNewerPassword() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        service.userLogin("owner", "Old-Test-123!", request);
        UserServiceImpl racingService =
                new UserServiceImpl() {
                    @Override
                    public User getLoginUser(jakarta.servlet.http.HttpServletRequest req) {
                        User snapshot = super.getLoginUser(req);
                        jdbc.update(
                                "UPDATE user SET userPassword=? WHERE id=?",
                                getEncryptPassword("Concurrent-password-456!"),
                                ownerId);
                        return snapshot;
                    }
                };
        ReflectionTestUtils.setField(racingService, "mapper", mapper);
        BusinessException error =
                assertThrows(
                        BusinessException.class,
                        () ->
                                racingService.updateMyPassword(
                                        "Old-Test-123!",
                                        "New-Test-456!",
                                        "New-Test-456!",
                                        request));
        assertEquals(ErrorCode.OPERATION_ERROR.getCode(), error.getCode());
        assertEquals(
                service.getEncryptPassword("Concurrent-password-456!"),
                mapper.selectOneById(ownerId).getUserPassword());
    }

    @Test
    void registerHttpAcceptsNicknameAndNeverRole() throws Exception {
        var response =
                mvc.perform(
                                post("/user/register")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                body(
                                                        Map.of(
                                                                "userAccount",
                                                                "newuser",
                                                                "userPassword",
                                                                "Test-only-123!",
                                                                "checkPassword",
                                                                "Test-only-123!",
                                                                "userName",
                                                                "新创作者",
                                                                "userRole",
                                                                "admin"))))
                        .andExpect(jsonPath("$.code").value(0))
                        .andReturn()
                        .getResponse();
        User user =
                mapper.selectOneById(
                        json.readTree(response.getContentAsString()).get("data").asLong());
        assertEquals("新创作者", user.getUserName());
        assertEquals("user", user.getUserRole());
    }

    @Test
    void legacyRegistrationGetsDistinctNonAccountNicknames() {
        long first = service.userRegister("legacy1", "Test-only-123!", "Test-only-123!");
        long second = service.userRegister("legacy2", "Test-only-123!", "Test-only-123!");
        String name = mapper.selectOneById(first).getUserName();
        assertTrue(name.startsWith("创作者_"));
        assertNotEquals(name, mapper.selectOneById(second).getUserName());
        assertFalse(name.contains("legacy1"));
    }

    @Test
    void deletedUserCannotUseOldSession() throws Exception {
        MockHttpSession session = login();
        service.removeById(ownerId);
        mvc.perform(get("/user/get/login").session(session))
                .andExpect(jsonPath("$.code").value(40100));
    }
}
