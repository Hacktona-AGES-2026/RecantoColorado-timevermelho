package com.hackatona.timevermelho.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.hackatona.timevermelho.api.CabecalhoSnapshot;

/**
 * Libera o front Next.js e identifica o snapshot em toda resposta da API. Aceita padrões
 * ({@code http://localhost:[*]} = qualquer porta), porque o Next usa a 3001 quando a 3000 está ocupada. No uso normal
 * o navegador nem precisa disso: o Next repassa /api para o backend (next.config.ts).
 */
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
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                // o front pode ler qual fila recebeu e o nome do arquivo baixado
                .exposedHeaders(CabecalhoSnapshot.NOME, HttpHeaders.CONTENT_DISPOSITION);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(cabecalhoSnapshot).addPathPatterns("/api/**");
    }
}
