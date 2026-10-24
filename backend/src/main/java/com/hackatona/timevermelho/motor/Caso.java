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
        List<Evento> eventos) {

    public boolean oncologicoPeloCid() {
        return cid != null && cid.startsWith("C");
    }
}
