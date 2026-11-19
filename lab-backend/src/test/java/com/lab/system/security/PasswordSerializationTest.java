package com.lab.system.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.system.entity.SysUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 敏感字段泄露防护测试（成员 A）
 *
 * SysUser.password 标注 @JsonIgnore，任何接口都不允许把密码（含密文）返回给前端。
 * 这类问题不会报错，只会静默泄露，所以必须用测试守住。
 */
@DisplayName("敏感字段序列化防护")
class PasswordSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private SysUser userWithPassword() {
        SysUser u = new SysUser();
        u.setId(1L);
        u.setUsername("admin");
        u.setRealName("系统管理员");
        u.setRole("ADMIN");
        u.setPassword("$2a$10$abcdefghijklmnopqrstuv");
        u.setCreditScore(100);
        return u;
    }

    @Test
    @DisplayName("TC-S001 - 序列化用户对象时不输出 password 字段")
    void passwordNotSerialized() throws Exception {
        String json = mapper.writeValueAsString(userWithPassword());
        assertFalse(json.contains("password"), "响应 JSON 中不应出现 password 字段：" + json);
        assertFalse(json.contains("$2a$10$"), "响应 JSON 中不应出现 BCrypt 密文");
    }

    @Test
    @DisplayName("TC-S002 - 正常字段仍能正常序列化")
    void normalFieldsSerialized() throws Exception {
        String json = mapper.writeValueAsString(userWithPassword());
        assertTrue(json.contains("\"username\":\"admin\""));
        assertTrue(json.contains("\"realName\":\"系统管理员\""));
        assertTrue(json.contains("\"role\":\"ADMIN\""));
    }

    @Test
    @DisplayName("TC-S003 - 反序列化时 password 被忽略，客户端无法直接指定密码")
    void passwordCannotBeInjected() throws Exception {
        // @JsonIgnore 同时作用于序列化与反序列化：客户端提交的 password 不会进入实体。
        // 这是刻意的安全设计——新增用户由 UserService 统一写入初始密码，
        // 修改密码走 /api/auth/password 独立接口（使用 PasswordDTO），不接受外部直接赋值。
        String json = "{\"username\":\"admin\",\"password\":\"hacked\"}";
        SysUser u = mapper.readValue(json, SysUser.class);
        assertNull(u.getPassword(), "客户端提交的 password 不应被绑定到实体");
        assertEquals("admin", u.getUsername());
    }

    @Test
    @DisplayName("TC-S004 - 用户列表整体序列化同样不含密码")
    void listDoesNotLeakPassword() throws Exception {
        var list = java.util.List.of(userWithPassword(), userWithPassword());
        String json = mapper.writeValueAsString(list);
        assertFalse(json.contains("password"));
        assertFalse(json.contains("$2a$"));
    }

    @Test
    @DisplayName("TC-S005 - 包装在统一响应 R 中也不会泄露")
    void wrappedInResponseDoesNotLeak() throws Exception {
        String json = mapper.writeValueAsString(
                com.lab.system.common.R.ok(userWithPassword()));
        assertFalse(json.contains("password"));
        assertTrue(json.contains("\"code\":200"));
    }
}
