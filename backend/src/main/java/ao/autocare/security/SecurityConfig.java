package ao.autocare.security;

import ao.autocare.common.ErrorResponse;
import ao.autocare.config.AutoCareProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_GET = {
        "/api/v1/health",
        "/api/v1/config",
        "/api/v1/catalog/**",
        // Convite: quem é convidado ainda não tem conta; o token é a credencial
        "/api/v1/invitations/*",
        // Fluxo em tempo real: o EventSource não envia cabeçalhos; a
        // credencial é o bilhete de uso único no URL
        "/api/v1/telemetry/stream",
        "/v3/api-docs/**",
        "/docs",
        "/docs/**",
        "/swagger-ui/**",
        // Ficheiros servidos por URL assinado (o controlador valida a assinatura)
        "/api/v1/files/**",
        // Raiz: redireciona para a aplicação web (RootController)
        "/",
        "/index.html",
        "/favicon.ico",
    };

    private static final String[] PUBLIC_POST = {
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout",
        "/api/v1/auth/forgot-password",
        "/api/v1/auth/reset-password",
        "/api/v1/invitations/*/accept",
        // Ingestão de GPS: quem publica é o aparelho, autenticado pela sua chave
        "/api/v1/telemetry/positions",
        "/api/v1/telemetry/traccar/forward",
    };

    private final JwtAuthenticationFilter jwtFilter;
    private final AutoCareProperties props;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            JwtAuthenticationFilter jwtFilter,
            AutoCareProperties props,
            ObjectMapper objectMapper) {
        this.jwtFilter = jwtFilter;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.GET, PUBLIC_GET).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, PUBLIC_POST).permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, HttpStatus.UNAUTHORIZED,
                                        "Precisa de iniciar sessão para aceder a este recurso.",
                                        request.getRequestURI()))
                        .accessDeniedHandler((request, response, deniedException) ->
                                writeError(response, HttpStatus.FORBIDDEN,
                                        "Não tem permissão para aceder a esta área.",
                                        request.getRequestURI())))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        String origins = props.cors().allowedOrigins();
        if (origins == null || origins.isBlank() || "*".equals(origins.trim())) {
            config.setAllowedOriginPatterns(List.of("*"));
        } else {
            config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toList());
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private void writeError(
            jakarta.servlet.http.HttpServletResponse response,
            HttpStatus status,
            String message,
            String path) {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        try {
            objectMapper.writeValue(
                    response.getWriter(),
                    ErrorResponse.of(status.value(), message, path));
        } catch (Exception ignored) {
            // resposta já comprometida — nada a fazer
        }
    }
}
