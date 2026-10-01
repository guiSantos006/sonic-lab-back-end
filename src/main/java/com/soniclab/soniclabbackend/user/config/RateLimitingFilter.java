package com.soniclab.soniclabbackend.user.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final long ONE_MINUTE_MS = 60_000L;
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int MAX_FORGOT_PASSWORD_ATTEMPTS = 3;
    private static final int MAX_REGISTER_ATTEMPTS = 5;

    private final Map<String, Deque<Long>> requestCountsPerIp = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equalsIgnoreCase(method)) {
            Integer limit = getLimitForPath(path);
            if (limit != null) {
                String clientIp = extractClientIp(request);
                String key = clientIp + ":" + path;

                if (isRateLimited(key, limit)) {
                    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("""
                            {
                              "status": 429,
                              "error": "Too Many Requests",
                              "message": "Limite de requisições excedido. Por favor, aguarde um minuto e tente novamente."
                            }
                            """);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private Integer getLimitForPath(String path) {
        if (path.endsWith("/auth/login")) {
            return MAX_LOGIN_ATTEMPTS;
        }
        if (path.endsWith("/auth/forgot-password")) {
            return MAX_FORGOT_PASSWORD_ATTEMPTS;
        }
        if (path.endsWith("/auth/register")) {
            return MAX_REGISTER_ATTEMPTS;
        }
        return null;
    }

    private boolean isRateLimited(String key, int maxRequests) {
        long now = System.currentTimeMillis();
        long windowStart = now - ONE_MINUTE_MS;

        return requestCountsPerIp.compute(key, (k, timestamps) -> {
            if (timestamps == null) {
                timestamps = new ArrayDeque<>();
            }

            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= maxRequests) {
                return timestamps;
            }

            timestamps.addLast(now);
            return timestamps;
        }).size() > maxRequests;
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
