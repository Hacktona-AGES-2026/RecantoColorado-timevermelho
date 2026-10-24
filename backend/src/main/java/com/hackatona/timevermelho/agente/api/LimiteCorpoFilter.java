package com.hackatona.timevermelho.agente.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.hackatona.timevermelho.agente.AgenteProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Recusa pedidos grandes demais para /api/agente antes de ler o corpo. Exige Content-Length (o fetch do
 * navegador sempre manda), para que ninguém encha a memória com um corpo sem tamanho conhecido.
 */
@Component
public class LimiteCorpoFilter extends OncePerRequestFilter {

    private final long maxBytes;

    public LimiteCorpoFilter(AgenteProperties props) {
        this.maxBytes = props.limites().maxCorpoBytes();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/agente") || !"POST".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long tamanho = request.getContentLengthLong();
        if (tamanho < 0) {
            recusar(response, 411, "Informe o tamanho do corpo (Content-Length).");
        } else if (tamanho > maxBytes) {
            recusar(response, 413, "Pedido grande demais.");
        } else {
            chain.doFilter(request, response);
        }
    }

    private static void recusar(HttpServletResponse response, int status, String mensagem) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(("{\"mensagem\":\"" + mensagem + "\"}").getBytes(StandardCharsets.UTF_8));
    }
}
