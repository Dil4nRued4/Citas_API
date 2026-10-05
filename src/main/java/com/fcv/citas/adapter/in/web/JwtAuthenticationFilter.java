package com.fcv.citas.adapter.in.web;

import com.fcv.citas.adapter.out.security.JwtTokenIssuer;
import com.fcv.citas.application.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenIssuer tokens;
    private final AuthService authService;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(
            JwtTokenIssuer tokens,
            AuthService authService,
            AuthenticationEntryPoint authenticationEntryPoint) {
        this.tokens = tokens;
        this.authService = authService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(7).trim();
        Optional<Long> userId = token.isEmpty() ? Optional.empty() : tokens.readAccessSubject(token);
        if (userId.isEmpty()) {
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new org.springframework.security.authentication.BadCredentialsException("Invalid access token"));
            return;
        }

        try {
            var account = authService.findById(userId.get());
            var authentication = new UsernamePasswordAuthenticationToken(
                    new AuthenticatedUser(userId.get(), account.role()),
                    null,
                    java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                            "ROLE_" + account.role())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (AuthService.InvalidCredentialsException exception) {
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new org.springframework.security.authentication.BadCredentialsException("Invalid access token"));
        }
    }
}
