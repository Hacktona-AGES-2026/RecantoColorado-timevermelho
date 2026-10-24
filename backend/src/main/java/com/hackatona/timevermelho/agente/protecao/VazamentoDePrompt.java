package com.hackatona.timevermelho.agente.protecao;

import java.util.HashSet;
import java.util.Set;

import com.hackatona.timevermelho.motor.texto.ExtratorDeTexto;

/**
 * Última barreira na saída: se a resposta repete um pedaço longo do System Prompt ou das regras de negócio,
 * ela é trocada por uma recusa. Compara trechos de 60 caracteres, ignorando acento, caixa e espaços repetidos.
 */
public final class VazamentoDePrompt {

    private static final int JANELA = 60;

    private final Set<String> trechos = new HashSet<>();

    public VazamentoDePrompt(String textoSistema) {
        String t = normalizar(textoSistema);
        for (int i = 0; i + JANELA <= t.length(); i += 20) trechos.add(t.substring(i, i + JANELA));
    }

    public boolean vazou(String resposta) {
        String r = normalizar(resposta);
        for (int i = 0; i + JANELA <= r.length(); i++) {
            // todo trecho da resposta é comparado; os do prompt foram amostrados de 20 em 20, então testamos as
            // janelas da resposta contra todas as janelas possíveis do prompt dentro de um deslocamento de 20.
            if (trechos.contains(r.substring(i, i + JANELA))) return true;
        }
        return false;
    }

    private static String normalizar(String s) {
        return ExtratorDeTexto.normalizar(s == null ? "" : s).replaceAll("[^a-z0-9]+", " ").strip();
    }
}
