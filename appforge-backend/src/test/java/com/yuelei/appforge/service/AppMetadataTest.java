package com.yuelei.appforge.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuelei.appforge.aop.AuthInterceptor;
import com.yuelei.appforge.controller.AppController;
import com.yuelei.appforge.exception.BusinessException;
import com.yuelei.appforge.exception.ErrorCode;
import com.yuelei.appforge.model.dto.app.AppAdminUpdateRequest;
import com.yuelei.appforge.model.dto.app.AppUpdateRequest;
import com.yuelei.appforge.model.entity.App;
import com.yuelei.appforge.model.entity.User;
import com.yuelei.appforge.service.impl.AppServiceImpl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.Serializable;
import java.lang.reflect.Proxy;

/** 验证实际服务及控制器权限切面，不启动数据库或调用模型。 */
class AppMetadataTest {
    private static final long APP_ID = 475952860203356160L;
    private AppServiceImpl service;
    private App app;
    private App saved;
    private User owner;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        app = new App();
        app.setId(APP_ID);
        app.setUserId(100L);
        app.setAppName("原名称");
        app.setCover("https://example.com/old.png");
        app.setPriority(0);
        owner = user(100L, "user");
        request = new MockHttpServletRequest();
        service =
                new AppServiceImpl() {
                    @Override
                    public App getById(Serializable id) {
                        return Long.valueOf(APP_ID).equals(id) ? app : null;
                    }

                    @Override
                    public boolean updateById(App entity) {
                        saved = entity;
                        return true;
                    }
                };
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void ownerCanSaveNameAndCoverThroughController() {
        AppUpdateRequest update = update("  新名称  ", " https://example.com/new.png ");
        assertTrue(controller(owner).updateApp(update, request).getData());
        assertEquals("新名称", saved.getAppName());
        assertEquals("https://example.com/new.png", saved.getCover());
        assertNotNull(saved.getEditTime());
    }

    @Test
    void blankCoverClearsIt() {
        service.updateApp(update("新名称", "  "), owner);
        assertEquals("", saved.getCover());
    }

    @Test
    void omittedCoverIsNotOverwritten() {
        service.updateApp(update("新名称", null), owner);
        assertNull(saved.getCover());
    }

    @Test
    void otherUserCannotUpdateEvenFeaturedApp() {
        app.setPriority(99);
        assertError(
                ErrorCode.NO_AUTH_ERROR,
                () -> controller(user(200L, "user")).updateApp(update("新名称", ""), request));
        assertNull(saved);
    }

    @Test
    void forgedAdminFieldsAreIgnoredByUserRequest() throws Exception {
        ObjectMapper mapper =
                new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        String json =
                "{\"id\":\""
                        + APP_ID
                        + "\",\"appName\":\"合法名称\",\"cover\":\"\","
                        + "\"priority\":99,\"userId\":200,\"codeGenType\":\"html\","
                        + "\"deployKey\":\"forged\",\"initPrompt\":\"forged\"}";
        AppUpdateRequest update = mapper.readValue(json, AppUpdateRequest.class);
        controller(owner).updateApp(update, request);
        assertNull(saved.getPriority());
        assertNull(saved.getUserId());
        assertNull(saved.getCodeGenType());
        assertNull(saved.getDeployKey());
        assertNull(saved.getInitPrompt());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "javascript:alert(1)",
                "data:image/png;base64,abc",
                "file:///tmp/a.png",
                "not-a-url",
                "https:///missing-host"
            })
    void invalidCoverIsRejected(String cover) {
        assertError(ErrorCode.PARAMS_ERROR, () -> service.updateApp(update("名称", cover), owner));
        assertNull(saved);
    }

    @Test
    void oversizedNameAndCoverAreRejected() {
        assertError(
                ErrorCode.PARAMS_ERROR,
                () -> service.updateApp(update("名".repeat(257), ""), owner));
        assertError(
                ErrorCode.PARAMS_ERROR,
                () ->
                        service.updateApp(
                                update("名称", "https://example.com/" + "a".repeat(512)), owner));
        assertNull(saved);
    }

    @Test
    void emptyNameIsRejected() {
        assertError(ErrorCode.PARAMS_ERROR, () -> service.updateApp(update("  ", ""), owner));
    }

    @Test
    void ownerCannotUseAdminEndpointToChangePriority() {
        AppAdminUpdateRequest update = new AppAdminUpdateRequest();
        update.setId(APP_ID);
        update.setPriority(99);
        assertError(ErrorCode.NO_AUTH_ERROR, () -> controller(owner).updateAppByAdmin(update));
        assertNull(saved);
    }

    @Test
    void adminCannotFeaturePrivateApplication() {
        app.setVisibility("PRIVATE");
        AppAdminUpdateRequest update = new AppAdminUpdateRequest();
        update.setId(APP_ID);
        update.setPriority(99);
        assertError(
                ErrorCode.PARAMS_ERROR,
                () -> controller(user(200L, "admin")).updateAppByAdmin(update));
        assertNull(saved);
    }

    @Test
    void adminCanChangeAnotherUsersPriority() {
        app.setVisibility("PUBLIC");
        AppAdminUpdateRequest update = new AppAdminUpdateRequest();
        update.setId(APP_ID);
        update.setPriority(99);
        assertTrue(controller(user(200L, "admin")).updateAppByAdmin(update).getData());
        assertEquals(99, saved.getPriority());
        assertNotNull(saved.getEditTime());
    }

    @Test
    void negativePriorityIsRejectedByAdminEndpoint() {
        AppAdminUpdateRequest update = new AppAdminUpdateRequest();
        update.setId(APP_ID);
        update.setPriority(-1);
        assertError(
                ErrorCode.PARAMS_ERROR,
                () -> controller(user(200L, "admin")).updateAppByAdmin(update));
        assertNull(saved);
    }

    private AppController controller(User loginUser) {
        UserService users =
                (UserService)
                        Proxy.newProxyInstance(
                                UserService.class.getClassLoader(),
                                new Class<?>[] {UserService.class},
                                (proxy, method, args) -> {
                                    if ("getLoginUser".equals(method.getName())) {
                                        return loginUser;
                                    }
                                    throw new UnsupportedOperationException(method.getName());
                                });
        AppController controller = new AppController();
        ReflectionTestUtils.setField(controller, "appService", service);
        ReflectionTestUtils.setField(controller, "userService", users);
        AuthInterceptor interceptor = new AuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "userService", users);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        AspectJProxyFactory factory = new AspectJProxyFactory(controller);
        factory.addAspect(interceptor);
        return factory.getProxy();
    }

    private AppUpdateRequest update(String name, String cover) {
        AppUpdateRequest update = new AppUpdateRequest();
        update.setId(APP_ID);
        update.setAppName(name);
        update.setCover(cover);
        return update;
    }

    private User user(long id, String role) {
        User user = new User();
        user.setId(id);
        user.setUserRole(role);
        return user;
    }

    private void assertError(ErrorCode code, Runnable action) {
        BusinessException error = assertThrows(BusinessException.class, action::run);
        assertEquals(code.getCode(), error.getCode());
    }
}
