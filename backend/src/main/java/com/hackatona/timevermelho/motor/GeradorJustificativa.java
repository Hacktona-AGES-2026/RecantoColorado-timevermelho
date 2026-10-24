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
        if (r.isClassePorTexto() && !cl.ajustada()) {
            partes.add("classe provisória: urgência descrita só no texto, pendente de revisão clínica (dados estruturados: "
                    + r.getClasseEstruturada() + ")");
        }
        if (r.getEvidenciaDaPromocao() != null) {
            // rastreabilidade: qual achado registrado sustenta a promoção
            partes.add("evidência da promoção: " + r.getEvidenciaDaPromocao().descricao()
                    + " (" + r.getEvidenciaDaPromocao().fonte() + ")");
        }
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
            partes.add(r.getReferencia() == Referencia.OFICIAL
                    ? "posição limitada pela regra de ouro: sem urgência pela definição oficial, fica abaixo de todos os oncológicos"
                    : r.isPromocaoSemEvidencia() && !r.isSemUrgencia()
                    ? "posição limitada pela regra de ouro: promoção do regulador sem evidência de agravamento registrada, fica abaixo de todos os oncológicos"
                    : "posição limitada pela regra de ouro: sem evidência clínica de urgência registrada, fica abaixo de todos os oncológicos");
        }
        if (r.getStatus() == StatusFila.SAIDA_SUGERIDA) {
            partes.add("saída sugerida aguardando o regulador (" + r.getSinalSaida().descricao() + ")");
        }
        return String.join(" · ", partes);
    }
}
