package com.hackatona.timevermelho.servico;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hackatona.timevermelho.dominio.AjusteRegulador;
import com.hackatona.timevermelho.dominio.AjusteReguladorRepository;
import com.hackatona.timevermelho.dominio.EventoClinico;
import com.hackatona.timevermelho.dominio.EventoClinicoRepository;
import com.hackatona.timevermelho.dominio.Procedimento;
import com.hackatona.timevermelho.dominio.ProcedimentoRepository;
import com.hackatona.timevermelho.dominio.Solicitacao;
import com.hackatona.timevermelho.dominio.SolicitacaoRepository;
import com.hackatona.timevermelho.importacao.Normalizacao;
import com.hackatona.timevermelho.motor.Caso;
import com.hackatona.timevermelho.motor.DecisaoRegulador;
import com.hackatona.timevermelho.motor.Evento;
import com.hackatona.timevermelho.motor.GuardRegraDeOuro;
import com.hackatona.timevermelho.motor.MotorPriorizacao;
import com.hackatona.timevermelho.motor.Pesos;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.motor.StatusFila;

/**
 * Mantém a fila calculada em memória (941 pacientes cabem com folga) e recalcula
 * inteira a cada evento novo ou decisão do regulador. Leituras nunca tocam no banco.
 */
@Service
public class FilaService {

    private static final Logger log = LoggerFactory.getLogger(FilaService.class);

    /** Foto da fila num instante. Imutável: cada recálculo gera uma nova. */
    public record Snapshot(List<Caso> casos, Map<String, DecisaoRegulador> decisoes, List<ResultadoPaciente> todos,
                           Map<String, ResultadoPaciente> porId, List<ResultadoPaciente> ativos) {

        public ResultadoPaciente get(String solicitacaoId) {
            return porId.get(solicitacaoId);
        }
    }

    private final SolicitacaoRepository solicitacoes;
    private final EventoClinicoRepository eventos;
    private final ProcedimentoRepository procedimentos;
    private final AjusteReguladorRepository ajustes;
    private final MotorPriorizacao motor;
    private final LocalDate referencia;
    private volatile Snapshot atual;

    public FilaService(SolicitacaoRepository solicitacoes, EventoClinicoRepository eventos,
                       ProcedimentoRepository procedimentos, AjusteReguladorRepository ajustes,
                       MotorPriorizacao motor, @Value("${app.data-referencia}") LocalDate referencia) {
        this.solicitacoes = solicitacoes;
        this.eventos = eventos;
        this.procedimentos = procedimentos;
        this.ajustes = ajustes;
        this.motor = motor;
        this.referencia = referencia;
    }

    public Snapshot atual() {
        if (atual == null) recalcular();
        return atual;
    }

    public LocalDate referencia() {
        return referencia;
    }

    @Transactional(readOnly = true)
    public synchronized Snapshot recalcular() {
        long inicio = System.currentTimeMillis();
        List<Caso> casos = carregarCasos();
        Map<String, DecisaoRegulador> decisoes = carregarDecisoes();
        atual = calcular(casos, decisoes, Pesos.PADRAO);
        log.info("Fila recalculada: {} ativos de {} em {} ms", atual.ativos().size(), casos.size(),
                System.currentTimeMillis() - inicio);
        if (!GuardRegraDeOuro.respeitada(atual.ativos())) {
            throw new IllegalStateException("Regra de ouro violada após recálculo — isso não deveria acontecer");
        }
        return atual;
    }

    /** Roda o motor sem salvar nada (simulações). */
    public Snapshot calcular(List<Caso> casos, Map<String, DecisaoRegulador> decisoes, Pesos pesos) {
        List<ResultadoPaciente> todos = motor.priorizar(casos, pesos, decisoes);
        Map<String, ResultadoPaciente> porId = todos.stream()
                .collect(Collectors.toMap(r -> r.getCaso().solicitacaoId(), Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<ResultadoPaciente> ativos = todos.stream().filter(r -> r.getStatus() == StatusFila.ATIVA).toList();
        return new Snapshot(casos, decisoes, todos, porId, ativos);
    }

    private List<Caso> carregarCasos() {
        Map<String, Procedimento> procs = procedimentos.findAll().stream()
                .collect(Collectors.toMap(Procedimento::getId, Function.identity()));
        Map<String, List<Evento>> eventosPorSolicitacao = new HashMap<>();
        for (EventoClinico e : eventos.findByOrfaoFalseAndDuplicadoFalse()) {
            eventosPorSolicitacao.computeIfAbsent(e.getSolicitacaoId(), k -> new ArrayList<>()).add(paraEvento(e));
        }
        List<Caso> casos = new ArrayList<>();
        for (Solicitacao s : solicitacoes.findBySituacao(Solicitacao.SituacaoCadastro.ATIVA)) {
            casos.add(paraCaso(s, procs.get(s.getProcedimentoId()), eventosPorSolicitacao.getOrDefault(s.getId(), List.of())));
        }
        return casos;
    }

    /** Aplica as decisões na ordem em que foram tomadas; a última de cada tipo vale. */
    private Map<String, DecisaoRegulador> carregarDecisoes() {
        Map<String, DecisaoRegulador> m = new HashMap<>();
        for (AjusteRegulador a : ajustes.findAllByOrderByCriadoEmAscIdAsc()) {
            DecisaoRegulador d = m.getOrDefault(a.getSolicitacaoId(), DecisaoRegulador.NENHUMA);
            d = switch (a.getTipo()) {
                case CONFIRMAR_CLASSE -> new DecisaoRegulador(null, null, d.saidaConfirmada(), d.justificativaSaida());
                case ALTERAR_CLASSE -> new DecisaoRegulador(a.getClasseNova(), a.getJustificativa(), d.saidaConfirmada(),
                        d.justificativaSaida());
                case CONFIRMAR_SAIDA -> new DecisaoRegulador(d.classe(), d.justificativaClasse(), true, a.getJustificativa());
                case RECUSAR_SAIDA -> new DecisaoRegulador(d.classe(), d.justificativaClasse(), false, a.getJustificativa());
            };
            m.put(a.getSolicitacaoId(), d);
        }
        return m;
    }

    Caso paraCaso(Solicitacao s, Procedimento p, List<Evento> evs) {
        int dias = s.getDataEntrada() == null ? 0 : (int) ChronoUnit.DAYS.between(s.getDataEntrada(), referencia);
        return Caso.builder()
                .solicitacaoId(s.getId())
                .pacienteId(s.getPacienteId())
                .nome(s.getNome())
                .sexo(s.getSexo())
                .idade(s.getIdade())
                .municipio(s.getMunicipio())
                .distanciaKm(s.getDistanciaKm())
                .procedimentoId(s.getProcedimentoId())
                .procedimento(s.getProcedimento())
                .especialidade(s.getEspecialidade())
                .riscoCirurgico(p == null ? null : p.getRiscoCirurgico())
                .cid(s.getCid())
                .cidFormatado(Normalizacao.cidFormatado(s.getCid()))
                .cidDescricao(s.getCidDescricao())
                .asa(s.getAsa())
                .comorbidades(s.getComorbidades())
                .dor(s.getDor())
                .limitacao(s.getLimitacao())
                .justificativa(s.getJustificativa())
                .observacoes(s.getObservacoes())
                .dataEntrada(s.getDataEntrada())
                .diasEmFila(dias)
                .prioridadeManual(s.getPrioridadeManual())
                .dataUltimaRevisao(s.getDataUltimaRevisao())
                .flagOncologico(s.getFlagOncologico())
                .flagDiabetico(s.getFlagDiabetico())
                .flagPiora(s.getFlagPiora())
                .eventos(List.copyOf(evs))
                .build();
    }

    static Evento paraEvento(EventoClinico e) {
        return new Evento(e.getId(), e.getSolicitacaoOriginal(), e.getData(), e.getTipo(), e.getResultado(),
                e.getValor(), e.getDescricao(), e.getOrigem(), e.isSintetico());
    }
}
