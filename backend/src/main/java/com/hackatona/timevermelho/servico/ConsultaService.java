package com.hackatona.timevermelho.servico;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.hackatona.timevermelho.api.dto.Conversor;
import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.dominio.AjusteReguladorRepository;
import com.hackatona.timevermelho.dominio.Procedimento;
import com.hackatona.timevermelho.dominio.ProcedimentoRepository;
import com.hackatona.timevermelho.dominio.Solicitacao;
import com.hackatona.timevermelho.dominio.SolicitacaoRepository;
import com.hackatona.timevermelho.importacao.Normalizacao;
import com.hackatona.timevermelho.motor.ClasseSwalis;
import com.hackatona.timevermelho.motor.Evento;
import com.hackatona.timevermelho.motor.ResultadoPaciente;

/** Leitura da fila e do detalhe do paciente (sempre a partir da foto em memória). */
@Service
public class ConsultaService {

    private final FilaService fila;
    private final DecisaoService decisoes;
    private final SolicitacaoRepository solicitacoes;
    private final ProcedimentoRepository procedimentos;
    private final AjusteReguladorRepository ajustes;
    private final AlertaService alertas;
    private DesejoCirurgiaService desejos;

    @org.springframework.beans.factory.annotation.Autowired
    void setDesejos(@org.springframework.context.annotation.Lazy DesejoCirurgiaService desejos) {
        this.desejos = desejos;
    }

    public ConsultaService(FilaService fila, DecisaoService decisoes, SolicitacaoRepository solicitacoes,
                           ProcedimentoRepository procedimentos, AjusteReguladorRepository ajustes,
                           AlertaService alertas) {
        this.alertas = alertas;
        this.fila = fila;
        this.decisoes = decisoes;
        this.solicitacoes = solicitacoes;
        this.procedimentos = procedimentos;
        this.ajustes = ajustes;
    }

    public Dtos.Pagina<Dtos.ItemFila> fila(String classe, String especialidade, String busca, String status,
                                            Boolean oncologico, int pagina, int tamanho) {
        var snap = fila.atual();
        String statusFiltro = status == null || status.isBlank() ? "ATIVA" : status.toUpperCase(Locale.ROOT);
        Stream<ResultadoPaciente> s = snap.todos().stream();
        // "ATIVA" = todos que estão na fila, inclusive saídas sugeridas ainda não confirmadas
        if (statusFiltro.equals("ATIVA")) s = s.filter(r -> r.getStatus().naFila());
        else if (!statusFiltro.equals("TODOS")) s = s.filter(r -> r.getStatus().name().equals(statusFiltro));
        if (classe != null && !classe.isBlank()) s = s.filter(r -> r.classe().name().equalsIgnoreCase(classe));
        if (especialidade != null && !especialidade.isBlank()) {
            s = s.filter(r -> especialidade.equalsIgnoreCase(r.getCaso().especialidade()));
        }
        if (Boolean.TRUE.equals(oncologico)) s = s.filter(ResultadoPaciente::oncologico);
        if (busca != null && !busca.isBlank()) {
            String b = Normalizacao.semAcento(busca.trim()).toLowerCase(Locale.ROOT);
            String bCid = b.replace(".", "");
            s = s.filter(r -> {
                var c = r.getCaso();
                return contem(c.nome(), b) || contem(c.solicitacaoId(), b) || contem(c.procedimento(), b)
                        || contem(c.pacienteId(), b) || (c.cid() != null && c.cid().toLowerCase(Locale.ROOT).startsWith(bCid));
            });
        }
        List<ResultadoPaciente> filtrados = s.toList();
        int tam = Math.max(1, Math.min(tamanho, 500));
        int pag = Math.max(0, pagina);
        List<Dtos.ItemFila> itens = filtrados.stream().skip((long) pag * tam).limit(tam).map(Conversor::item).toList();

        Map<String, Long> porClasse = new LinkedHashMap<>();
        for (ClasseSwalis c : ClasseSwalis.values()) porClasse.put(c.name(), 0L);
        snap.ativos().forEach(r -> porClasse.merge(r.classe().name(), 1L, Long::sum));
        List<String> especialidades = snap.todos().stream().map(r -> r.getCaso().especialidade()).distinct().sorted().toList();
        return new Dtos.Pagina<>(itens, filtrados.size(), pag, tam, porClasse, especialidades, Conversor.snapshot(snap.info()));
    }

    private static boolean contem(String campo, String busca) {
        return campo != null && Normalizacao.semAcento(campo).toLowerCase(Locale.ROOT).contains(busca);
    }

    public Dtos.PacienteDetalhe paciente(String solicitacaoId) {
        String id = decisoes.resolverId(solicitacaoId);
        ResultadoPaciente r = fila.atual().get(id);
        if (r == null) throw new Erros.NaoEncontrado("Solicitação " + solicitacaoId + " não está na fila");
        Solicitacao s = solicitacoes.findById(id).orElseThrow();
        Procedimento p = procedimentos.findById(s.getProcedimentoId()).orElse(null);
        var c = r.getCaso();

        var dados = new Dtos.DadosClinicos(s.getSexo(), s.getIdade(), s.getMunicipio(), s.getUf(), s.getDistanciaKm(),
                s.getProcedimentoId(), s.getProcedimento(), s.getEspecialidade(), p == null ? null : p.getPorte(),
                p == null ? null : p.getRiscoCirurgico(), c.cidFormatado(), s.getCidDescricao(),
                Normalizacao.cidFormatado(s.getCidSecundario()), s.getAsa(), s.getComorbidades(), s.getDor(),
                s.getLimitacao(), s.getJustificativa(), s.getObservacoes(), s.getDataEntrada(), s.getPrioridadeManual(),
                s.getDataUltimaRevisao(), s.getFlagOncologico(), s.getFlagDiabetico(), s.getFlagPiora());

        List<Dtos.EventoDto> eventos = c.eventos().stream().sorted(Comparator.comparing(Evento::data))
                .map(e -> new Dtos.EventoDto(e.id(), e.data(), e.tipo(), e.resultado(), e.valor(), e.descricao(),
                        e.origem(), e.sintetico(), e.solicitacaoOriginal())).toList();
        List<Dtos.AjusteDto> hist = ajustes.findBySolicitacaoIdOrderByCriadoEmDescIdDesc(id).stream()
                .map(Conversor::ajuste).toList();
        List<String> duplicatas = solicitacoes.findByDuplicadaDe(id).stream().map(Solicitacao::getId).toList();

        return new Dtos.PacienteDetalhe(Conversor.item(r), dados, Conversor.classificacao(r),
                r.getComponentes().stream().map(Conversor::componente).toList(),
                r.getSinais().stream().map(Conversor::sinal).toList(), eventos, hist,
                r.getReavaliacoes().stream().map(x -> new Dtos.ReavaliacaoDto(x.tipo().name(), x.tipo().titulo(), x.mensagem())).toList(),
                duplicatas, Conversor.sinal(r.getSinalSaida()), alertas.rebaixamentoDe(id), desejos.atual(id),
                desejos.historico(id));
    }
}
