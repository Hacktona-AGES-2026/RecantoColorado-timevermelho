package com.hackatona.timevermelho.api.dto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.hackatona.timevermelho.importacao.Normalizacao;
import com.hackatona.timevermelho.motor.Caso;
import com.hackatona.timevermelho.motor.Evento;

/**
 * Resumo da saúde do paciente para mostrar na fila: diabetes (confirmado pelos dados estruturados), última HbA1c e as
 * comorbidades do cadastro. Só leitura dos dados que já existem; não muda classe, score nem posição.
 */
public final class SaudePaciente {

    private SaudePaciente() {}

    static final double HBA1C_LIMITE = 8.5;

    /** Rótulo em português de cada comorbidade do dataset. */
    private static final Map<String, String> ROTULOS = new LinkedHashMap<>();
    static {
        ROTULOS.put("dm2", "Diabetes tipo 2");
        ROTULOS.put("has", "Hipertensão");
        ROTULOS.put("icc", "Insuficiência cardíaca");
        ROTULOS.put("cardiopatia isquemica", "Cardiopatia isquêmica");
        ROTULOS.put("fibrilacao atrial", "Fibrilação atrial");
        ROTULOS.put("anticoagulacao oral", "Anticoagulação oral");
        ROTULOS.put("dpoc", "DPOC");
        ROTULOS.put("asma", "Asma");
        ROTULOS.put("drc", "Doença renal crônica");
        ROTULOS.put("obesidade", "Obesidade");
        ROTULOS.put("tabagismo", "Tabagismo");
        ROTULOS.put("hipotireoidismo", "Hipotireoidismo");
        ROTULOS.put("depressao", "Depressão");
    }

    public static Dtos.Saude de(Caso c) {
        List<String> comorbidades = new ArrayList<>();
        if (c.comorbidades() != null) {
            for (String parte : c.comorbidades().split(";")) {
                String k = Normalizacao.semAcento(parte.trim()).toLowerCase(Locale.ROOT);
                if (k.isEmpty() || k.equals("nenhuma")) continue;
                comorbidades.add(ROTULOS.getOrDefault(k, parte.trim()));
            }
        }
        boolean dmNaLista = c.diabetesNasComorbidades();
        // diabetes: lista de comorbidades, flag manual e CID secundário (E10–E14)
        List<String> fontes = new ArrayList<>();
        if (dmNaLista) fontes.add("comorbidades");
        if ("sim".equals(c.flagDiabetico())) fontes.add("flag do cadastro");
        String sec = c.cidSecundario() == null ? "" : c.cidSecundario().toUpperCase(Locale.ROOT);
        if (sec.matches("E1[0-4].*")) fontes.add("CID secundário " + Normalizacao.cidFormatado(sec));
        boolean diabetes = c.diabetesNoCadastro();
        // divergência: a flag e a lista discordam (uma diz que sim, a outra não)
        boolean divergencia = dmNaLista != "sim".equals(c.flagDiabetico());
        if (diabetes && !dmNaLista) comorbidades.add(0, "Diabetes (pela " + fontes.getFirst() + ")");

        // última HbA1c: exame mais recente; se não houver, a do cadastro
        Evento ultimo = c.eventos() == null ? null : c.eventos().stream()
                .filter(e -> "hba1c".equals(e.tipo()) && e.valor() != null).max(Comparator.comparing(Evento::data)).orElse(null);
        Double hba1c = ultimo != null ? ultimo.valor() : c.hba1cCadastro();
        java.time.LocalDate data = ultimo != null ? ultimo.data() : c.dataHba1cCadastro();
        String origem = ultimo != null ? "exame" : hba1c != null ? "cadastro" : null;
        return new Dtos.Saude(diabetes, fontes, divergencia, hba1c, data, origem, hba1c != null && hba1c > HBA1C_LIMITE,
                comorbidades);
    }
}
