package com.hackatona.timevermelho.motor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Regra de ouro: nenhum paciente com neoplasia maligna abaixo de um paciente sem urgência clínica.
 *
 * Aplicada como restrição de ordenação, depois do score, sobre uma de duas referências:
 * - ATUAL (fila operacional): "sem urgência" = a classificação clínica atual não achou nenhuma
 *   evidência de urgência (classe D, com dor e limitação informadas) E os critérios do verificador,
 *   aplicados a todos os eventos (inclusive os novos), também não acham. Protegidos: CID C* ou biópsia
 *   positiva. Uma promoção manual sem evidência não muda nada disso.
 * - OFICIAL (fila de referência da entrega): exatamente o verificar_fila_priorizada.py — só eventos do
 *   dataset, dado ausente tratado como "nenhum"/0, protegidos só pelo CID C*.
 */
public class GuardRegraDeOuro {

    private static final Pattern KW_ALARME = Pattern.compile(
            "encarcer|estrangul|colangite|icter|pancreatite|enterorragia|melena|hematemese|suboclus|perda ponderal|disfagia|sepse|"
                    + "hidronefrose|perda de funcao|transfund|hemoptise|rouquidao|linfonodo.*(?:fixo|palpavel)|sangramento espontaneo|acamad|"
                    + "ulcera de pressao|quedas de repeticao|glaucoma facomorfico|retencao de co2|dano neurologico|psa em elevacao|dessaturacao|"
                    + "angina em repouso|sincope|crescimento rapido|retencao urinaria aguda|massa anexial|ca-125");

    private static final Set<String> CID_URGENTE = Set.of("I200", "K403", "K400", "K420", "K430", "K800", "N132", "I832");

    /** Fila de referência: exatamente o verificador oficial, sobre o dataset de referência. */
    public static boolean semUrgenciaOficial(Caso c) {
        return criteriosDoVerificador(c, true);
    }

    /**
     * Fila operacional: sem urgência só quando nada indica urgência. A classe calculada vem das regras
     * clínicas (não do ajuste do regulador), então uma promoção manual sem evidência não conta.
     */
    public static boolean semUrgenciaAtual(Caso c, ClasseSwalis classeCalculada) {
        if (classeCalculada != ClasseSwalis.D) return false;
        // dado ausente não é ausência de urgência
        if (c.dor() == null || c.limitacao() == null || c.limitacao().isBlank()) return false;
        return criteriosDoVerificador(c, false);
    }

    /** Os critérios do verificador valem sobre os dados atuais, mas não enxergam a urgência que só o FilaVIVA lê. */
    public static boolean verificadorNaoVeUrgencia(Caso c) {
        return criteriosDoVerificador(c, false);
    }

    /**
     * Critérios do verificar_fila_priorizada.py. Com soDataset, usa só os eventos que vieram do
     * eventos_clinicos.csv para a própria solicitação, como o verificador; sem, usa todos os eventos do caso.
     */
    private static boolean criteriosDoVerificador(Caso c, boolean soDataset) {
        if (c.oncologicoPeloCid()) return false;
        int dor = c.dor() == null ? 0 : c.dor();
        String lim = c.limitacao() == null || c.limitacao().isBlank() ? "nenhuma" : c.limitacao();
        if (dor > 3 || !(lim.equals("nenhuma") || lim.equals("leve"))) return false;
        if (CID_URGENTE.contains(c.cid())) return false;
        boolean eventoDePiora = c.eventos() != null && c.eventos().stream()
                .filter(e -> !soDataset || (!e.sintetico() && c.solicitacaoId().equals(e.solicitacaoOriginal())))
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
     * Recebe a fila já ordenada e faz o menor ajuste que garante a regra: todo paciente sem urgência
     * que esteja acima do último protegido desce para logo depois dele, mantendo a ordem entre eles.
     * Quem tem urgência não sai do lugar. Os campos semUrgencia e protegido já vêm da referência usada.
     */
    public List<ResultadoPaciente> aplicar(List<ResultadoPaciente> fila) {
        int ultimoOnco = -1;
        for (int i = 0; i < fila.size(); i++) {
            if (fila.get(i).isProtegido()) ultimoOnco = i;
        }
        if (ultimoOnco < 0) return fila;

        List<ResultadoPaciente> nova = new ArrayList<>(fila.size());
        List<ResultadoPaciente> descer = new ArrayList<>();
        for (int i = 0; i <= ultimoOnco; i++) {
            ResultadoPaciente r = fila.get(i);
            if (naoPodeFicarAcimaDeProtegido(r)) {
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

    /**
     * Quem a trava desce para baixo do último protegido: o paciente sem urgência e o que só subiu por
     * uma promoção manual sem evidência de agravamento registrada.
     */
    public static boolean naoPodeFicarAcimaDeProtegido(ResultadoPaciente r) {
        return r.isSemUrgencia() || r.isPromocaoSemEvidencia();
    }

    /** Confere se uma ordem respeita a regra. Usado nos testes e antes de exportar. */
    public static boolean respeitada(List<ResultadoPaciente> fila) {
        boolean viuCalmo = false;
        for (ResultadoPaciente r : fila) {
            if (naoPodeFicarAcimaDeProtegido(r)) viuCalmo = true;
            else if (viuCalmo && r.isProtegido()) return false;
        }
        return true;
    }
}
