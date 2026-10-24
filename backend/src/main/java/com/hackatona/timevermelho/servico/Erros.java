package com.hackatona.timevermelho.servico;

/** Erros de negócio com mensagem pronta para o usuário. */
public final class Erros {

    private Erros() {}

    /** Pedido válido no formato, mas proibido pelas regras (ex.: rebaixar oncológico). Vira HTTP 422. */
    public static class RegraNegocio extends RuntimeException {
        public RegraNegocio(String mensagem) { super(mensagem); }
    }

    /** Vira HTTP 404. */
    public static class NaoEncontrado extends RuntimeException {
        public NaoEncontrado(String mensagem) { super(mensagem); }
    }
}
