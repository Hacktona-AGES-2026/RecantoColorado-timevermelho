package com.hackatona.timevermelho.agente.provedores;

/**
 * Falha de uma chamada a um provedor de IA. A mensagem é sempre resumida (status e código do erro):
 * nunca carrega cabeçalhos, chave nem o corpo da resposta do provedor.
 */
public class ErroProvedor extends RuntimeException {

    public enum Tipo {
        /** Crédito ou cota acabou: o provedor sai da rotação por um tempo e passa para o próximo. */
        CREDITO_ESGOTADO,
        /** Limite de requisições momentâneo: espera e tenta de novo no mesmo provedor. */
        LIMITE_TEMPORARIO,
        /** Erro do servidor, timeout ou rede: tenta o próximo provedor. */
        INDISPONIVEL,
        /** Chave recusada ou sem permissão: provedor mal configurado, sai da rotação por um tempo. */
        AUTENTICACAO,
        /** Pedido inválido: erro nosso, trocar de provedor não resolve. */
        PEDIDO_INVALIDO
    }

    private final Tipo tipo;

    public ErroProvedor(Tipo tipo, String mensagem) {
        super(mensagem);
        this.tipo = tipo;
    }

    public ErroProvedor(Tipo tipo, String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.tipo = tipo;
    }

    public Tipo tipo() {
        return tipo;
    }
}
