package com.aiinterview.platform.security.jwt;

import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.service.UserService;
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

    private final UserService userService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization"); // condition to verify token need to have

        if (authHeader != null && authHeader.toLowerCase().startsWith("bearer ")) { // condition to verify token need to have
            String token = authHeader.substring(7).trim();

            // Handle token wrapped in quotes (edge case from some clients)
            if (token.startsWith("\"") && token.endsWith("\"")) {
                token = token.substring(1, token.length() - 1);
            }

            try {
                var claims = JwtUtil.validateAccessToken(token);
                String email = claims.getSubject();

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

            } catch (JwtException | UsernameNotFoundException e) {
                // Invalid token or user not found — continue without authentication
            }
        }

        filterChain.doFilter(request, response);
    }
}
