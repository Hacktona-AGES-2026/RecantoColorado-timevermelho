package com.hackatona.timevermelho.motor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Monta a frase que explica a posição de cada paciente (vai para a tela e para o fila_priorizada.csv). */
public class GeradorJustificativa {

    public String gerar(ResultadoPaciente r) {
        Classificacao cl = r.getClassificacao();
        List<String> partes = new ArrayList<>();
        partes.add(cl.classe().name() + " (prazo " + cl.classe().prazoDias() + " dias)");
        partes.add(cl.ajustada() ? "classe definida pelo regulador: " + cl.justificativaAjuste() : cl.regra());
        Set<String> motivos = new LinkedHashSet<>();
        for (Sinal s : cl.determinantes()) {
            motivos.add(s.descricao());
            if (motivos.size() == 3) break;
        }
        partes.addAll(motivos);
        if (r.temSinal(TipoSinal.BIOPSIA_POSITIVA) && !r.getCaso().oncologicoPeloCid()) {
            partes.add("CID " + r.getCaso().cidFormatado() + " no cadastro diverge do laudo");
        }
        partes.add(r.getCaso().diasEmFila() + " dias de fila (" + Math.round(r.percentualPrazo()) + "% do prazo)");
        partes.add("score " + Math.round(r.getScore()));
        if (r.isMovidoPelaRegraDeOuro()) {
            partes.add("posição limitada pela regra de ouro: sem urgência pela definição oficial, fica abaixo de todos os oncológicos");
        }
        if (r.getStatus() == StatusFila.SAIDA_SUGERIDA) {
            partes.add("saída sugerida aguardando o regulador (" + r.getSinalSaida().descricao() + ")");
        }
        return String.join(" · ", partes);
    }
}
