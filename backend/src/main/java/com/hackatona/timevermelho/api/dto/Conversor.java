package com.hackatona.timevermelho.api.dto;

import java.util.List;

import com.hackatona.timevermelho.motor.ComponenteScore;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.motor.Sinal;

/** Converte o resultado do motor para os DTOs da API. */
public final class Conversor {

    private Conversor() {}

    public static Dtos.SnapshotDto snapshot(com.hackatona.timevermelho.servico.FilaService.Info i) {
        String descricao = switch (i.tipo()) {
            case com.hackatona.timevermelho.servico.FilaService.OFICIAL ->
                    "Fila de referência — entrega oficial: só o dataset oficial, com a interpretação do verificador. "
                            + "Gera o fila_priorizada.csv.";
            case com.hackatona.timevermelho.servico.FilaService.OPERACIONAL ->
                    "Fila operacional: dataset + eventos e decisões registrados. As prioridades são propostas, "
                            + "sujeitas à decisão do regulador.";
            default -> "Simulação: nada foi gravado.";
        };
        return new Dtos.SnapshotDto(i.tipo(), i.versao(), i.calculadoEm(), i.eventosRegistrados(),
                i.decisoesRegulador(), descricao);
    }

    public static Dtos.ItemFila item(ResultadoPaciente r) {
        var c = r.getCaso();
        var cl = r.getClassificacao();
        Integer delta = r.getPosicao() == null || r.getPosicaoManual() == null ? null
                : r.getPosicaoManual() - r.getPosicao();
        List<Dtos.Motivo> motivos = cl.determinantes().stream().limit(3)
                .map(s -> new Dtos.Motivo(s.descricao(), s.evidencia(), s.fonte())).toList();
        if (motivos.isEmpty()) motivos = List.of(new Dtos.Motivo(cl.regra(), null, null));
        return new Dtos.ItemFila(c.solicitacaoId(), c.pacienteId(), c.nome(), c.idade(), c.sexo(), c.municipio(),
                c.procedimento(), c.especialidade(), c.cidFormatado(), c.cidDescricao(), r.oncologico(),
                r.getPosicao(), r.getPosicaoManual(), delta, cl.classe().name(), cl.classeCalculada().name(),
                cl.ajustada(), cl.classe().prazoDias(), c.diasEmFila(), r.percentualPrazo(), r.getScore(),
                c.prioridadeManual(), r.getStatus().name(), r.isMovidoPelaRegraDeOuro(), r.getReavaliacoes().size(),
                motivos, r.getJustificativa(), r.getEstadoRebaixamento(), r.getDesejoCirurgia(), r.getPosicaoFixada());
    }

    public static Dtos.AjusteDto ajuste(com.hackatona.timevermelho.dominio.AjusteRegulador a) {
        return new Dtos.AjusteDto(a.getId(), a.getSolicitacaoId(), a.getTipo().name(),
                a.getClasseAnterior() == null ? null : a.getClasseAnterior().name(),
                a.getClasseNova() == null ? null : a.getClasseNova().name(), a.getJustificativa(), a.getAutor(),
                a.getCriadoEm(), a.getMedicoId(), a.isInformacaoForaDoSistema(), a.getPosicaoFixada());
    }

    public static Dtos.SinalDto sinal(Sinal s) {
        if (s == null) return null;
        return new Dtos.SinalDto(s.tipo().name(), s.tipo().rotulo(), s.tipo().categoria().name(), s.descricao(),
                s.evidencia(), s.fonte(), s.data());
    }

    public static Dtos.Componente componente(ComponenteScore c) {
        return new Dtos.Componente(c.chave(), c.nome(), c.pontos(), c.maximo(), c.explicacao());
    }

    public static Dtos.ClassificacaoDto classificacao(ResultadoPaciente r) {
        var cl = r.getClassificacao();
        return new Dtos.ClassificacaoDto(cl.classe().name(), cl.classeCalculada().name(), cl.classe().descricao(),
                cl.classe().prazoDias(), cl.regra(), cl.ajustada(), cl.justificativaAjuste(),
                cl.determinantes().stream().map(Conversor::sinal).toList());
    }
}
