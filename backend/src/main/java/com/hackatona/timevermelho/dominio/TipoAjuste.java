package com.hackatona.timevermelho.dominio;

/** O que o regulador decidiu sobre um paciente. */
public enum TipoAjuste {
    /** Concorda com a classe calculada. */
    CONFIRMAR_CLASSE,
    /** Define outra classe (oncológico nunca abaixo de A2). */
    ALTERAR_CLASSE,
    /** Confirma que o paciente saiu da fila (desistiu, operou em outro serviço). */
    CONFIRMAR_SAIDA,
    /** Recusa a sugestão de saída: o paciente continua na fila. */
    RECUSAR_SAIDA
}
