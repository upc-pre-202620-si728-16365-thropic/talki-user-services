package com.thropic.talki.identity.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Valida el JWT (firma, expiración y denylist) en todos los endpoints
 * protegidos. Si el token es válido, autentica la petición con el email
 * como principal; si falta o es inválido, la petición sigue anónima y
 * Security responde 401 en las rutas protegidas.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenDenylist tokenDenylist;

    public JwtAuthFilter(JwtTokenProvider jwtTokenProvider, TokenDenylist tokenDenylist) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenDenylist = tokenDenylist;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtTokenProvider.validateToken(token) && !tokenDenylist.isDenylisted(token)) {
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        jwtTokenProvider.getEmailFromToken(token), null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(request, response);
    }
}
