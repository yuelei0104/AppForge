package com.yuelei.appforge.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yuelei.appforge.aop.AuthInterceptor;
import com.yuelei.appforge.controller.ChatHistoryController;
import com.yuelei.appforge.exception.*;
import com.yuelei.appforge.model.dto.chathistory.ChatHistoryQueryRequest;
import com.yuelei.appforge.model.entity.*;
import com.yuelei.appforge.service.impl.ChatHistoryServiceImpl;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

class ChatHistoryIntegrationTest {
    JdbcTemplate jdbc;
    ChatHistoryServiceImpl service;
    User owner, admin, other;
    App app;
    final long appId = 475952860203356160L;
    final long firstId = 475952860203356170L;
    final LocalDateTime time = LocalDateTime.of(2026, 11, 7, 12, 0, 0);

    @BeforeEach
    void setup() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        jdbc = new JdbcTemplate(ds);
        // DB_CLOSE_DELAY keeps the schema while JdbcTemplate opens independent connections.
        ds.setURL(ds.getURL() + ";DB_CLOSE_DELAY=-1");
        jdbc.execute(
                "CREATE TABLE chat_history(id bigint primary key,appId bigint,userId bigint,message"
                    + " text,messageType varchar(16),createTime timestamp,updateTime"
                    + " timestamp,isDelete int default 0)");
        jdbc.execute(
                "CREATE TABLE app_attachment(id bigint primary key,chatHistoryId bigint,kind"
                    + " varchar(16),originalName varchar(255),mimeType varchar(128),sizeBytes"
                    + " bigint,url varchar(1024),isDelete int default 0)");
        owner = user(100, "user");
        admin = user(200, "admin");
        other = user(300, "user");
        app = new App();
        app.setId(appId);
        app.setUserId(owner.getId());
        AppService apps =
                (AppService)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class[] {AppService.class},
                                (p, m, a) ->
                                        m.getName().equals("getById") && Objects.equals(a[0], appId)
                                                ? app
                                                : null);
        service = new ChatHistoryServiceImpl();
        ReflectionTestUtils.setField(service, "appService", apps);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
    }

    User user(long id, String role) {
        User user = new User();
        user.setId(id);
        user.setUserRole(role);
        return user;
    }

    void insert(long id, long targetApp, LocalDateTime created, int deleted) {
        jdbc.update(
                "INSERT INTO"
                    + " chat_history(id,appId,userId,message,messageType,createTime,updateTime,isDelete)"
                    + " VALUES(?,?,?,?,?,?,?,?)",
                id,
                targetApp,
                100,
                "message " + id,
                "user",
                created,
                created,
                deleted);
    }

    @Test
    void compositeCursorReturnsEverySameSecondMessageExactlyOnce() {
        for (int i = 0; i < 25; i++) insert(firstId + i, appId, time, 0);
        insert(firstId + 100, appId + 1, time, 0);
        insert(firstId + 101, appId, time, 1);
        var ids = new ArrayList<Long>();
        LocalDateTime cursorTime = null;
        Long cursorId = null;
        for (int page = 0; page < 3; page++) {
            var result = service.listAppChatHistoryByPage(appId, 10, cursorTime, cursorId, owner);
            assertEquals(25, result.getTotalRow());
            assertEquals(page < 2 ? 10 : 5, result.getRecords().size());
            ids.addAll(result.getRecords().stream().map(ChatHistory::getId).toList());
            var oldest = result.getRecords().getLast();
            cursorTime = oldest.getCreateTime();
            cursorId = oldest.getId();
        }
        assertEquals(25, new HashSet<>(ids).size());
        for (int i = 0; i < 25; i++) assertEquals(firstId + 24 - i, ids.get(i));
        assertTrue(
                service.listAppChatHistoryByPage(appId, 10, cursorTime, cursorId, owner)
                        .getRecords()
                        .isEmpty());
    }

    @Test
    void timeCursorRemainsCompatibleAndAdminCanRead() {
        insert(firstId, appId, time.minusSeconds(1), 0);
        insert(firstId + 1, appId, time, 0);
        assertEquals(
                firstId,
                service.listAppChatHistoryByPage(appId, 10, time, admin)
                        .getRecords()
                        .getFirst()
                        .getId());
    }

    @Test
    void deniesOtherUsersAndInvalidCursors() {
        assertEquals(
                ErrorCode.NO_AUTH_ERROR.getCode(),
                assertThrows(
                                BusinessException.class,
                                () ->
                                        service.listAppChatHistoryByPage(
                                                appId, 10, null, null, other))
                        .getCode());
        assertThrows(
                BusinessException.class,
                () -> service.listAppChatHistoryByPage(appId, 10, null, null, null));
        assertThrows(
                BusinessException.class,
                () -> service.listAppChatHistoryByPage(appId, 51, null, null, owner));
        assertThrows(
                BusinessException.class,
                () -> service.listAppChatHistoryByPage(appId, 10, null, firstId, owner));
        assertThrows(
                BusinessException.class,
                () -> service.listAppChatHistoryByPage(appId + 1, 10, null, null, owner));
    }

    @Test
    void rejectsArbitraryAdminSortExpressions() {
        var request = new ChatHistoryQueryRequest();
        request.setSortField("createTime; DROP TABLE chat_history");
        assertThrows(BusinessException.class, () -> service.getQueryWrapper(request));
    }

    @Test
    void httpCursorBindsIsoTimeAndGeneratedCrudRequiresAdmin() throws Exception {
        insert(firstId, appId, time, 0);
        insert(firstId + 1, appId, time, 0);
        var login = new AtomicReference<>(owner);
        UserService users =
                (UserService)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class[] {UserService.class},
                                (p, m, a) -> login.get());
        var controller = new ChatHistoryController();
        ReflectionTestUtils.setField(controller, "chatHistoryService", service);
        ReflectionTestUtils.setField(controller, "userService", users);
        var aspect = new AuthInterceptor();
        ReflectionTestUtils.setField(aspect, "userService", users);
        var factory = new AspectJProxyFactory(controller);
        factory.addAspect(aspect);
        factory.setProxyTargetClass(true);
        MockMvc mvc =
                MockMvcBuilders.standaloneSetup((Object) factory.getProxy())
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        mvc.perform(
                        get("/chatHistory/app/" + appId)
                                .param("lastCreateTime", time.toString())
                                .param("lastId", String.valueOf(firstId + 1)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(1));
        mvc.perform(delete("/chatHistory/remove/" + firstId))
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_AUTH_ERROR.getCode()));
        mvc.perform(get("/chatHistory/list"))
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_AUTH_ERROR.getCode()));
        mvc.perform(post("/chatHistory/save").contentType("application/json").content("{}"))
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_AUTH_ERROR.getCode()));
        login.set(other);
        mvc.perform(get("/chatHistory/app/" + appId))
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_AUTH_ERROR.getCode()));
    }
}
