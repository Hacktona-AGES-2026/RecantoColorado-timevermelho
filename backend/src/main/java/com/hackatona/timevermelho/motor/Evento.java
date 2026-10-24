package com.hackatona.timevermelho.motor;

import java.time.LocalDate;

/**
 * Evento clínico visto pelo motor.
 *
 * @param solicitacaoOriginal solicitação a que o evento estava ligado no CSV (pode ser uma duplicata mesclada)
 */
public record Evento(String id, String solicitacaoOriginal, LocalDate data, String tipo, String resultado,
                     Double valor, String descricao, String origem, boolean sintetico) {}
