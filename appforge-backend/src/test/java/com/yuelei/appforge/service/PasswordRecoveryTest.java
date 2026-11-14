package com.yuelei.appforge.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.mybatisflex.core.MybatisFlexBootstrap;
import com.mybatisflex.core.query.QueryWrapper;
import com.yuelei.appforge.config.JsonConfig;
import com.yuelei.appforge.controller.PasswordRecoveryController;
import com.yuelei.appforge.controller.UserController;
import com.yuelei.appforge.exception.BusinessException;
import com.yuelei.appforge.exception.ErrorCode;
import com.yuelei.appforge.exception.GlobalExceptionHandler;
import com.yuelei.appforge.mapper.UserMapper;
import com.yuelei.appforge.model.dto.user.PasswordResetRequest;
import com.yuelei.appforge.model.entity.User;
import com.yuelei.appforge.service.impl.UserServiceImpl;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/** 实际 HTTP、H2、Redis Lua 集成；CI 必须启动隔离 Redis 后运行。 */
@EnabledIfEnvironmentVariable(named = "APPFORGE_PASSWORD_REDIS_TEST", matches = "true")
class PasswordRecoveryTest {
    private static LettuceConnectionFactory connection;
    private static StringRedisTemplate redis;
    private final com.fasterxml.jackson.databind.ObjectMapper json =
            new JsonConfig().jacksonObjectMapper(new Jackson2ObjectMapperBuilder());
    private UserServiceImpl users;
    private PasswordRecoveryService recovery;
    private UserMapper mapper;
    private JdbcTemplate jdbc;
    private MockMvc mvc;
    private long ownerId, otherId, adminId;
    private MockHttpSession admin;
    private String address;

    @BeforeAll
    static void connectRedis() {
        int port =
                Integer.parseInt(
                        System.getenv().getOrDefault("APPFORGE_PASSWORD_REDIS_PORT", "16379"));
        connection = new LettuceConnectionFactory("127.0.0.1", port);
        connection.afterPropertiesSet();
        redis = new StringRedisTemplate(connection);
        redis.afterPropertiesSet();
        try (var conn = connection.getConnection()) {
            assertEquals("PONG", conn.ping());
        }
    }

    @AfterAll
    static void closeRedis() {
        if (connection != null) connection.destroy();
    }

    @BeforeEach
    void setup() {
        address = UUID.randomUUID().toString();
        var ds = new JdbcDataSource();
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
                        .setEnvironmentId("recovery-" + UUID.randomUUID())
                        .setDataSource(ds)
                        .addMapper(UserMapper.class)
                        .start()
                        .getMapper(UserMapper.class);
        users = new UserServiceImpl();
        ReflectionTestUtils.setField(users, "mapper", mapper);
        ownerId = users.userRegister("owner", "Old-Test-123!", "Old-Test-123!", "测试创作者");
        otherId = users.userRegister("other", "Other-Test-123!", "Other-Test-123!", "其他用户");
        adminId = users.userRegister("administrator", "Admin-Test-123!", "Admin-Test-123!", "站点管理");
        jdbc.update("UPDATE user SET userRole='admin' WHERE id=?", adminId);
        admin = login("administrator", "Admin-Test-123!");
        recovery = new PasswordRecoveryService();
        ReflectionTestUtils.setField(recovery, "userService", users);
        ReflectionTestUtils.setField(recovery, "redis", redis);
        var controller = new PasswordRecoveryController();
        ReflectionTestUtils.setField(controller, "passwordRecoveryService", recovery);
        var userController = new UserController();
        ReflectionTestUtils.setField(userController, "userService", users);
        mvc =
                MockMvcBuilders.standaloneSetup(controller, userController)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                        .build();
    }

    private MockHttpSession login(String account, String password) {
        var request = new MockHttpServletRequest();
        users.userLogin(account, password, request);
        return (MockHttpSession) request.getSession();
    }

    private String key() {
        return "appforge:password-recovery:grant:" + ownerId;
    }

    private String issue() throws Exception {
        var response =
                mvc.perform(
                                post("/user/password/reset-code")
                                        .session(admin)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "userId",
                                                                ownerId,
                                                                "identityVerified",
                                                                true))))
                        .andExpect(jsonPath("$.code").value(0))
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(jsonPath("$.data.expiresInSeconds").value(600))
                        .andReturn()
                        .getResponse();
        return json.readTree(response.getContentAsString()).at("/data/resetCode").asText();
    }

    private Map<String, String> resetBody(String account, String code) {
        return Map.of(
                "userAccount",
                account,
                "resetCode",
                code,
                "newPassword",
                "New-Test-456!",
                "checkPassword",
                "New-Test-456!");
    }

    private org.springframework.test.web.servlet.ResultActions reset(Map<String, String> body)
            throws Exception {
        return mvc.perform(
                post("/user/password/reset")
                        .with(
                                r -> {
                                    r.setRemoteAddr(address);
                                    return r;
                                })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)));
    }

    @Test
    void onlyVerifiedAdminCanIssue() throws Exception {
        String body = json.writeValueAsString(Map.of("userId", ownerId, "identityVerified", true));
        mvc.perform(
                        post("/user/password/reset-code")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(jsonPath("$.code").value(40100));
        mvc.perform(
                        post("/user/password/reset-code")
                                .session(login("owner", "Old-Test-123!"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(jsonPath("$.code").value(40101));
        mvc.perform(
                        post("/user/password/reset-code")
                                .session(admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "userId",
                                                        ownerId,
                                                        "identityVerified",
                                                        false))))
                .andExpect(jsonPath("$.code").value(40000));
        assertNull(redis.opsForValue().get(key()));
    }

    @Test
    void codeIsRandomHashedAndExpires() throws Exception {
        String code = issue();
        assertTrue(code.matches("[A-Za-z0-9_-]{32}"));
        String stored = redis.opsForValue().get(key());
        assertNotNull(stored);
        assertFalse(stored.contains(code));
        assertFalse(stored.contains(mapper.selectOneById(ownerId).getUserPassword()));
        Long ttl = redis.getExpire(key());
        assertNotNull(ttl);
        assertTrue(ttl > 590 && ttl <= 600);
    }

    @Test
    void anonymousResetPersistsHashAndRevokesOldLoginsWithoutAutoLogin() throws Exception {
        MockHttpSession oldSession = login("owner", "Old-Test-123!");
        reset(resetBody("owner", issue()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertEquals(
                users.getEncryptPassword("New-Test-456!"),
                mapper.selectOneById(ownerId).getUserPassword());
        assertEquals(
                users.getEncryptPassword("Other-Test-123!"),
                mapper.selectOneById(otherId).getUserPassword());
        assertNull(redis.opsForValue().get(key()));
        assertThrows(
                BusinessException.class,
                () -> users.userLogin("owner", "Old-Test-123!", new MockHttpServletRequest()));
        assertEquals(
                ownerId,
                users.userLogin("owner", "New-Test-456!", new MockHttpServletRequest()).getId());
        mvc.perform(get("/user/get/login").session(oldSession))
                .andExpect(jsonPath("$.code").value(40100));
        mvc.perform(get("/user/get/login")).andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void wrongAccountOrCodeDoesNotBurnValidGrant() throws Exception {
        String code = issue();
        reset(resetBody("other", code)).andExpect(jsonPath("$.code").value(40000));
        reset(resetBody("owner", "x".repeat(32))).andExpect(jsonPath("$.code").value(40000));
        assertNotNull(redis.opsForValue().get(key()));
        reset(resetBody("owner", code)).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void nonexistentAndInvalidAccountsReturnSameMessage() throws Exception {
        var first = reset(resetBody("missing", "x".repeat(32))).andReturn().getResponse();
        var second = reset(resetBody("owner", "x".repeat(32))).andReturn().getResponse();
        assertEquals(
                json.readTree(first.getContentAsString()).get("message"),
                json.readTree(second.getContentAsString()).get("message"));
        assertEquals(40000, json.readTree(first.getContentAsString()).get("code").asInt());
    }

    @Test
    void blankAccountAndMalformedCodeCannotResetOrConsume() throws Exception {
        String code = issue();
        reset(resetBody("", code)).andExpect(jsonPath("$.code").value(40000));
        reset(resetBody("owner", "short")).andExpect(jsonPath("$.code").value(40000));
        assertNotNull(redis.opsForValue().get(key()));
        reset(resetBody("owner", code)).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void expirationRejectsTheCode() throws Exception {
        String code = issue();
        redis.expire(key(), Duration.ZERO);
        reset(resetBody("owner", code)).andExpect(jsonPath("$.code").value(40000));
        assertEquals(
                users.getEncryptPassword("Old-Test-123!"),
                mapper.selectOneById(ownerId).getUserPassword());
    }

    @Test
    void reissueInvalidatesPreviousCode() throws Exception {
        String old = issue(), current = issue();
        assertNotEquals(old, current);
        reset(resetBody("owner", old)).andExpect(jsonPath("$.code").value(40000));
        reset(resetBody("owner", current)).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void replayIsRejected() throws Exception {
        String code = issue();
        reset(resetBody("owner", code)).andExpect(jsonPath("$.code").value(0));
        reset(resetBody("owner", code)).andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void normalPasswordChangeInvalidatesRecoveryProof() throws Exception {
        String code = issue();
        var request = new MockHttpServletRequest();
        users.userLogin("owner", "Old-Test-123!", request);
        users.updateMyPassword(
                "Old-Test-123!", "Normal-change-123!", "Normal-change-123!", request);
        reset(resetBody("owner", code)).andExpect(jsonPath("$.code").value(40000));
        assertEquals(
                users.getEncryptPassword("Normal-change-123!"),
                mapper.selectOneById(ownerId).getUserPassword());
    }

    @Test
    void invalidNewPasswordsDoNotConsumeCode() throws Exception {
        String code = issue();
        for (String value : new String[] {"short", "x".repeat(65), "Old-Test-123!"}) {
            reset(
                            Map.of(
                                    "userAccount",
                                    "owner",
                                    "resetCode",
                                    code,
                                    "newPassword",
                                    value,
                                    "checkPassword",
                                    value))
                    .andExpect(jsonPath("$.code").value(40000));
        }
        reset(
                        Map.of(
                                "userAccount",
                                "owner",
                                "resetCode",
                                code,
                                "newPassword",
                                "New-Test-456!",
                                "checkPassword",
                                "mismatch"))
                .andExpect(jsonPath("$.code").value(40000));
        reset(resetBody("owner", code)).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void deletedUserCannotReset() throws Exception {
        String code = issue();
        users.removeById(ownerId);
        reset(resetBody("owner", code)).andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void adminRoleIsReadFromDatabase() throws Exception {
        jdbc.update("UPDATE user SET userRole='user' WHERE id=?", adminId);
        mvc.perform(
                        post("/user/password/reset-code")
                                .session(admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "userId",
                                                        ownerId,
                                                        "identityVerified",
                                                        true))))
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void arbitraryForwardedHeadersCannotBypassResetLimit() throws Exception {
        for (int i = 0; i < 11; i++) {
            mvc.perform(
                            post("/user/password/reset")
                                    .with(
                                            r -> {
                                                r.setRemoteAddr(address);
                                                return r;
                                            })
                                    .header("X-Forwarded-For", "192.0.2." + i)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            json.writeValueAsString(
                                                    resetBody("missing", "x".repeat(32)))))
                    .andExpect(jsonPath("$.code").value(i < 10 ? 40000 : 42900));
        }
    }

    @Test
    void issuanceIsRateLimited() throws Exception {
        for (int i = 0; i < 10; i++) issue();
        mvc.perform(
                        post("/user/password/reset-code")
                                .session(admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "userId",
                                                        ownerId,
                                                        "identityVerified",
                                                        true))))
                .andExpect(jsonPath("$.code").value(42900));
    }

    @Test
    void concurrentRedemptionSucceedsExactlyOnce() throws Exception {
        String code = issue();
        var body = json.convertValue(resetBody("owner", code), PasswordResetRequest.class);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            Callable<Boolean> run =
                    () -> {
                        gate.await();
                        var request = new MockHttpServletRequest();
                        request.setRemoteAddr(UUID.randomUUID().toString());
                        try {
                            recovery.reset(body, request);
                            return true;
                        } catch (BusinessException expected) {
                            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), expected.getCode());
                            return false;
                        }
                    };
            Future<Boolean> first = pool.submit(run), second = pool.submit(run);
            gate.countDown();
            assertNotEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        }
        assertNull(redis.opsForValue().get(key()));
    }

    @Test
    void databaseFailureDoesNotMakeConsumedCodeReusable() throws Exception {
        String code = issue();
        UserServiceImpl failing =
                new UserServiceImpl() {
                    @Override
                    public boolean update(User patch, QueryWrapper query) {
                        throw new IllegalStateException("simulated write failure");
                    }
                };
        ReflectionTestUtils.setField(failing, "mapper", mapper);
        ReflectionTestUtils.setField(recovery, "userService", failing);
        reset(resetBody("owner", code)).andExpect(jsonPath("$.code").value(50000));
        ReflectionTestUtils.setField(recovery, "userService", users);
        reset(resetBody("owner", code)).andExpect(jsonPath("$.code").value(40000));
        assertEquals(
                users.getEncryptPassword("Old-Test-123!"),
                mapper.selectOneById(ownerId).getUserPassword());
    }
}
