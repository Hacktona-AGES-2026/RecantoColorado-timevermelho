package com.hackatona.timevermelho.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.hackatona.timevermelho.api.CabecalhoSnapshot;

/** Libera o front Next.js (localhost:3000) e identifica o snapshot em toda resposta da API. */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.origins}")
    private String[] origins;

    private final CabecalhoSnapshot cabecalhoSnapshot;

    public CorsConfig(CabecalhoSnapshot cabecalhoSnapshot) {
        this.cabecalhoSnapshot = cabecalhoSnapshot;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                // o front pode ler qual fila recebeu e o nome do arquivo baixado
                .exposedHeaders(CabecalhoSnapshot.NOME, HttpHeaders.CONTENT_DISPOSITION);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(cabecalhoSnapshot).addPathPatterns("/api/**");
    }
}
