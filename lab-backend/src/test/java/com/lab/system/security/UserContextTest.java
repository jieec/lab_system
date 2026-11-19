package com.lab.system.security;

import com.lab.system.common.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 登录用户上下文测试（成员 A）
 *
 * 验证 ThreadLocal 上下文的写入、读取、清理，以及未登录时的兜底行为。
 * 这是权限体系的基础：所有 Service 都通过 UserContext 取当前用户。
 */
@DisplayName("登录用户上下文 UserContext")
class UserContextTest {

    private LoginUser user(Long id, String role) {
        LoginUser u = new LoginUser();
        u.setId(id);
        u.setUsername("u" + id);
        u.setRealName("测试用户" + id);
        u.setRole(role);
        u.setTokenVersion(0);
        return u;
    }

    @AfterEach
    void tearDown() {
        // 必须清理，否则线程复用时上下文会串到下一个测试
        UserContext.clear();
    }

    @Test
    @DisplayName("TC-U001 - set 后 get 能取到同一对象")
    void setAndGet() {
        LoginUser u = user(1L, "STUDENT");
        UserContext.set(u);
        assertSame(u, UserContext.get());
    }

    @Test
    @DisplayName("TC-U002 - 未设置上下文时 get 抛 401 业务异常")
    void getWithoutSetThrows401() {
        BizException ex = assertThrows(BizException.class, UserContext::get);
        assertEquals(401, ex.getCode());
        assertTrue(ex.getMessage().contains("登录"));
    }

    @Test
    @DisplayName("TC-U003 - 未设置上下文时 peek 返回 null 而不抛异常")
    void peekReturnsNull() {
        assertNull(UserContext.peek());
    }

    @Test
    @DisplayName("TC-U004 - userId 返回当前用户 ID")
    void userId() {
        UserContext.set(user(42L, "TEACHER"));
        assertEquals(42L, UserContext.userId());
    }

    @Test
    @DisplayName("TC-U005 - userId 未登录时抛 401")
    void userIdWithoutLogin() {
        assertThrows(BizException.class, UserContext::userId);
    }

    @Test
    @DisplayName("TC-U006 - role 返回当前角色")
    void role() {
        UserContext.set(user(7L, "REPAIRER"));
        assertEquals("REPAIRER", UserContext.role());
    }

    @Test
    @DisplayName("TC-U007 - 管理员 isAdmin 为 true")
    void isAdminTrue() {
        UserContext.set(user(1L, "ADMIN"));
        assertTrue(UserContext.isAdmin());
    }

    @Test
    @DisplayName("TC-U008 - 非管理员 isAdmin 为 false")
    void isAdminFalse() {
        for (String role : new String[]{"STUDENT", "TEACHER", "REPAIRER"}) {
            UserContext.set(user(2L, role));
            assertFalse(UserContext.isAdmin(), role);
        }
    }

    @Test
    @DisplayName("TC-U009 - clear 后上下文被清空，get 抛 401")
    void clearRemovesContext() {
        UserContext.set(user(1L, "ADMIN"));
        UserContext.clear();
        assertNull(UserContext.peek());
        assertThrows(BizException.class, UserContext::get);
    }

    @Test
    @DisplayName("TC-U010 - 后设置的上下文覆盖前一个")
    void overwrite() {
        UserContext.set(user(1L, "STUDENT"));
        UserContext.set(user(2L, "ADMIN"));
        assertEquals(2L, UserContext.userId());
        assertTrue(UserContext.isAdmin());
    }
}
