package com.hackatona.timevermelho.servico;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
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
import com.hackatona.timevermelho.motor.Referencia;
import com.hackatona.timevermelho.motor.ResultadoPaciente;

/**
 * Mantém duas filas em memória (941 pacientes cabem com folga):
 * - operacional: a que o regulador vê. Dados originais + eventos e decisões registrados; recalculada
 *   inteira a cada mudança; regra de ouro sobre a situação clínica atual.
 * - oficial: a fila de referência da entrega (fila_priorizada.csv). Só o dataset, sem eventos novos nem
 *   decisões, com a interpretação do verificador oficial; reproduzível.
 * Leituras nunca tocam no banco.
 */
@Service
public class FilaService {

    private static final Logger log = LoggerFactory.getLogger(FilaService.class);

    public static final String OPERACIONAL = "operacional";
    public static final String OFICIAL = "oficial";
    public static final String SIMULACAO = "simulacao";

    /** Identidade de um snapshot: o que é, qual versão e quando foi calculado. */
    public record Info(String tipo, String versao, Instant calculadoEm, int eventosRegistrados, int decisoesRegulador) {

        /** Valor do cabeçalho HTTP X-FilaViva-Snapshot. */
        public String cabecalho() {
            return tipo + "; versao=" + versao + "; calculado=" + calculadoEm;
        }
    }

    /** Foto da fila num instante. Imutável: cada recálculo gera uma nova. */
    public record Snapshot(List<Caso> casos, Map<String, DecisaoRegulador> decisoes, List<ResultadoPaciente> todos,
                           Map<String, ResultadoPaciente> porId, List<ResultadoPaciente> ativos, Info info) {

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
    private final Path pastaDataset;
    private final AtomicLong versaoOperacional = new AtomicLong();
    private volatile Snapshot atual;
    private volatile Snapshot oficial;

    public FilaService(SolicitacaoRepository solicitacoes, EventoClinicoRepository eventos,
                       ProcedimentoRepository procedimentos, AjusteReguladorRepository ajustes,
                       MotorPriorizacao motor, @Value("${app.data-referencia}") LocalDate referencia,
                       @Value("${app.dataset.dir}") String pastaDataset) {
        this.solicitacoes = solicitacoes;
        this.eventos = eventos;
        this.procedimentos = procedimentos;
        this.ajustes = ajustes;
        this.motor = motor;
        this.referencia = referencia;
        this.pastaDataset = Path.of(pastaDataset);
    }

    /** Fila operacional: a que o regulador vê e a API devolve. */
    public Snapshot atual() {
        if (atual == null) recalcular();
        return atual;
    }

    /** Fila de referência da entrega: só o dataset, independente de eventos e decisões registrados. */
    public Snapshot oficial() {
        if (oficial == null) recalcular();
        if (oficial == null) throw new Erros.Conflito("O sistema ainda está importando o dataset. Tente de novo em alguns segundos.");
        return oficial;
    }

    public LocalDate referencia() {
        return referencia;
    }

    @Transactional(readOnly = true)
    public synchronized Snapshot recalcular() {
        long inicio = System.currentTimeMillis();
        List<Caso> casos = carregarCasos();
        Map<String, DecisaoRegulador> decisoes = carregarDecisoes();
        int eventosNovos = (int) casos.stream().flatMap(c -> c.eventos().stream()).filter(Evento::sintetico).count();
        Info info = new Info(OPERACIONAL, "op-" + versaoOperacional.incrementAndGet(), Instant.now(), eventosNovos,
                decisoes.size());
        Snapshot novo = calcular(casos, decisoes, Pesos.PADRAO, Referencia.ATUAL, info);
        exigirRegraDeOuro(novo);
        atual = novo;
        // a referência oficial não muda enquanto o sistema roda: calcula uma vez, e só com o dataset já importado
        // (um pedido durante a subida não pode fixar uma referência vazia nem gerar um CSV só com cabeçalho)
        if (oficial == null && !casos.isEmpty()) oficial = calcularOficial(casos);
        log.info("Fila operacional {} recalculada: {} na fila de {} em {} ms ({} eventos novos, {} decisões)",
                info.versao(), atual.ativos().size(), casos.size(), System.currentTimeMillis() - inicio,
                eventosNovos, decisoes.size());
        return atual;
    }

    /** Roda o motor sem salvar nada (simulações). Nunca substitui a fila operacional nem a oficial. */
    public Snapshot calcular(List<Caso> casos, Map<String, DecisaoRegulador> decisoes, Pesos pesos) {
        return calcular(casos, decisoes, pesos, Referencia.ATUAL,
                new Info(SIMULACAO, "sim", Instant.now(), 0, decisoes.size()));
    }

    private Snapshot calcular(List<Caso> casos, Map<String, DecisaoRegulador> decisoes, Pesos pesos,
                              Referencia ref, Info info) {
        List<ResultadoPaciente> todos = motor.priorizar(casos, pesos, decisoes, ref);
        Map<String, ResultadoPaciente> porId = todos.stream()
                .collect(Collectors.toMap(r -> r.getCaso().solicitacaoId(), Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<ResultadoPaciente> ativos = todos.stream().filter(r -> r.getStatus().naFila()).toList();
        return new Snapshot(casos, decisoes, todos, porId, ativos, info);
    }

    /** Mesmos casos, só com os eventos do dataset e sem decisões do regulador. */
    private Snapshot calcularOficial(List<Caso> casos) {
        List<Caso> referenciaDataset = casos.stream()
                .map(c -> c.toBuilder().eventos(c.eventos().stream().filter(e -> !e.sintetico()).toList()).build())
                .toList();
        Info info = new Info(OFICIAL, "ref-" + impressaoDigitalDaReferencia(), Instant.now(), 0, 0);
        Snapshot s = calcular(referenciaDataset, Map.of(), Pesos.PADRAO, Referencia.OFICIAL, info);
        exigirRegraDeOuro(s);
        log.info("Fila de referência {} calculada: {} na fila", info.versao(), s.ativos().size());
        return s;
    }

    private static void exigirRegraDeOuro(Snapshot s) {
        if (!GuardRegraDeOuro.respeitada(s.ativos())) {
            throw new IllegalStateException("Regra de ouro violada na fila " + s.info().tipo() + " — isso não deveria acontecer");
        }
    }

    /** Versão da referência: hash dos CSVs do dataset e do regras.json. Mesmos dados e regras, mesma versão. */
    private String impressaoDigitalDaReferencia() {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (String arquivo : List.of("fila_cirurgica.csv", "eventos_clinicos.csv", "procedimentos.csv")) {
                sha.update(Files.readAllBytes(pastaDataset.resolve(arquivo)));
            }
            try (InputStream regras = getClass().getResourceAsStream("/regras.json")) {
                if (regras != null) sha.update(regras.readAllBytes());
            }
            return HexFormat.of().formatHex(sha.digest()).substring(0, 12);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Não consegui calcular a versão da referência", e);
        }
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
                .cidSecundario(Normalizacao.cid(s.getCidSecundario()))
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
