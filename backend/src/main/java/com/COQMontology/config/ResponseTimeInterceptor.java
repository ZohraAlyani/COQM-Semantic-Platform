package com.timeontology.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ResponseTimeInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "startTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTR, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Object startObj = request.getAttribute(START_TIME_ATTR);
        if (startObj instanceof Long startTime) {
            long endTime = System.nanoTime();
            double durationMs = (endTime - startTime) / 1_000_000.0;

            response.setHeader("X-Response-Time-ms", String.format("%.3f", durationMs));

            System.out.println(
                request.getMethod() + " " +
                request.getRequestURI() + " -> " +
                String.format("%.3f", durationMs) + " ms"
            );
        }
    }
}