package com.hackatona.timevermelho.servico;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.hackatona.timevermelho.motor.Caso;
import com.hackatona.timevermelho.motor.DecisaoRegulador;
import com.hackatona.timevermelho.motor.MotorPriorizacao;
import com.hackatona.timevermelho.motor.Pesos;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.motor.StatusFila;
import com.hackatona.timevermelho.motor.texto.RegrasClinicas;

/** Monta, à mão, uma fila pequena e um {@link FilaService.Snapshot} sem banco nem Spring. */
final class FilaDeTeste {

    static final LocalDate REF = LocalDate.of(2026, 10, 23);
    static final MotorPriorizacao MOTOR = new MotorPriorizacao(RegrasClinicas.carregarPadrao(), REF);

    private FilaDeTeste() {}

    static Caso.CasoBuilder caso(String id, String cid) {
        return Caso.builder().solicitacaoId(id).pacienteId("PAC-" + id).nome("Paciente " + id).cid(cid).cidFormatado(cid)
                .procedimento("Proc").especialidade("Cirurgia Geral").riscoCirurgico(2).asa(2).dor(1).limitacao("nenhuma")
                .idade(50).distanciaKm(10).justificativa("Paciente com indicacao cirurgica eletiva, estavel.")
                .dataEntrada(REF.minusDays(100)).diasEmFila(100).prioridadeManual("media")
                .flagOncologico(cid.startsWith("C") ? "sim" : "nao").flagPiora("nao").eventos(List.of());
    }

    static FilaService.Snapshot snapshot(List<Caso> casos) {
        return snapshot(casos, Map.of());
    }

    static FilaService.Snapshot snapshot(List<Caso> casos, Map<String, DecisaoRegulador> decisoes) {
        List<ResultadoPaciente> todos = MOTOR.priorizar(casos, Pesos.PADRAO, decisoes);
        Map<String, ResultadoPaciente> porId = todos.stream().collect(Collectors.toMap(
                r -> r.getCaso().solicitacaoId(), Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<ResultadoPaciente> ativos = todos.stream().filter(r -> r.getStatus() == StatusFila.ATIVA).toList();
        return new FilaService.Snapshot(casos, decisoes, todos, porId, ativos);
    }
}
