package neko.fantasy.auth.config;

import neko.fantasy.auth.security.JwtAuthenticationFilter;
import neko.fantasy.auth.security.RestAccessDeniedHandler;
import neko.fantasy.auth.security.RestAuthenticationEntryPoint;
import neko.fantasy.auth.oauth.OAuthClientCorsOrigins;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/setup", "/setup/**", "/api/setup/**").permitAll()
                        .requestMatchers("/.well-known/**", "/oauth2/**").permitAll()
                        .requestMatchers("/api/internal/**").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/register/code",
                                "/api/auth/register/captcha", "/api/auth/login",
                                "/api/auth/refresh", "/api/auth/logout",
                                "/api/auth/password/reset-code", "/api/auth/password/reset").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/registration-policy").permitAll()
                        .requestMatchers(HttpMethod.GET, "/avatars/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/oauth/authorize").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .headers(headers -> headers
                        .cacheControl(cacheControl -> cacheControl.disable())
                        .frameOptions(frame -> frame.deny())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data:; font-src 'self'; connect-src 'self'; "
                                        + "object-src 'none'; base-uri 'self'; frame-ancestors 'none'"))
                        .httpStrictTransportSecurity(Customizer.withDefaults()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AuthProperties properties,
                                                    OAuthClientCorsOrigins clientCorsOrigins) {
        List<String> configuredOrigins = properties.cors().allowedOrigins().stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        return request -> {
            String path = request.getRequestURI();
            if (!(path.startsWith("/api/") || path.startsWith("/oauth2/")
                    || path.startsWith("/.well-known/"))) {
                return null;
            }

            CorsConfiguration configuration = new CorsConfiguration();
            List<String> allowedOrigins = new ArrayList<>(configuredOrigins);
            String origin = request.getHeader(HttpHeaders.ORIGIN);
            boolean publicOAuthEndpoint = path.startsWith("/oauth2/") || path.startsWith("/.well-known/");
            if (publicOAuthEndpoint && origin != null && !origin.isBlank()
                    && clientCorsOrigins.contains(origin)) {
                allowedOrigins.add(origin);
            }
            configuration.setAllowedOrigins(allowedOrigins);
            configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
            configuration.setExposedHeaders(List.of("Content-Disposition", "X-Request-Id"));
            configuration.setAllowCredentials(true);
            configuration.setMaxAge(3600L);
            return configuration;
        };
    }
}
