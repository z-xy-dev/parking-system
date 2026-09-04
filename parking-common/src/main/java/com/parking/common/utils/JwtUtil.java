package com.parking.common.utils;

import cn.hutool.core.date.DateUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtUtil {

    /**
     * 默认密钥仅用于本地开发。生产环境必须通过环境变量 JWT_SECRET 注入，
     * 且长度不少于 32 字节（HS256 要求）。
     */
    private static final String DEFAULT_SECRET = "parking-system-shared-secret-key-2024";
    private static final String SECRET = resolveSecret();
    private static final long EXPIRE_HOURS = 24;

    private static String resolveSecret() {
        String env = System.getenv("JWT_SECRET");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return DEFAULT_SECRET;
    }

    private static SecretKey getKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    public static String generate(Long userId, String username, String role, String carPlate) {
        Date now = new Date();
        JwtBuilder builder = Jwts.builder()
                .claim("userId", userId)
                .claim("username", username)
                .claim("role", role);
        if (carPlate != null && !carPlate.isBlank()) {
            builder.claim("carPlate", carPlate);
        }
        return builder
                .issuedAt(now)
                .expiration(DateUtil.offsetHour(now, (int) EXPIRE_HOURS))
                .signWith(getKey())
                .compact();
    }

    public static Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public static Long getUserId(String token) {
        return parse(token).get("userId", Long.class);
    }

    public static String getRole(String token) {
        return parse(token).get("role", String.class);
    }

    /**
     * 取车牌号。老版本签发的 token 不含该 claim，返回 null。
     */
    public static String getCarPlate(String token) {
        return parse(token).get("carPlate", String.class);
    }

    public static boolean isExpired(String token) {
        try {
            parse(token);
            return false;
        } catch (Exception e) {
            return true;
        }
    }
}
