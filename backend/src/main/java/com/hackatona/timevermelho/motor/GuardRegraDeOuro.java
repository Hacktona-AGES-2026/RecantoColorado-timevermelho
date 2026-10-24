package com.hackatona.timevermelho.motor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Regra de ouro: nenhum paciente com CID C* abaixo de um paciente sem urgência clínica.
 *
 * Usa exatamente a definição conservadora do verificar_fila_priorizada.py (só os dados e eventos
 * da própria solicitação, palavras-chave sem tratar negação). Assim, o que passa aqui passa lá.
 */
public class GuardRegraDeOuro {

    private static final Pattern KW_ALARME = Pattern.compile(
            "encarcer|estrangul|colangite|icter|pancreatite|enterorragia|melena|hematemese|suboclus|perda ponderal|disfagia|sepse|"
                    + "hidronefrose|perda de funcao|transfund|hemoptise|rouquidao|linfonodo.*(?:fixo|palpavel)|sangramento espontaneo|acamad|"
                    + "ulcera de pressao|quedas de repeticao|glaucoma facomorfico|retencao de co2|dano neurologico|psa em elevacao|dessaturacao|"
                    + "angina em repouso|sincope|crescimento rapido|retencao urinaria aguda|massa anexial|ca-125");

    private static final Set<String> CID_URGENTE = Set.of("I200", "K403", "K400", "K420", "K430", "K800", "N132", "I832");

    public static boolean semUrgenciaClinica(Caso c) {
        if (c.oncologicoPeloCid()) return false;
        int dor = c.dor() == null ? 0 : c.dor();
        String lim = c.limitacao() == null || c.limitacao().isBlank() ? "nenhuma" : c.limitacao();
        if (dor > 3 || !(lim.equals("nenhuma") || lim.equals("leve"))) return false;
        if (CID_URGENTE.contains(c.cid())) return false;
        boolean eventoDePiora = c.eventos() != null && c.eventos().stream()
                .filter(e -> c.solicitacaoId().equals(e.solicitacaoOriginal()))
                .anyMatch(e -> switch (e.tipo()) {
                    case "pronto_socorro", "internacao" -> true;
                    case "biopsia" -> "positivo".equals(e.resultado());
                    case "exame_imagem" -> "progressao".equals(e.resultado());
                    case "consulta_reavaliacao" -> "piora".equals(e.resultado());
                    default -> false;
                });
        if (eventoDePiora) return false;
        String texto = c.justificativa() == null ? "" : c.justificativa().toLowerCase(Locale.ROOT);
        return !KW_ALARME.matcher(texto).find();
    }

    /**
     * Recebe a fila já ordenada e faz o menor ajuste que garante a regra: todo paciente
     * "sem urgência" (definição oficial) que esteja acima do último oncológico desce para logo
     * depois dele, mantendo a ordem entre eles. Quem tem urgência não sai do lugar.
     */
    public List<ResultadoPaciente> aplicar(List<ResultadoPaciente> fila) {
        int ultimoOnco = -1;
        for (int i = 0; i < fila.size(); i++) {
            if (fila.get(i).getCaso().oncologicoPeloCid()) ultimoOnco = i;
        }
        if (ultimoOnco < 0) return fila;

        List<ResultadoPaciente> nova = new ArrayList<>(fila.size());
        List<ResultadoPaciente> descer = new ArrayList<>();
        for (int i = 0; i <= ultimoOnco; i++) {
            ResultadoPaciente r = fila.get(i);
            if (r.isSemUrgencia()) {
                r.setMovidoPelaRegraDeOuro(true);
                descer.add(r);
            } else {
                nova.add(r);
            }
        }
        nova.addAll(descer);
        nova.addAll(fila.subList(ultimoOnco + 1, fila.size()));
        return nova;
    }

    /** Confere se uma ordem respeita a regra. Usado nos testes e antes de exportar. */
    public static boolean respeitada(List<ResultadoPaciente> fila) {
        boolean viuCalmo = false;
        for (ResultadoPaciente r : fila) {
            if (r.isSemUrgencia()) viuCalmo = true;
            else if (viuCalmo && r.getCaso().oncologicoPeloCid()) return false;
        }
        return true;
    }
}
