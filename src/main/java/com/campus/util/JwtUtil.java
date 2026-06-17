package com.campus.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JwtUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long EXPIRATION = 7 * 24 * 60 * 60 * 1000L;
    private static final Map<String, Long> TOKEN_EXPIRY = new ConcurrentHashMap<>();

    public static String generate(Long userId, String username, String role) {
        Map<String, Object> payload = Map.of(
                "userId", userId,
                "username", username,
                "role", role,
                "exp", System.currentTimeMillis() + EXPIRATION
        );
        try {
            String json = MAPPER.writeValueAsString(payload);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            TOKEN_EXPIRY.put(token, payload.get("exp") instanceof Number ? ((Number) payload.get("exp")).longValue() : 0L);
            return token;
        } catch (Exception e) {
            throw new RuntimeException("生成token失败", e);
        }
    }

    public static Map<String, Object> parse(String token) {
        removeExpiredTokens();
        Long exp = TOKEN_EXPIRY.get(token);
        if (exp == null) {
            throw new RuntimeException("invalid");
        }
        if (System.currentTimeMillis() > exp) {
            TOKEN_EXPIRY.remove(token);
            throw new RuntimeException("expired");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            String json = new String(decoded, StandardCharsets.UTF_8);
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("malformed");
        }
    }

    public static Long getUserId(Map<String, Object> claims) {
        Object val = claims.get("userId");
        if (val instanceof Integer) return ((Integer) val).longValue();
        if (val instanceof Long) return (Long) val;
        return null;
    }

    public static String getUsername(Map<String, Object> claims) {
        return (String) claims.get("username");
    }

    public static String getRole(Map<String, Object> claims) {
        return (String) claims.get("role");
    }

    private static void removeExpiredTokens() {
        long now = System.currentTimeMillis();
        TOKEN_EXPIRY.entrySet().removeIf(e -> e.getValue() < now);
    }
}
