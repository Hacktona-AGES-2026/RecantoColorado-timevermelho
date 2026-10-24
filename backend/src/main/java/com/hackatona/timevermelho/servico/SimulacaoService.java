package com.hackatona.timevermelho.servico;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

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
                "Simulação (nada foi salvo). " + ImpactoCalculadora.mensagem(antes, depois, motivo),
                observacoes(req, original, antes, depois, novos));
    }

    /** Eventos que o motor registra, mas que não mudam a urgência (o FilaVIVA não baixa classe sozinho). */
    private static final Set<String> NAO_MUDAM_URGENCIA = Set.of("biopsia:negativo", "biopsia:inconclusivo",
            "exame_imagem:estavel", "consulta_reavaliacao:estavel", "consulta_reavaliacao:melhora",
            "contato_telefonico:atendeu", "contato_telefonico:nao_atendeu", "contato_telefonico:relata_melhora");

    /** Explica o resultado, principalmente quando nada muda, para a simulação não parecer quebrada. */
    static List<String> observacoes(Dtos.SimulacaoRequest req, Caso original, ResultadoPaciente antes,
                                    ResultadoPaciente depois, List<Sinal> novos) {
        List<String> obs = new ArrayList<>();
        if (req.dor() != null && req.dor().equals(original.dor())) {
            obs.add("A dor escolhida (" + req.dor() + ") é igual à de hoje.");
        }
        if (req.limitacao() != null && req.limitacao().equalsIgnoreCase(String.valueOf(original.limitacao()))) {
            obs.add("A limitação escolhida (" + req.limitacao() + ") é igual à de hoje.");
        }
        if (req.eventos() != null) {
            for (var e : req.eventos()) {
                String chave = e.tipo() + ":" + e.resultado();
                if (NAO_MUDAM_URGENCIA.contains(chave) || "falta".equals(e.tipo())) {
                    obs.add("Evento \"" + e.tipo().replace('_', ' ') + (e.resultado() == null ? "" : ": "
                            + e.resultado().replace('_', ' ')) + "\" não muda a urgência. Melhora ou exame normal nunca "
                            + "baixa a classe sozinho: na fila real, um rebaixamento só vale depois que o regulador confirma.");
                }
                if (e.descricao() != null && !e.descricao().isBlank()) {
                    obs.add("O texto do evento hipotético só gera alerta para revisão, nunca muda a classe (mesma regra "
                            + "dos eventos reais). Para testar um texto, use \"Texto adicional do médico\".");
                }
            }
        }
        boolean igual = antes.classe() == depois.classe() && java.util.Objects.equals(antes.getPosicao(), depois.getPosicao())
                && antes.getScore() == depois.getScore();
        if (igual && !novos.isEmpty()) {
            boolean pioraNoMaximo = depois.getComponentes().stream()
                    .anyMatch(c -> "piora".equals(c.chave()) && c.pontos() >= c.maximo());
            obs.add("Sinal novo detectado (" + novos.getFirst().descricao() + "), mas o paciente já está na classe "
                    + depois.classe() + (pioraNoMaximo ? " e o componente de piora já está no máximo" : "")
                    + ": a posição não muda.");
        } else if (igual && obs.isEmpty()) {
            obs.add("A situação hipotética não traz nada que o paciente já não tenha (por exemplo, uma biópsia positiva "
                    + "já registrada): classe, score e posição continuam os mesmos.");
        }
        if (antes.classe().maisUrgenteQue(depois.classe())) {
            obs.add(antes.oncologico()
                    ? "Paciente oncológico: na fila real a classe nunca seria reduzida."
                    : "Na fila real isso seria só um rebaixamento sugerido: a classe " + antes.classe()
                    + " continua valendo até o regulador confirmar.");
        }
        return obs;
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
