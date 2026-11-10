package com.lab.system.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 */
@Component
public class JwtUtil {

    @Value("${lab.jwt.secret}")
    private String secret;

    @Value("${lab.jwt.expire-hours}")
    private long expireHours;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(LoginUser user) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + expireHours * 3600 * 1000);
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("realName", user.getRealName())
                .claim("role", user.getRole())
                .claim("ver", user.getTokenVersion() == null ? 0 : user.getTokenVersion())
                .issuedAt(now)
                .expiration(expire)
                .signWith(key())
                .compact();
    }

    public LoginUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        LoginUser user = new LoginUser();
        user.setId(Long.valueOf(claims.getSubject()));
        user.setUsername(claims.get("username", String.class));
        user.setRealName(claims.get("realName", String.class));
        user.setRole(claims.get("role", String.class));
        Integer ver = claims.get("ver", Integer.class);
        user.setTokenVersion(ver == null ? 0 : ver);
        return user;
    }
}
