package com.hackatona.timevermelho.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Libera o front Next.js para chamar a API direto. Aceita padrões ({@code http://localhost:[*]} = qualquer porta),
 * porque o Next usa a 3001 quando a 3000 está ocupada. No uso normal o navegador nem precisa disso: o Next
 * repassa /api para o backend (next.config.ts).
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.origins}")
    private String[] origins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    }
}
