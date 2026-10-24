package com.hackatona.timevermelho.api;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.hackatona.timevermelho.servico.FilaService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Toda resposta da API diz de qual fila ela é: X-FilaViva-Snapshot: operacional; versao=op-12; calculado=...
 * Todas as rotas leem a fila operacional; o export define o próprio cabeçalho (pode ser a oficial).
 */
@Component
public class CabecalhoSnapshot implements HandlerInterceptor {

    public static final String NOME = "X-FilaViva-Snapshot";

    private final FilaService fila;

    public CabecalhoSnapshot(FilaService fila) {
        this.fila = fila;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        if (!req.getRequestURI().endsWith("/export.csv")) {
            resp.setHeader(NOME, fila.atual().info().cabecalho());
        }
        return true;
    }
}
