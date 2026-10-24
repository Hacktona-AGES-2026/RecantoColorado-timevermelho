package com.hackatona.timevermelho.motor;

import static com.hackatona.timevermelho.motor.TipoSinal.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Mapeia os sinais para uma classe SWALIS. A primeira regra que bater vence (da mais urgente para a menos).
 * Todo oncológico (CID C* ou biópsia positiva) fica no mínimo em A2.
 */
public class ClassificadorSwalis {

    private static final Set<TipoSinal> PROGRESSAO_ONCO = EnumSet.of(PROGRESSAO_IMAGEM, PS_RECENTE,
            INTERNACAO_RECENTE, ALARME_GRAVE, SUSPEITA_MALIGNIDADE, REAVALIACAO_PIORA);

    private static final Set<TipoSinal> CLASSE_B = EnumSet.of(DOR_INTENSA, LIMITACAO_GRAVE, PS_RECENTE,
            INTERNACAO_RECENTE, PS_TEXTO, REAVALIACAO_PIORA, RELATA_PIORA, CID_INCERTO, SUSPEITA_MALIGNIDADE,
            ALARME_FUNCIONAL, GRAVIDADE_CLINICA, PROGRESSAO_IMAGEM);

    private static final Set<TipoSinal> CLASSE_C = EnumSet.of(DOR_MODERADA, LIMITACAO_MODERADA);

    public Classificacao classificar(List<Sinal> sinais) {
        List<Sinal> onco = filtrar(sinais, EnumSet.of(ONCO_CID, BIOPSIA_POSITIVA));

        if (!onco.isEmpty()) {
            List<Sinal> progressao = filtrar(sinais, PROGRESSAO_ONCO);
            if (!progressao.isEmpty()) {
                return nova(ClasseSwalis.A1, "Neoplasia maligna com sinais de progressão", concat(onco, progressao));
            }
        }
        List<Sinal> cidUrgente = filtrar(sinais, EnumSet.of(CID_URGENTE));
        if (!cidUrgente.isEmpty()) {
            return nova(ClasseSwalis.A1, "Diagnóstico de urgência pelo CID", cidUrgente);
        }
        List<Sinal> alarme = filtrar(sinais, EnumSet.of(ALARME_GRAVE));
        if (!alarme.isEmpty()) {
            return nova(ClasseSwalis.A1, "Sinal de alarme grave descrito pelo médico", alarme);
        }
        if (!onco.isEmpty()) {
            boolean soBiopsia = onco.stream().noneMatch(x -> x.tipo() == ONCO_CID);
            return nova(ClasseSwalis.A2, soBiopsia
                    ? "Biópsia positiva: tratado como neoplasia maligna mesmo com CID benigno no cadastro"
                    : "Neoplasia maligna confirmada (piso para todo oncológico)", onco);
        }
        List<Sinal> b = filtrar(sinais, CLASSE_B);
        if (!b.isEmpty()) {
            return nova(ClasseSwalis.B, "Dor, incapacidade ou piora importantes", b);
        }
        List<Sinal> c = filtrar(sinais, CLASSE_C);
        if (!c.isEmpty()) {
            return nova(ClasseSwalis.C, "Dor ou limitação moderadas", c);
        }
        List<Sinal> incompleto = sinais.stream()
                .filter(x -> x.tipo() == DADO_INCOMPLETO && !"justificativa_clinica".equals(x.fonte())).toList();
        if (!incompleto.isEmpty()) {
            return nova(ClasseSwalis.C, "Dado de dor ou limitação faltando: não rebaixamos para D sem informação",
                    incompleto);
        }
        return nova(ClasseSwalis.D, "Sem dor, limitação ou sinal de piora relevantes", List.of());
    }

    private static Classificacao nova(ClasseSwalis classe, String regra, List<Sinal> determinantes) {
        return new Classificacao(classe, classe, regra, determinantes, false, null);
    }

    private static List<Sinal> filtrar(List<Sinal> sinais, Set<TipoSinal> tipos) {
        return sinais.stream().filter(x -> tipos.contains(x.tipo())).toList();
    }

    private static List<Sinal> concat(List<Sinal> a, List<Sinal> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }
}
