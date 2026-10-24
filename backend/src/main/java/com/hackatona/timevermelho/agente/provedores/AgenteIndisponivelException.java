package com.hackatona.timevermelho.agente.provedores;

/** Nenhum provedor conseguiu atender (todos sem crédito, fora do ar ou ainda em espera). */
public class AgenteIndisponivelException extends RuntimeException {

    public AgenteIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
