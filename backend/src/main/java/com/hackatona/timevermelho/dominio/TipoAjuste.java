package com.hackatona.timevermelho.dominio;

/** O que o regulador decidiu (ou o sistema sugeriu) sobre um paciente. Tudo fica na trilha de auditoria. */
public enum TipoAjuste {
    /** Concorda com a classe vigente (não muda a classe). */
    CONFIRMAR_CLASSE,
    /** Define uma classe igual ou mais urgente. Uma classe menos urgente vira SUGERIR_REBAIXAMENTO. */
    ALTERAR_CLASSE,
    /** Confirma que o paciente saiu da fila (desistiu, operou em outro serviço). */
    CONFIRMAR_SAIDA,
    /** Recusa a sugestão de saída: o paciente continua na fila. */
    RECUSAR_SAIDA,
    /** Rebaixamento sugerido (manual ou pelo recálculo): a classe vigente é mantida até o regulador decidir. */
    SUGERIR_REBAIXAMENTO,
    /** Regulador confirma o rebaixamento sugerido. */
    CONFIRMAR_REBAIXAMENTO,
    /** Regulador rejeita o rebaixamento sugerido: a classe vigente fica como piso. */
    REJEITAR_REBAIXAMENTO,
    /** Registrado pelo sistema: o recálculo baixaria a classe de um oncológico, e isso nunca é permitido. */
    REBAIXAMENTO_BLOQUEADO,
    /** Coloca o paciente numa posição escolhida (só para cima). A regra de ouro continua valendo. */
    FIXAR_POSICAO,
    /** Desfaz a posição escolhida: o paciente volta para a posição calculada. */
    LIBERAR_POSICAO,
    /**
     * Exame ou evento clínico registrado por uma pessoa (regulador ou médico). Só auditoria: quem registrou, quando e
     * o impacto. Não é decisão de prioridade; o efeito na fila vem do próprio evento, recalculado pelo motor.
     */
    REGISTRAR_EVENTO;

    public boolean ehRebaixamento() {
        return this == SUGERIR_REBAIXAMENTO || this == CONFIRMAR_REBAIXAMENTO || this == REJEITAR_REBAIXAMENTO
                || this == REBAIXAMENTO_BLOQUEADO;
    }
}
