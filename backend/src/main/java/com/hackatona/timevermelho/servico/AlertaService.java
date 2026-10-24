package com.hackatona.timevermelho.servico;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.dominio.AlertaRepository;
import com.hackatona.timevermelho.motor.Reavaliacao;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.motor.StatusFila;
import com.hackatona.timevermelho.motor.TipoReavaliacao;

/** A revisão diária do regulador: o que mudou, quem precisa ser olhado de novo e quem pode ter saído da fila. */
@Service
public class AlertaService {

    private static final int MAX_ITENS = 50;

    private final FilaService fila;
    private final AlertaRepository alertas;

    public AlertaService(FilaService fila, AlertaRepository alertas) {
        this.fila = fila;
        this.alertas = alertas;
    }

    public Dtos.Alertas alertas() {
        var snap = fila.atual();

        List<Dtos.AlertaDto> recentes = alertas.findTop50ByOrderByCriadoEmDescIdDesc().stream()
                .map(a -> new Dtos.AlertaDto(a.getId(), a.getSolicitacaoId(), a.getNome(), a.getTipo().name(),
                        a.getMensagem(), a.getClasseAntes() == null ? null : a.getClasseAntes().name(),
                        a.getClasseDepois() == null ? null : a.getClasseDepois().name(), a.getPosicaoAntes(),
                        a.getPosicaoDepois(), a.getCriadoEm())).toList();

        // todos já estão na ordem clínica: ativos primeiro, depois os fora da fila
        Map<TipoReavaliacao, List<Dtos.ItemReavaliacao>> grupos = new EnumMap<>(TipoReavaliacao.class);
        for (ResultadoPaciente r : snap.todos()) {
            if (r.getStatus() == StatusFila.SAIDA_CONFIRMADA) continue;
            for (Reavaliacao x : r.getReavaliacoes()) {
                grupos.computeIfAbsent(x.tipo(), k -> new ArrayList<>()).add(new Dtos.ItemReavaliacao(
                        r.getCaso().solicitacaoId(), r.getCaso().nome(), r.classe().name(), r.getPosicao(),
                        r.getStatus().name(), x.mensagem()));
            }
        }
        List<Dtos.GrupoReavaliacao> reavaliacao = new ArrayList<>();
        for (var e : grupos.entrySet()) {
            var itens = e.getValue();
            reavaliacao.add(new Dtos.GrupoReavaliacao(e.getKey().name(), e.getKey().titulo(), itens.size(),
                    itens.subList(0, Math.min(MAX_ITENS, itens.size()))));
        }

        List<Dtos.Saida> saidas = snap.todos().stream()
                .filter(r -> r.getSinalSaida() != null && r.getStatus() != StatusFila.SAIDA_CONFIRMADA)
                .map(r -> new Dtos.Saida(r.getCaso().solicitacaoId(), r.getCaso().nome(), r.getCaso().cidFormatado(),
                        r.oncologico(), r.getStatus().name(), r.getSinalSaida().descricao(),
                        r.getSinalSaida().evidencia(), r.getSinalSaida().fonte()))
                .toList();

        return new Dtos.Alertas(recentes, reavaliacao, saidas);
    }
}
