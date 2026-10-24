package com.hackatona.timevermelho.servico;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.hackatona.timevermelho.api.dto.Conversor;
import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.motor.Caso;
import com.hackatona.timevermelho.motor.Evento;
import com.hackatona.timevermelho.motor.GuardRegraDeOuro;
import com.hackatona.timevermelho.motor.Pesos;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.motor.Sinal;

/** "E se?": roda o motor com dados hipotéticos sem gravar nada. */
@Service
public class SimulacaoService {

    private final FilaService fila;
    private final DecisaoService decisoes;

    public SimulacaoService(FilaService fila, DecisaoService decisoes) {
        this.fila = fila;
        this.decisoes = decisoes;
    }

    public Dtos.Simulacao simular(Dtos.SimulacaoRequest req) {
        String id = decisoes.resolverId(req.solicitacaoId());
        var snap = fila.atual();
        ResultadoPaciente antes = snap.get(id);
        if (antes == null) throw new Erros.NaoEncontrado("Solicitação " + id + " não está na fila");

        Caso original = antes.getCaso();
        List<Evento> eventos = new ArrayList<>(original.eventos());
        int n = 0;
        if (req.eventos() != null) {
            for (var h : req.eventos()) {
                eventos.add(new Evento("SIMULADO-" + (++n), id, fila.referencia(), h.tipo(), h.resultado(),
                        h.valorNumerico(), h.descricao() == null ? "Evento hipotético" : h.descricao(), "simulação", true));
            }
        }
        String texto = original.justificativa();
        if (req.textoAdicional() != null && !req.textoAdicional().isBlank()) {
            texto = (texto == null ? "" : texto + " ") + req.textoAdicional().trim();
        }
        Caso hipotetico = original.toBuilder()
                .dor(req.dor() != null ? req.dor() : original.dor())
                .limitacao(req.limitacao() != null && !req.limitacao().isBlank() ? req.limitacao() : original.limitacao())
                .justificativa(texto)
                .eventos(List.copyOf(eventos))
                .build();

        List<Caso> casos = snap.casos().stream().map(c -> c.solicitacaoId().equals(id) ? hipotetico : c).toList();
        ResultadoPaciente depois = fila.calcular(casos, snap.decisoes(), Pesos.PADRAO).get(id);

        List<Sinal> novos = ImpactoCalculadora.sinaisNovos(antes, depois);
        String motivo = novos.isEmpty() ? "nenhum sinal novo" : novos.getFirst().descricao();
        return new Dtos.Simulacao(Conversor.item(antes), Conversor.item(depois),
                novos.stream().map(Conversor::sinal).toList(),
                depois.getComponentes().stream().map(Conversor::componente).toList(),
                "Simulação (nada foi salvo). " + ImpactoCalculadora.mensagem(antes, depois, motivo));
    }

    public Dtos.SimulacaoPesos simularPesos(Dtos.PesosDto p) {
        Pesos pesos = new Pesos(p.diagnostico(), p.piora(), p.dor(), p.limitacao(), p.espera(), p.risco());
        if (pesos.soma() <= 0) throw new Erros.RegraNegocio("Pelo menos um peso precisa ser maior que zero");
        var snap = fila.atual();
        var nova = fila.calcular(snap.casos(), snap.decisoes(), pesos);

        List<Dtos.Movimento> movimentos = new ArrayList<>();
        for (ResultadoPaciente r : nova.ativos()) {
            ResultadoPaciente antes = snap.get(r.getCaso().solicitacaoId());
            if (antes == null || antes.getPosicao() == null) continue;
            int delta = antes.getPosicao() - r.getPosicao();
            if (delta != 0) {
                movimentos.add(new Dtos.Movimento(r.getCaso().solicitacaoId(), r.getCaso().nome(), r.classe().name(),
                        antes.getPosicao(), r.getPosicao(), delta));
            }
        }
        var subidas = movimentos.stream().sorted(Comparator.comparingInt(Dtos.Movimento::delta).reversed()).limit(10).toList();
        var quedas = movimentos.stream().sorted(Comparator.comparingInt(Dtos.Movimento::delta)).limit(10).toList();
        var topo = nova.ativos().stream().limit(20).map(Conversor::item).toList();
        return new Dtos.SimulacaoPesos(p, subidas, quedas, topo, GuardRegraDeOuro.respeitada(nova.ativos()),
                movimentos.size());
    }
}
