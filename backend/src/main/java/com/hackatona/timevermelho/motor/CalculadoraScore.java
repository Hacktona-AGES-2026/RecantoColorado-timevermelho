package com.hackatona.timevermelho.motor;

import static com.hackatona.timevermelho.motor.TipoSinal.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Score de 0 a 100, dividido em 6 componentes com explicação.
 * Ordena pacientes dentro da mesma classe; não muda a classe.
 */
public class CalculadoraScore {

    /** Quanto cada sinal soma no componente "piora" (fração de 0 a 1; o total é limitado a 1). */
    private static final Map<TipoSinal, Double> PIORA = Map.ofEntries(
            Map.entry(ALARME_GRAVE, 0.6), Map.entry(PROGRESSAO_IMAGEM, 0.6), Map.entry(INTERNACAO_RECENTE, 0.4),
            Map.entry(PS_RECENTE, 0.3), Map.entry(REAVALIACAO_PIORA, 0.3), Map.entry(ALARME_FUNCIONAL, 0.3),
            Map.entry(SUSPEITA_MALIGNIDADE, 0.3), Map.entry(GRAVIDADE_CLINICA, 0.3), Map.entry(PS_TEXTO, 0.25), Map.entry(RELATA_PIORA, 0.2),
            Map.entry(HBA1C_ALTA, 0.2), Map.entry(HBA1C_SUBINDO, 0.1));

    public record Resultado(double total, List<ComponenteScore> componentes) {}

    public Resultado calcular(Caso c, List<Sinal> sinais, ClasseSwalis classe, Pesos pesos) {
        double soma = pesos.soma() <= 0 ? 1 : pesos.soma();
        List<ComponenteScore> comp = new ArrayList<>();

        // diagnóstico
        double fd;
        String ed;
        if (tem(sinais, ONCO_CID) || tem(sinais, BIOPSIA_POSITIVA)) {
            fd = 1.0;
            ed = tem(sinais, ONCO_CID) ? "Neoplasia maligna (CID " + c.cidFormatado() + ")" : "Biópsia positiva";
        } else if (tem(sinais, CID_URGENTE)) {
            fd = 0.85;
            ed = "CID de urgência (" + c.cidFormatado() + ")";
        } else if (tem(sinais, CID_INCERTO)) {
            fd = 0.5;
            ed = "Lesão de comportamento incerto (" + c.cidFormatado() + ")";
        } else {
            fd = 0;
            ed = "Diagnóstico benigno (" + c.cidFormatado() + ")";
        }
        comp.add(componente("diagnostico", "Diagnóstico", fd, pesos.diagnostico(), soma, ed));

        // piora
        double fp = 0;
        List<String> motivos = new ArrayList<>();
        for (Sinal s : sinais) {
            Double v = PIORA.get(s.tipo());
            if (v != null) {
                fp += v;
                if (motivos.size() < 3) motivos.add(s.descricao());
            }
        }
        fp = Math.min(fp, 1.0);
        comp.add(componente("piora", "Sinais de piora", fp, pesos.piora(), soma,
                motivos.isEmpty() ? "Nenhum sinal de piora registrado" : String.join("; ", motivos)));

        // dor
        double fdor = c.dor() == null ? 0 : c.dor() / 10.0;
        comp.add(componente("dor", "Dor", fdor, pesos.dor(), soma,
                c.dor() == null ? "Dor não informada" : "EVA " + c.dor() + "/10"));

        // limitação
        String lim = c.limitacao() == null ? "" : c.limitacao();
        double fl = switch (lim) {
            case "grave" -> 1.0;
            case "moderada" -> 0.6;
            case "leve" -> 0.3;
            default -> 0;
        };
        comp.add(componente("limitacao", "Limitação funcional", fl, pesos.limitacao(), soma,
                lim.isBlank() ? "Limitação não informada" : "Limitação " + lim));

        // espera relativa ao prazo da classe (satura em 3x o prazo)
        double pct = 100.0 * c.diasEmFila() / classe.prazoDias();
        double fe = Math.min(pct / 300.0, 1.0);
        comp.add(componente("espera", "Tempo de espera", fe, pesos.espera(), soma,
                c.diasEmFila() + " dias de fila (" + Math.round(pct) + "% do prazo de " + classe.prazoDias() + " dias)"));

        // condição geral e risco do procedimento
        int asa = c.asa() == null ? 2 : c.asa();
        int risco = c.riscoCirurgico() == null ? 2 : c.riscoCirurgico();
        double fr = 0.6 * (asa - 1) / 3.0 + 0.4 * (risco - 1) / 4.0;
        comp.add(componente("risco", "ASA e risco cirúrgico", fr, pesos.risco(), soma,
                "ASA " + (c.asa() == null ? "não informado" : asa) + ", risco do procedimento " + risco + "/5"));

        double total = comp.stream().mapToDouble(ComponenteScore::pontos).sum();
        return new Resultado(arred(total), comp);
    }

    private static ComponenteScore componente(String chave, String nome, double fracao, double peso, double soma,
                                              String explicacao) {
        double max = 100.0 * peso / soma;
        return new ComponenteScore(chave, nome, arred(fracao * max), arred(max), explicacao);
    }

    private static boolean tem(List<Sinal> sinais, TipoSinal t) {
        return sinais.stream().anyMatch(s -> s.tipo() == t);
    }

    private static double arred(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
