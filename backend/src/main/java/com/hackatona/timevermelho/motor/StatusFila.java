package com.hackatona.timevermelho.motor;

public enum StatusFila {
    /** Na fila ativa, com posição. */
    ATIVA,
    /**
     * Há evidência de que saiu da fila (desistiu, operou em outro lugar), mas ninguém confirmou.
     * Continua na fila, na posição calculada, até o regulador decidir.
     */
    SAIDA_SUGERIDA,
    /** Regulador confirmou a saída: fica fora da fila. */
    SAIDA_CONFIRMADA;

    /** Ocupa uma posição na fila (só a saída confirmada tira o paciente). */
    public boolean naFila() {
        return this != SAIDA_CONFIRMADA;
    }
}
