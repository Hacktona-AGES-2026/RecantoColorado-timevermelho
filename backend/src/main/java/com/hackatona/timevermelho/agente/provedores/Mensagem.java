package com.hackatona.timevermelho.agente.provedores;

/** Uma mensagem da conversa. {@code papel} é "system", "user" ou "assistant". */
public record Mensagem(String papel, String conteudo) {

    public static Mensagem sistema(String texto) {
        return new Mensagem("system", texto);
    }

    public static Mensagem usuario(String texto) {
        return new Mensagem("user", texto);
    }

    public static Mensagem assistente(String texto) {
        return new Mensagem("assistant", texto);
    }
}
