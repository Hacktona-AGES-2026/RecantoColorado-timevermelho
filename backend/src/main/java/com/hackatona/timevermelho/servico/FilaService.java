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
import java.util.Set;
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
import com.hackatona.timevermelho.dominio.TipoAjuste;
import com.hackatona.timevermelho.importacao.Normalizacao;
import com.hackatona.timevermelho.motor.Caso;
import com.hackatona.timevermelho.motor.DecisaoRegulador;
import com.hackatona.timevermelho.motor.Evento;
import com.hackatona.timevermelho.motor.GuardRegraDeOuro;
import com.hackatona.timevermelho.motor.MotorPriorizacao;
import com.hackatona.timevermelho.motor.Pesos;
import com.hackatona.timevermelho.motor.Referencia;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.motor.Sinal;

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
    /** Opcional: marca na fila se o paciente quer ser operado (não entra no cálculo). */
    private com.hackatona.timevermelho.dominio.DesejoCirurgiaRepository desejos;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setDesejos(com.hackatona.timevermelho.dominio.DesejoCirurgiaRepository desejos) {
        this.desejos = desejos;
    }

    /** Opcional: marca na fila o parecer de aptidão cirúrgica e a decisão (não entra no cálculo). */
    private com.hackatona.timevermelho.dominio.RegistroAptidaoRepository registrosAptidao;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setRegistrosAptidao(com.hackatona.timevermelho.dominio.RegistroAptidaoRepository registrosAptidao) {
        this.registrosAptidao = registrosAptidao;
    }

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

    /** Data de referência do dataset (23/10/2026): base dos dias de fila e da fila oficial. */
    public LocalDate referencia() {
        return referencia;
    }

    /**
     * Data de hoje para eventos novos (registrados ou simulados): o relógio do servidor, nunca antes da data de
     * referência do dataset. A referência continua sendo a base dos dias de fila e da fila oficial.
     */
    public LocalDate hoje() {
        LocalDate h = LocalDate.now();
        return h.isBefore(referencia) ? referencia : h;
    }

    @Transactional
    public synchronized Snapshot recalcular() {
        return recalcular(Set.of());
    }

    /**
     * Recalcula a fila operacional.
     * Rede de segurança dos rebaixamentos: se o recálculo baixaria a classe de alguém (por qualquer caminho: evento,
     * correção de dado, regra), a queda não é aplicada. Vira um rebaixamento sugerido, que mantém a classe vigente
     * até o regulador decidir; para um oncológico, vira rebaixamento bloqueado. Só passam as quedas confirmadas pelo
     * regulador nesta ação.
     *
     * @param autorizados pacientes cujo rebaixamento o regulador acabou de confirmar
     */
    @Transactional
    public synchronized Snapshot recalcular(Set<String> autorizados) {
        long inicio = System.currentTimeMillis();
        List<Caso> casos = carregarCasos();
        int eventosNovos = (int) casos.stream().flatMap(c -> c.eventos().stream()).filter(Evento::sintetico).count();
        String versao = "op-" + versaoOperacional.incrementAndGet();
        Snapshot novo = calcularOperacional(casos, versao, eventosNovos);
        if (atual != null) {
            List<AjusteRegulador> travas = travarQuedasDeClasse(atual, novo, autorizados);
            if (!travas.isEmpty()) {
                ajustes.saveAll(travas);
                log.info("{} queda(s) de classe sem confirmação do regulador viraram sugestão ou bloqueio", travas.size());
                novo = calcularOperacional(casos, versao, eventosNovos);
            }
        }
        Map<String, DecisaoRegulador> decisoes = novo.decisoes();
        exigirRegraDeOuro(novo);
        atual = novo;
        // a referência oficial não muda enquanto o sistema roda: calcula uma vez, e só com o dataset já importado
        // (um pedido durante a subida não pode fixar uma referência vazia nem gerar um CSV só com cabeçalho)
        if (oficial == null && !casos.isEmpty()) oficial = calcularOficial(casos);
        log.info("Fila operacional {} recalculada: {} na fila de {} em {} ms ({} eventos novos, {} decisões)",
                versao, atual.ativos().size(), casos.size(), System.currentTimeMillis() - inicio,
                eventosNovos, decisoes.size());
        return atual;
    }

    /**
     * Volta à base (reinício da demo): a fila recalculada vira a nova referência, sem comparar com a anterior.
     * Não é um rebaixamento clínico: eventos e decisões registrados foram apagados de propósito.
     */
    @Transactional
    public synchronized Snapshot recalcularComoBase() {
        atual = null;
        return recalcular(Set.of());
    }

    private Snapshot calcularOperacional(List<Caso> casos, String versao, int eventosNovos) {
        Map<String, DecisaoRegulador> decisoes = carregarDecisoes();
        Info info = new Info(OPERACIONAL, versao, Instant.now(), eventosNovos, decisoes.size());
        Snapshot s = calcular(casos, decisoes, Pesos.PADRAO, Referencia.ATUAL, info);
        Map<String, String> estados = estadosDeRebaixamento();
        Map<String, String> desejo = new HashMap<>();
        if (desejos != null) {
            for (var d : desejos.findAllByOrderByCriadoEmAscIdAsc()) desejo.put(d.getSolicitacaoId(), d.getResposta().name());
        }
        Map<String, com.hackatona.timevermelho.dominio.RegistroAptidao[]> aptidao = registrosAptidao == null ? Map.of()
                : AptidaoService.ultimos(registrosAptidao.findAllByOrderByCriadoEmAscIdAsc());
        for (ResultadoPaciente r : s.todos()) {
            String id = r.getCaso().solicitacaoId();
            r.setEstadoRebaixamento(estados.get(id));
            r.setDesejoCirurgia(desejo.get(id));
            var par = aptidao.get(id);
            r.setAptidao(AvaliadorAptidao.avaliar(r, par == null ? null : par[0]).veredito().name());
            r.setDecisaoAptidao(par == null || par[1] == null ? null : par[1].getDecisao().name());
        }
        return s;
    }

    /** Para cada paciente, a situação do último registro de rebaixamento. */
    private Map<String, String> estadosDeRebaixamento() {
        Map<String, String> m = new HashMap<>();
        for (AjusteRegulador a : ajustes.findAllByOrderByCriadoEmAscIdAsc()) {
            String estado = switch (a.getTipo()) {
                case SUGERIR_REBAIXAMENTO -> "PENDENTE";
                case CONFIRMAR_REBAIXAMENTO -> "CONFIRMADO";
                case REJEITAR_REBAIXAMENTO -> "REJEITADO";
                case REBAIXAMENTO_BLOQUEADO -> "BLOQUEADO";
                default -> null;
            };
            if (estado != null) m.put(a.getSolicitacaoId(), estado);
        }
        return m;
    }

    /** Quedas de classe que ninguém confirmou: viram sugestão (ou bloqueio, se oncológico) com a classe vigente mantida. */
    private static List<AjusteRegulador> travarQuedasDeClasse(Snapshot antes, Snapshot depois, Set<String> autorizados) {
        List<AjusteRegulador> travas = new ArrayList<>();
        for (ResultadoPaciente r : depois.todos()) {
            String id = r.getCaso().solicitacaoId();
            ResultadoPaciente a = antes.get(id);
            if (a == null || autorizados.contains(id) || !a.classe().maisUrgenteQue(r.classe())) continue;
            boolean oncologico = a.oncologico() || r.oncologico();
            List<String> perdidos = ImpactoCalculadora.sinaisNovos(r, a).stream().map(Sinal::descricao).toList();
            String fatores = "O recálculo automático baixaria a classe de " + a.classe() + " para " + r.classe() + ". "
                    + (perdidos.isEmpty() ? "Nenhum sinal novo; mudou a regra aplicada."
                    : "Sinais que deixaram de valer: " + String.join("; ", perdidos) + ".");
            AjusteRegulador t = new AjusteRegulador();
            t.setSolicitacaoId(id);
            t.setTipo(oncologico ? TipoAjuste.REBAIXAMENTO_BLOQUEADO : TipoAjuste.SUGERIR_REBAIXAMENTO);
            t.setClasseAnterior(a.classe());
            t.setClasseNova(r.classe());
            t.setJustificativa(oncologico
                    ? "Paciente oncológico: rebaixamento bloqueado, classe " + a.classe() + " mantida. " + fatores
                    : fatores);
            t.setEvidencias(evidencias(r));
            t.setAutor("FilaVIVA (recálculo automático)");
            t.setAutomatico(true);
            t.setSeguirCalculada(true);
            t.setCriadoEm(java.time.LocalDateTime.now());
            travas.add(t);
        }
        return travas;
    }

    /** Regra e sinais que sustentam a classe calculada (a proposta de um rebaixamento). */
    static String evidencias(ResultadoPaciente r) {
        var cl = r.getClassificacao();
        String sinais = cl.determinantes().stream().map(Sinal::descricao).collect(Collectors.joining("; "));
        return "Classe calculada " + cl.classeCalculada() + ": " + cl.regra() + (sinais.isEmpty() ? "" : " (" + sinais + ")");
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
                // concorda com a classe vigente: não mexe nela (antes voltava para a calculada, o que podia rebaixar)
                case CONFIRMAR_CLASSE -> d;
                case ALTERAR_CLASSE -> d.comClasse(a.getClasseNova(), a.getJustificativa());
                case CONFIRMAR_SAIDA -> d.comSaida(true, a.getJustificativa());
                case RECUSAR_SAIDA -> d.comSaida(false, a.getJustificativa());
                case SUGERIR_REBAIXAMENTO -> d.comPiso(a.getClasseAnterior(), "classe " + a.getClasseAnterior()
                        + " mantida: rebaixamento para " + a.getClasseNova() + " sugerido, aguarda confirmação do regulador");
                case CONFIRMAR_REBAIXAMENTO -> d.comClasse(a.isSeguirCalculada() ? null : a.getClasseNova(),
                        a.getJustificativa()).comPiso(null, null);
                case REJEITAR_REBAIXAMENTO -> d.comPiso(a.getClasseAnterior(), "rebaixamento rejeitado pelo regulador, classe "
                        + a.getClasseAnterior() + " mantida: " + a.getJustificativa());
                case FIXAR_POSICAO -> d.comPosicao(a.getPosicaoFixada(), a.getAutor() + ": " + a.getJustificativa());
                case LIBERAR_POSICAO -> d.comPosicao(null, null);
                case REGISTRAR_EVENTO -> d; // só auditoria: o evento em si já entra no recálculo
                case REBAIXAMENTO_BLOQUEADO -> d.comPiso(a.getClasseAnterior(), "paciente oncológico: rebaixamento bloqueado, classe "
                        + a.getClasseAnterior() + " mantida");
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
                .hba1cCadastro(s.getHba1cUltima())
                .dataHba1cCadastro(s.getDataHba1cUltima())
                .avaliacaoPreAnestesica(s.getAvaliacaoPreAnestesica())
                .examesPreOpValidos(s.getExamesPreOpValidos())
                .porte(p == null ? null : p.getPorte())
                .duracaoMin(p == null ? null : p.getDuracaoMediaMin())
                .necessitaUti(p == null ? null : p.getNecessitaUti())
                .build();
    }

    static Evento paraEvento(EventoClinico e) {
        return new Evento(e.getId(), e.getSolicitacaoOriginal(), e.getData(), e.getTipo(), e.getResultado(),
                e.getValor(), e.getDescricao(), e.getOrigem(), e.isSintetico());
    }
}
