package com.aiinterview.platform.security.jwt;

import com.aiinterview.platform.common.exception.InvalidAccountException;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.service.UserService;
import com.aiinterview.platform.service.UserSessionService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    /** Controller đọc thuộc tính này để biết request đang mang phiên nào (logout, danh sách thiết bị). */
    public static final String SESSION_ID_ATTRIBUTE = "aiinterview.sessionId";

    private final UserService userService;
    private final UserSessionService userSessionService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.toLowerCase().startsWith("bearer ")) {
            String token = authHeader.substring(7).trim();

            // Handle token wrapped in quotes (edge case from some clients)
            if (token.startsWith("\"") && token.endsWith("\"")) {
                token = token.substring(1, token.length() - 1);
            }

            try {
                var claims = JwtUtil.validateAccessToken(token);
                // Challenge token 2FA ký bằng đúng access key — không được cấp quyền truy cập API.
                if (claims.get("purpose", String.class) != null) {
                    filterChain.doFilter(request, response);
                    return;
                }
                String email = claims.getSubject();
                String sessionId = claims.get("sid", String.class);

                // Phiên đã thu hồi (logout, đổi mật khẩu, admin khóa tài khoản) mất quyền ngay,
                // không đợi access token hết hạn 24h.
                if (!userSessionService.isLive(sessionId)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                // Load full User entity so @AuthenticationPrincipal User works in controllers
                UserDetails userDetails = userService.loadUserByUsername(email);

                User user = (User) userDetails;
                if (!user.isEnabled() || user.isLocked() || user.getDeletedAt() != null) {
                    filterChain.doFilter(request, response);
                    return;
                }

                var auth = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(auth);

                if (sessionId != null) {
                    request.setAttribute(SESSION_ID_ATTRIBUTE, sessionId);
                    userSessionService.touch(sessionId);
                }

            } catch (JwtException | UsernameNotFoundException | InvalidAccountException e) {
                // Invalid token or user no longer exists — continue without authentication
            }
        }

        filterChain.doFilter(request, response);
    }
}
