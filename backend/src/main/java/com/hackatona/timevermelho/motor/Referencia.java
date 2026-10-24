package com.hackatona.timevermelho.motor;

/** Sobre quais dados e com qual interpretação a regra de ouro é aplicada. */
public enum Referencia {
    /**
     * Fila operacional (a que o regulador vê): situação clínica atual, inclusive eventos registrados
     * depois da importação. "Sem urgência" = nenhuma evidência clínica de urgência; dado ausente não conta.
     */
    ATUAL,
    /**
     * Fila de referência da entrega (fila_priorizada.csv): só o dataset oficial, com exatamente a
     * interpretação do verificar_fila_priorizada.py, para o arquivo ser reproduzível e passar nele.
     */
    OFICIAL
}
