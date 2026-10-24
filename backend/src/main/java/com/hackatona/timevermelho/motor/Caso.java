package com.hackatona.timevermelho.motor;

import java.time.LocalDate;
import java.util.List;

import lombok.Builder;

/** Uma solicitação cirúrgica já limpa, pronta para o motor. Independente do banco. */
@Builder(toBuilder = true)
public record Caso(
        String solicitacaoId,
        String pacienteId,
        String nome,
        String sexo,
        Integer idade,
        String municipio,
        Integer distanciaKm,
        String procedimentoId,
        String procedimento,
        String especialidade,
        Integer riscoCirurgico,
        String cid,
        String cidFormatado,
        String cidDescricao,
        String cidSecundario,
        Integer asa,
        String comorbidades,
        Integer dor,
        String limitacao,
        String justificativa,
        String observacoes,
        LocalDate dataEntrada,
        int diasEmFila,
        String prioridadeManual,
        LocalDate dataUltimaRevisao,
        String flagOncologico,
        String flagDiabetico,
        String flagPiora,
        List<Evento> eventos,
        // pré-operatório (só para a avaliação de aptidão; o motor de priorização não usa)
        Double hba1cCadastro,
        java.time.LocalDate dataHba1cCadastro,
        String avaliacaoPreAnestesica,
        String examesPreOpValidos,
        String porte,
        Integer duracaoMin,
        String necessitaUti) {

    public boolean oncologicoPeloCid() {
        return cid != null && cid.startsWith("C");
    }

    /**
     * Diabetes registrado no cadastro: DM na lista de comorbidades, flag_diabetico = sim ou CID secundário E10–E14.
     * HbA1c alta sozinha não conta: sem um desses campos o paciente não é chamado de diabético.
     */
    public boolean diabetesNoCadastro() {
        return diabetesNasComorbidades() || "sim".equals(flagDiabetico)
                || (cidSecundario != null && cidSecundario.toUpperCase(java.util.Locale.ROOT).matches("E1[0-4].*"));
    }

    /** DM1, DM2 ou "diabetes" escrito na lista de comorbidades. */
    public boolean diabetesNasComorbidades() {
        if (comorbidades == null) return false;
        String t = comorbidades.toLowerCase(java.util.Locale.ROOT);
        return java.util.regex.Pattern.compile("\\bdm[12]?\\b|diabet").matcher(t).find();
    }
}
