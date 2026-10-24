package com.hackatona.timevermelho.motor;

public enum StatusFila {
    /** Na fila ativa, com posição. */
    ATIVA,
    /** Evidência de que saiu da fila (desistiu, operou em outro lugar). Aguarda confirmação do regulador. */
    SAIDA_SUGERIDA,
    /** Regulador confirmou a saída. */
    SAIDA_CONFIRMADA
}
