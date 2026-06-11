package com.example.demo.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Date;

public class JwtUtil {

    private static final String SECRET = "hangyin-video-jwt-secret-key-2026";
    private static final long EXPIRE_HOURS = 72;
    private static final Algorithm ALGORITHM = Algorithm.HMAC256(SECRET);

    /** 生成 token，payload 里放 userId */
    public static String generate(Long userId) {
        return JWT.create()
                .withClaim("userId", userId)
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRE_HOURS * 3600_000))
                .sign(ALGORITHM);
    }

    /** 校验并解析 userId */
    public static Long verify(String token) {
        try {
            JWTVerifier verifier = JWT.require(ALGORITHM).build();
            DecodedJWT jwt = verifier.verify(token);
            return jwt.getClaim("userId").asLong();
        } catch (JWTVerificationException e) {
            return null;
        }
    }
}
