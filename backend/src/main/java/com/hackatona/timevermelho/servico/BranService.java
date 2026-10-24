package com.hackatona.timevermelho.servico;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.dominio.Alerta;
import com.hackatona.timevermelho.dominio.AlertaRepository;
import com.hackatona.timevermelho.dominio.ComunicacaoBran;
import com.hackatona.timevermelho.dominio.ComunicacaoBranRepository;
import com.hackatona.timevermelho.dominio.Procedimento;
import com.hackatona.timevermelho.dominio.ProcedimentoRepository;
import com.hackatona.timevermelho.dominio.Solicitacao;
import com.hackatona.timevermelho.dominio.SolicitacaoRepository;
import com.hackatona.timevermelho.importacao.Normalizacao;
import com.hackatona.timevermelho.medico.Medico;
import com.hackatona.timevermelho.medico.MedicoRepository;
import com.hackatona.timevermelho.motor.Caso;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.motor.TipoSinal;

import tools.jackson.databind.json.JsonMapper;

/**
 * BRAN: o que o médico comunica ao paciente antes de decidir a cirurgia. Benefícios, Riscos, Alternativas (sem
 * cirurgia ou outros procedimentos) e Nada (o que acontece se não operar). O sistema monta um rascunho com o texto geral
 * do procedimento e os fatores de risco do próprio paciente; o médico revisa e registra a conversa. Não muda a fila.
 */
@Service
public class BranService {

    static final String AVISO = "Rascunho montado pelo FilaVIVA a partir de informações gerais do procedimento e dos dados "
            + "do cadastro. Revise e adapte ao paciente antes de comunicar: não é recomendação clínica individual.";

    private static final Pattern ICC = Pattern.compile("\\bicc\\b|insuficiencia cardiaca|cardiopatia");
    private static final Pattern RIM = Pattern.compile("\\bdrc\\b|doenca renal|insuficiencia renal");

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TextoProcedimento(String beneficios, String riscos, String alternativas, String nada) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Catalogo(Map<String, TextoProcedimento> procedimentos) {}

    private final ComunicacaoBranRepository brans;
    private final SolicitacaoRepository solicitacoes;
    private final ProcedimentoRepository procedimentos;
    private final MedicoRepository medicos;
    private final DecisaoService decisoes;
    private final FilaService fila;
    private final AlertaRepository alertas;
    private final Map<String, TextoProcedimento> catalogo;

    public BranService(ComunicacaoBranRepository brans, SolicitacaoRepository solicitacoes,
                       ProcedimentoRepository procedimentos, MedicoRepository medicos, DecisaoService decisoes,
                       FilaService fila, AlertaRepository alertas) {
        this.brans = brans;
        this.solicitacoes = solicitacoes;
        this.procedimentos = procedimentos;
        this.medicos = medicos;
        this.decisoes = decisoes;
        this.fila = fila;
        this.alertas = alertas;
        this.catalogo = carregarCatalogo();
    }

    static Map<String, TextoProcedimento> carregarCatalogo() {
        try (InputStream in = BranService.class.getResourceAsStream("/bran-procedimentos.json")) {
            if (in == null) throw new IllegalStateException("bran-procedimentos.json não encontrado no classpath");
            return JsonMapper.builder().build().readValue(in, Catalogo.class).procedimentos();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ResultadoPaciente paciente(String solicitacaoId) {
        String id = decisoes.resolverId(solicitacaoId);
        ResultadoPaciente r = fila.atual().get(id);
        if (r == null) throw new Erros.NaoEncontrado("Solicitação " + solicitacaoId + " não está na fila");
        return r;
    }

    /** Rascunho para o médico revisar. */
    public Dtos.BranRascunho rascunho(String solicitacaoId) {
        ResultadoPaciente r = paciente(solicitacaoId);
        Caso c = r.getCaso();
        Solicitacao s = solicitacoes.findById(c.solicitacaoId()).orElseThrow();
        Procedimento p = procedimentos.findById(c.procedimentoId()).orElse(null);
        TextoProcedimento t = catalogo.getOrDefault(c.procedimentoId(), new TextoProcedimento(
                "Descreva o que a cirurgia deve melhorar para este paciente.",
                "Descreva os riscos do procedimento e os do próprio paciente.",
                "Descreva as alternativas sem cirurgia ou com outros procedimentos.",
                "Descreva o que acontece se o paciente não operar."));

        StringBuilder beneficios = new StringBuilder(t.beneficios());
        if (c.dor() != null && c.dor() >= 7) beneficios.append(" Hoje a dor está em ").append(c.dor()).append(" de 10.");
        if ("grave".equals(c.limitacao()) || "moderada".equals(c.limitacao())) {
            beneficios.append(" A limitação para as atividades hoje é ").append(c.limitacao()).append(".");
        }

        List<String> fatores = fatoresDeRisco(r, s);
        StringBuilder riscos = new StringBuilder();
        if (p != null) {
            riscos.append("Risco do procedimento: ").append(p.getRiscoCirurgico()).append(" numa escala de 1 a 5 (porte ")
                    .append(p.getPorte()).append(", cerca de ").append(p.getDuracaoMediaMin()).append(" minutos)");
            if ("sim".equals(p.getNecessitaUti())) riscos.append("; costuma precisar de UTI");
            else if ("eventual".equals(p.getNecessitaUti())) riscos.append("; pode precisar de UTI");
            riscos.append(". ");
        }
        riscos.append(t.riscos());
        if (!fatores.isEmpty()) {
            riscos.append(" No seu caso, aumentam o risco: ").append(String.join("; ", fatores)).append(".");
        }

        StringBuilder nada = new StringBuilder(t.nada());
        nada.append(" Hoje você está na classe ").append(r.classe()).append(" da fila (prazo sugerido de ")
                .append(r.classe().prazoDias()).append(" dias) e espera há ").append(c.diasEmFila()).append(" dias.");
        if (r.oncologico()) nada.append(" Por ser câncer, a sua cirurgia tem prioridade na fila.");

        return new Dtos.BranRascunho(c.solicitacaoId(), c.procedimento(), beneficios.toString(), riscos.toString(),
                t.alternativas(), nada.toString(), fatores, AVISO);
    }

    /** Fatores do próprio paciente que aumentam o risco (do cadastro e dos eventos). */
    public static List<String> fatoresDeRisco(ResultadoPaciente r, Solicitacao s) {
        Caso c = r.getCaso();
        List<String> f = new ArrayList<>();
        String com = c.comorbidades() == null ? "" : Normalizacao.semAcento(c.comorbidades()).toLowerCase(Locale.ROOT);
        if (c.asa() != null && c.asa() >= 3) f.add("estado físico ASA " + c.asa() + " (doença sistêmica importante)");
        if (c.idade() != null && c.idade() >= 75) f.add("idade de " + c.idade() + " anos");
        boolean diabetes = c.diabetesNoCadastro();
        boolean hba1cAlta = r.temSinal(TipoSinal.HBA1C_ALTA) || (s.getHba1cUltima() != null && s.getHba1cUltima() > 8.5);
        if (diabetes && hba1cAlta) f.add("diabetes descontrolado (HbA1c acima de 8,5%): controlar antes da cirurgia reduz complicações");
        else if (diabetes) f.add("diabetes");
        if (ICC.matcher(com).find()) f.add("doença do coração");
        if (com.contains("fibrilacao") || com.contains("anticoagul")) f.add("uso de anticoagulante ou arritmia (risco de sangramento e trombose)");
        if (com.contains("dpoc") || com.contains("enfisema")) f.add("doença pulmonar (DPOC)");
        if (com.contains("obesidade")) f.add("obesidade");
        if (com.contains("tabagismo")) f.add("tabagismo: parar de fumar antes da cirurgia ajuda na cicatrização");
        if (RIM.matcher(com).find()) f.add("doença dos rins");
        return f;
    }

    @Transactional
    public Dtos.BranDto registrar(String solicitacaoId, Dtos.BranRequest req) {
        ResultadoPaciente r = paciente(solicitacaoId);
        String autor = req.autor() == null || req.autor().isBlank() ? "regulador" : req.autor().trim();
        if (req.medicoId() != null) {
            Medico m = medicos.findById(req.medicoId())
                    .orElseThrow(() -> new Erros.NaoEncontrado("Médico " + req.medicoId() + " não encontrado"));
            autor = m.getNome() + " (" + m.getCrm() + ")";
        }
        ComunicacaoBran b = new ComunicacaoBran();
        b.setSolicitacaoId(r.getCaso().solicitacaoId());
        b.setBeneficios(req.beneficios().trim());
        b.setRiscos(req.riscos().trim());
        b.setAlternativas(req.alternativas().trim());
        b.setNada(req.nada().trim());
        b.setForma(req.forma());
        b.setDuvidas(req.duvidas() == null || req.duvidas().isBlank() ? null : req.duvidas().trim());
        b.setCompreendeu(req.compreendeu());
        b.setAutor(autor);
        b.setMedicoId(req.medicoId());
        b.setCriadoEm(LocalDateTime.now());
        brans.save(b);

        Alerta a = new Alerta();
        a.setSolicitacaoId(b.getSolicitacaoId());
        a.setNome(r.getCaso().nome());
        a.setTipo(Alerta.Tipo.AJUSTE_REGULADOR);
        a.setMensagem("BRAN comunicado por " + autor + " (" + req.forma().name().toLowerCase(Locale.ROOT) + ")"
                + (req.compreendeu() ? ", paciente recebeu as informações e teve as dúvidas respondidas"
                : ", entendimento ainda não confirmado") + ". A posição na fila não muda.");
        a.setClasseAntes(r.classe());
        a.setClasseDepois(r.classe());
        a.setPosicaoAntes(r.getPosicao());
        a.setPosicaoDepois(r.getPosicao());
        a.setCriadoEm(LocalDateTime.now());
        alertas.save(a);
        return dto(b);
    }

    public List<Dtos.BranDto> historico(String solicitacaoId) {
        return brans.findBySolicitacaoIdOrderByCriadoEmDescIdDesc(decisoes.resolverId(solicitacaoId)).stream()
                .map(BranService::dto).toList();
    }

    /** Comunicação mais recente de cada paciente. */
    public Map<String, ComunicacaoBran> atuais() {
        Map<String, ComunicacaoBran> m = new HashMap<>();
        for (ComunicacaoBran b : brans.findAllByOrderByCriadoEmAscIdAsc()) m.put(b.getSolicitacaoId(), b);
        return m;
    }

    static Dtos.BranDto dto(ComunicacaoBran b) {
        return new Dtos.BranDto(b.getId(), b.getBeneficios(), b.getRiscos(), b.getAlternativas(), b.getNada(),
                b.getForma().name(), b.getDuvidas(), b.isCompreendeu(), b.getAutor(), b.getMedicoId(), b.getCriadoEm());
    }
}
