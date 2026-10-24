package com.hackatona.timevermelho.servico;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hackatona.timevermelho.dominio.Alerta;
import com.hackatona.timevermelho.dominio.AlertaRepository;
import com.hackatona.timevermelho.dominio.RegistroAptidao;
import com.hackatona.timevermelho.dominio.RegistroAptidaoRepository;
import com.hackatona.timevermelho.medico.Medico;
import com.hackatona.timevermelho.medico.MedicoRepository;
import com.hackatona.timevermelho.motor.ResultadoPaciente;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Aptidão cirúrgica: o paciente pode ser operado agora? Junta o parecer calculado (AvaliadorAptidao) com o que o médico
 * informa (fatores que o sistema não tem, como colesterol e pressão descontrolada) e com a decisão humana (liberar,
 * aguardar otimização, contraindicar). Não muda a posição na fila; marca quem não deve ser agendado agora.
 */
@Service
public class AptidaoService {

    static final String AVISO = "Parecer de apoio calculado com regras simples a partir do cadastro, dos exames e do que o "
            + "médico informou. Não substitui a avaliação pré-anestésica nem a decisão da equipe. A estimativa de recursos "
            + "serve para planejamento e precisa ser calibrada com os dados do hospital.";

    public record FatoresInformados(boolean colesterolAlto, boolean pressaoDescontrolada, Double imc, boolean tabagismoAtual,
                                    boolean adesaoRuim, String outros, String autor, LocalDateTime criadoEm) {}

    public record DecisaoAptidao(String decisao, String rotulo, String plano, LocalDate reavaliarEm, String justificativa,
                                 String autor, Long medicoId, LocalDateTime criadoEm) {}

    public record Aptidao(String solicitacaoId, String veredito, String vereditoRotulo, String risco, int pontos,
                          List<AvaliadorAptidao.Fator> fatores, List<String> acoes, List<String> preRequisitos,
                          AvaliadorAptidao.Recursos recursos, String observacaoPrioridade,
                          FatoresInformados fatoresInformados, DecisaoAptidao decisao, List<DecisaoAptidao> historicoDecisoes,
                          String aviso) {}

    public record FatoresRequest(boolean colesterolAlto, boolean pressaoDescontrolada,
                                 @DecimalMin(value = "10.0", message = "IMC entre 10 e 90")
                                 @DecimalMax(value = "90.0", message = "IMC entre 10 e 90") Double imc,
                                 boolean tabagismoAtual, boolean adesaoRuim, @Size(max = 1000) String outros,
                                 @Size(max = 120) String autor, Long medicoId) {}

    public record DecisaoRequest(@NotNull(message = "informe a decisão") RegistroAptidao.Decisao decisao,
                                 @NotBlank @Size(min = 10, max = 2000, message = "a justificativa precisa ter pelo menos 10 caracteres")
                                 String justificativa,
                                 @Size(max = 2000) String plano, LocalDate reavaliarEm,
                                 @Size(max = 120) String autor, Long medicoId) {}

    public static String rotulo(AvaliadorAptidao.Veredito v) {
        return switch (v) {
            case APTO -> "Apto";
            case APTO_COM_RESSALVAS -> "Apto com ressalvas (risco alto)";
            case OTIMIZAR_ANTES -> "Otimizar antes da cirurgia";
        };
    }

    public static String rotulo(RegistroAptidao.Decisao d) {
        return switch (d) {
            case LIBERADO -> "Liberado para agendar";
            case AGUARDAR_OTIMIZACAO -> "Aguardar otimização";
            case CONTRAINDICADO -> "Cirurgia contraindicada no momento";
        };
    }

    private final RegistroAptidaoRepository registros;
    private final MedicoRepository medicos;
    private final DecisaoService decisoes;
    private final FilaService fila;
    private final AlertaRepository alertas;

    public AptidaoService(RegistroAptidaoRepository registros, MedicoRepository medicos, DecisaoService decisoes,
                          FilaService fila, AlertaRepository alertas) {
        this.registros = registros;
        this.medicos = medicos;
        this.decisoes = decisoes;
        this.fila = fila;
        this.alertas = alertas;
    }

    private ResultadoPaciente paciente(String solicitacaoId) {
        String id = decisoes.resolverId(solicitacaoId);
        ResultadoPaciente r = fila.atual().get(id);
        if (r == null) throw new Erros.NaoEncontrado("Solicitação " + solicitacaoId + " não está na fila");
        return r;
    }

    public Aptidao avaliar(String solicitacaoId) {
        ResultadoPaciente r = paciente(solicitacaoId);
        List<RegistroAptidao> hist = registros.findBySolicitacaoIdOrderByCriadoEmDescIdDesc(r.getCaso().solicitacaoId());
        RegistroAptidao fat = hist.stream().filter(x -> x.getTipo() == RegistroAptidao.Tipo.FATORES).findFirst().orElse(null);
        List<DecisaoAptidao> decs = hist.stream().filter(x -> x.getTipo() == RegistroAptidao.Tipo.DECISAO)
                .map(AptidaoService::decisao).toList();
        var res = AvaliadorAptidao.avaliar(r, fat);
        return new Aptidao(r.getCaso().solicitacaoId(), res.veredito().name(), rotulo(res.veredito()), res.risco().name(),
                res.pontos(), res.fatores(), res.acoes(), res.preRequisitos(), res.recursos(), res.observacaoPrioridade(),
                fat == null ? null : fatores(fat), decs.isEmpty() ? null : decs.getFirst(), decs, AVISO);
    }

    @Transactional
    public Aptidao informarFatores(String solicitacaoId, FatoresRequest req) {
        ResultadoPaciente r = paciente(solicitacaoId);
        RegistroAptidao a = novo(r, RegistroAptidao.Tipo.FATORES, req.autor(), req.medicoId());
        a.setColesterolAlto(req.colesterolAlto());
        a.setPressaoDescontrolada(req.pressaoDescontrolada());
        a.setImc(req.imc());
        a.setTabagismoAtual(req.tabagismoAtual());
        a.setAdesaoRuim(req.adesaoRuim());
        a.setOutros(req.outros() == null || req.outros().isBlank() ? null : req.outros().trim());
        registros.save(a);
        fila.recalcular(); // só atualiza a marcação de aptidão; a ordem não muda
        return avaliar(solicitacaoId);
    }

    @Transactional
    public Aptidao decidir(String solicitacaoId, DecisaoRequest req) {
        ResultadoPaciente r = paciente(solicitacaoId);
        if (req.decisao() == RegistroAptidao.Decisao.AGUARDAR_OTIMIZACAO && (req.plano() == null || req.plano().isBlank())) {
            throw new Erros.RegraNegocio("Para aguardar otimização, descreva o plano (ex.: controlar a glicemia e repetir a HbA1c)");
        }
        RegistroAptidao a = novo(r, RegistroAptidao.Tipo.DECISAO, req.autor(), req.medicoId());
        a.setDecisao(req.decisao());
        a.setJustificativa(req.justificativa().trim());
        a.setPlano(req.plano() == null || req.plano().isBlank() ? null : req.plano().trim());
        a.setReavaliarEm(req.reavaliarEm());
        registros.save(a);
        fila.recalcular();

        Alerta al = new Alerta();
        al.setSolicitacaoId(r.getCaso().solicitacaoId());
        al.setNome(r.getCaso().nome());
        al.setTipo(Alerta.Tipo.AJUSTE_REGULADOR);
        al.setMensagem("Aptidão cirúrgica: " + rotulo(req.decisao()).toLowerCase() + " por " + a.getAutor() + " (\""
                + a.getJustificativa() + "\"). A posição na fila não muda.");
        al.setClasseAntes(r.classe());
        al.setClasseDepois(r.classe());
        al.setPosicaoAntes(r.getPosicao());
        al.setPosicaoDepois(r.getPosicao());
        al.setCriadoEm(LocalDateTime.now());
        alertas.save(al);
        return avaliar(solicitacaoId);
    }

    private RegistroAptidao novo(ResultadoPaciente r, RegistroAptidao.Tipo tipo, String autorPedido, Long medicoId) {
        String autor = autorPedido == null || autorPedido.isBlank() ? "regulador" : autorPedido.trim();
        if (medicoId != null) {
            Medico m = medicos.findById(medicoId).orElseThrow(() -> new Erros.NaoEncontrado("Médico " + medicoId + " não encontrado"));
            autor = m.getNome() + " (" + m.getCrm() + ")";
        }
        RegistroAptidao a = new RegistroAptidao();
        a.setSolicitacaoId(r.getCaso().solicitacaoId());
        a.setTipo(tipo);
        a.setAutor(autor);
        a.setMedicoId(medicoId);
        a.setCriadoEm(LocalDateTime.now());
        return a;
    }

    /** Fatores informados e decisão mais recentes de cada paciente (para marcar a fila). */
    public static Map<String, RegistroAptidao[]> ultimos(List<RegistroAptidao> todos) {
        Map<String, RegistroAptidao[]> m = new HashMap<>();
        for (RegistroAptidao a : todos) {
            RegistroAptidao[] par = m.computeIfAbsent(a.getSolicitacaoId(), k -> new RegistroAptidao[2]);
            par[a.getTipo() == RegistroAptidao.Tipo.FATORES ? 0 : 1] = a;
        }
        return m;
    }

    static FatoresInformados fatores(RegistroAptidao a) {
        return new FatoresInformados(a.isColesterolAlto(), a.isPressaoDescontrolada(), a.getImc(), a.isTabagismoAtual(),
                a.isAdesaoRuim(), a.getOutros(), a.getAutor(), a.getCriadoEm());
    }

    static DecisaoAptidao decisao(RegistroAptidao a) {
        return new DecisaoAptidao(a.getDecisao().name(), rotulo(a.getDecisao()), a.getPlano(), a.getReavaliarEm(),
                a.getJustificativa(), a.getAutor(), a.getMedicoId(), a.getCriadoEm());
    }
}
