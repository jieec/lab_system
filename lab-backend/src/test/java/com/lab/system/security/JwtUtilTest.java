package com.lab.system.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** JWT 签发/解析与令牌版本（token_version）载荷。 */
class JwtUtilTest {

    private static final String SECRET = "lab-reservation-system-secret-key-2026-test-only";

    private JwtUtil jwtUtil;

    private LoginUser sample() {
        LoginUser u = new LoginUser();
        u.setId(7L);
        u.setUsername("student01");
        u.setRealName("张三");
        u.setRole("STUDENT");
        u.setTokenVersion(3);
        return u;
    }

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expireHours", 12L);
    }

    @Test
    @DisplayName("签发后可还原全部用户字段")
    void roundTrip() {
        String token = jwtUtil.generate(sample());
        assertNotNull(token);
        LoginUser parsed = jwtUtil.parse(token);
        assertEquals(7L, parsed.getId());
        assertEquals("student01", parsed.getUsername());
        assertEquals("张三", parsed.getRealName());
        assertEquals("STUDENT", parsed.getRole());
    }

    @Test
    @DisplayName("令牌版本写入 claim 并能还原")
    void tokenVersionClaim() {
        LoginUser parsed = jwtUtil.parse(jwtUtil.generate(sample()));
        assertEquals(3, parsed.getTokenVersion());
    }

    @Test
    @DisplayName("tokenVersion 为 null 时按 0 处理")
    void nullVersionDefaultsToZero() {
        LoginUser u = sample();
        u.setTokenVersion(null);
        assertEquals(0, jwtUtil.parse(jwtUtil.generate(u)).getTokenVersion());
    }

    @Test
    @DisplayName("不同密钥签发的令牌无法解析")
    void rejectForeignSignature() {
        JwtUtil other = new JwtUtil();
        ReflectionTestUtils.setField(other, "secret", "another-completely-different-secret-key-0001");
        ReflectionTestUtils.setField(other, "expireHours", 12L);
        String foreign = other.generate(sample());
        assertThrows(JwtException.class, () -> jwtUtil.parse(foreign));
    }

    @Test
    @DisplayName("过期令牌解析失败")
    void rejectExpired() {
        ReflectionTestUtils.setField(jwtUtil, "expireHours", -1L);
        String expired = jwtUtil.generate(sample());
        assertThrows(JwtException.class, () -> jwtUtil.parse(expired));
    }
}
