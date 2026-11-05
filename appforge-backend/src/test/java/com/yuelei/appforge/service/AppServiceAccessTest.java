package com.yuelei.appforge.service;

import com.yuelei.appforge.exception.BusinessException;
import com.yuelei.appforge.exception.ErrorCode;
import com.yuelei.appforge.model.entity.App;
import com.yuelei.appforge.model.entity.User;
import com.yuelei.appforge.model.vo.AppVO;
import com.yuelei.appforge.service.impl.AppServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 精选应用只开放查看权限，生成、部署和编辑仍要求归属。 */
class AppServiceAccessTest {

    private static final long APP_ID = 475952860203356160L;
    private AppServiceImpl service;
    private App app;
    private AppVO appVO;
    private User owner;
    private User visitor;

    @BeforeEach
    void setUp() {
        // 替换数据读取边界，不启动数据库或依赖 JVM 动态代理。
        service = new AppServiceImpl() {
            @Override
            public App getById(Serializable id) {
                assertEquals(APP_ID, id);
                return app;
            }

            @Override
            public AppVO getAppVO(App value) {
                assertSame(app, value);
                return appVO;
            }
        };
        app = new App();
        app.setId(APP_ID);
        app.setUserId(100L);
        app.setPriority(0);
        appVO = new AppVO();
        owner = new User();
        owner.setId(100L);
        visitor = new User();
        visitor.setId(200L);
    }

    @Test
    void ownerCanReadOwnUnfeaturedApp() {
        assertSame(appVO, service.getAppVOById(APP_ID, owner));
    }

    @Test
    void visitorCanReadFeaturedApp() {
        app.setPriority(99);
        assertSame(appVO, service.getAppVOById(APP_ID, visitor));
    }

    @Test
    void visitorCannotReadUnfeaturedApp() {
        assertError(ErrorCode.NO_AUTH_ERROR, () -> service.getAppVOById(APP_ID, visitor));
    }

    @Test
    void otherPriorityDoesNotGrantFeaturedAccess() {
        app.setPriority(1);
        assertError(ErrorCode.NO_AUTH_ERROR, () -> service.getAppVOById(APP_ID, visitor));
    }

    @Test
    void anonymousCannotReadFeaturedDetails() {
        app.setPriority(99);
        assertError(ErrorCode.NOT_LOGIN_ERROR, () -> service.getAppVOById(APP_ID, null));
    }

    @Test
    void visitorCannotGenerateFeaturedApp() {
        app.setPriority(99);
        assertError(ErrorCode.NO_AUTH_ERROR,
            () -> service.chatToGenCode(APP_ID, "修改页面", visitor));
    }

    @Test
    void visitorCannotDeployFeaturedApp() {
        app.setPriority(99);
        assertError(ErrorCode.NO_AUTH_ERROR, () -> service.deployApp(APP_ID, visitor));
    }

    private void assertError(ErrorCode code, Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        assertEquals(code.getCode(), exception.getCode());
    }
}
