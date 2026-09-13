package ao.autocare.config;

import ao.autocare.security.LicenseInterceptor;
import ao.autocare.security.RateLimitInterceptor;
import ao.autocare.security.RoleInterceptor;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Faz as respostas JSON declararem {@code charset=UTF-8} explicitamente (os
 * acentos do português são frequentes) e liga a verificação de papéis da equipa.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RoleInterceptor roleInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final LicenseInterceptor licenseInterceptor;

    public WebConfig(
            RoleInterceptor roleInterceptor,
            RateLimitInterceptor rateLimitInterceptor,
            LicenseInterceptor licenseInterceptor) {
        this.roleInterceptor = roleInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.licenseInterceptor = licenseInterceptor;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter json) {
                json.setSupportedMediaTypes(List.of(
                        new MediaType("application", "json", StandardCharsets.UTF_8),
                        new MediaType("application", "*+json", StandardCharsets.UTF_8)));
            }
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // A travagem vem primeiro: não vale a pena verificar papéis de quem já
        // ultrapassou o limite de tentativas.
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(licenseInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(roleInterceptor).addPathPatterns("/api/**");
    }
}
