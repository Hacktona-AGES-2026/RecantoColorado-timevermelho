package com.hackatona.timevermelho.agente.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Proteção contra abuso e custo (T43), prefixo {@code agente.abuso.*}. */
@ConfigurationProperties("agente.abuso")
public record AbusoProperties(
        /** Recusas "de verdade" (fora do escopo, burlar, moderação) numa janela para bloquear o IP por um tempo. */
        @DefaultValue("5") int recusasParaBloquear,
        @DefaultValue("10") int janelaMinutos,
        @DefaultValue("5") int bloqueioMinutos,
        /** Aviso no log quando as recusas do sistema todo, nos últimos 10 minutos, chegam a este número. */
        @DefaultValue("30") int alertaRecusas10Min,
        /** Aviso no log quando os tokens gastos na última hora chegam a este número. */
        @DefaultValue("200000") long alertaTokensHora,
        /** Pedidos por minuto, por IP, de categorização e de relatórios (cada pedido é uma chamada paga à IA). */
        @DefaultValue("10") int categorizacoesPorMinuto,
        @DefaultValue("3") int relatoriosPorMinuto) {
}
