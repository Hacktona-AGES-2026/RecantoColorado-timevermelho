package com.hackatona.timevermelho.servico;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.dominio.AjusteRegulador;
import com.hackatona.timevermelho.dominio.AjusteReguladorRepository;
import com.hackatona.timevermelho.dominio.Alerta;
import com.hackatona.timevermelho.dominio.AlertaRepository;
import com.hackatona.timevermelho.dominio.EventoClinico;
import com.hackatona.timevermelho.dominio.EventoClinicoRepository;
import com.hackatona.timevermelho.dominio.Solicitacao;
import com.hackatona.timevermelho.dominio.SolicitacaoRepository;
import com.hackatona.timevermelho.dominio.TipoAjuste;
import com.hackatona.timevermelho.motor.ClasseSwalis;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.motor.Sinal;

/**
 * Tudo que muda a fila de verdade: um evento clínico novo ou uma decisão do regulador.
 * Cada mudança recalcula a fila inteira e gera alertas com o impacto.
 */
@Service
public class DecisaoService {

    private static final Set<String> TIPOS_EVENTO = Set.of("biopsia", "pronto_socorro", "internacao", "exame_imagem",
            "consulta_reavaliacao", "contato_telefonico", "hba1c", "falta");

    private final FilaService fila;
    private final SolicitacaoRepository solicitacoes;
    private final EventoClinicoRepository eventos;
    private final AjusteReguladorRepository ajustes;
    private final AlertaRepository alertas;

    public DecisaoService(FilaService fila, SolicitacaoRepository solicitacoes, EventoClinicoRepository eventos,
                          AjusteReguladorRepository ajustes, AlertaRepository alertas) {
        this.fila = fila;
        this.solicitacoes = solicitacoes;
        this.eventos = eventos;
        this.alertas = alertas;
        this.ajustes = ajustes;
    }

    /** Se pedirem uma solicitação duplicada, trabalha na principal. */
    public String resolverId(String solicitacaoId) {
        Solicitacao s = solicitacoes.findById(solicitacaoId)
                .orElseThrow(() -> new Erros.NaoEncontrado("Solicitação " + solicitacaoId + " não encontrada"));
        return s.getDuplicadaDe() != null ? s.getDuplicadaDe() : s.getId();
    }

    public synchronized Dtos.Impacto registrarEvento(Dtos.NovoEvento req) {
        String id = resolverId(req.solicitacaoId());
        if (!TIPOS_EVENTO.contains(req.tipo())) {
            throw new Erros.RegraNegocio("Tipo de evento desconhecido: " + req.tipo());
        }
        ResultadoPaciente antes = fila.atual().get(id);

        EventoClinico e = new EventoClinico();
        e.setId("EVT-NOVO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        e.setSolicitacaoId(id);
        e.setSolicitacaoOriginal(id);
        e.setPacienteId(antes.getCaso().pacienteId());
        LocalDate data = req.data() == null ? fila.referencia() : req.data();
        e.setData(data);
        e.setTipo(req.tipo());
        e.setResultado(req.resultado());
        e.setValor(req.valorNumerico());
        e.setDescricao(req.descricao());
        e.setOrigem("FilaVIVA (registrado pelo regulador)");
        e.setSintetico(true);
        e.setCriadoEm(LocalDateTime.now());
        eventos.save(e);

        ResultadoPaciente depois = fila.recalcular().get(id);
        List<Sinal> novos = ImpactoCalculadora.sinaisNovos(antes, depois);
        String motivo = novos.isEmpty()
                ? "evento " + req.tipo() + (req.resultado() == null ? "" : " (" + req.resultado() + ")") + " não muda a urgência"
                : novos.getFirst().descricao();
        Dtos.Impacto impacto = ImpactoCalculadora.impacto(antes, depois, motivo);
        alertar(antes, depois, Alerta.Tipo.EVENTO_NOVO, "Evento novo — " + impacto.mensagem());
        return impacto;
    }

    public synchronized Dtos.Impacto ajustar(Dtos.AjusteRequest req) {
        String id = resolverId(req.solicitacaoId());
        ResultadoPaciente antes = fila.atual().get(id);
        ClasseSwalis nova = null;

        switch (req.tipo()) {
            case ALTERAR_CLASSE -> {
                if (req.classeNova() == null) throw new Erros.RegraNegocio("Informe a classe nova");
                try {
                    nova = ClasseSwalis.valueOf(req.classeNova());
                } catch (IllegalArgumentException ex) {
                    throw new Erros.RegraNegocio("Classe inválida: " + req.classeNova());
                }
                if (antes.oncologico() && ClasseSwalis.A2.maisUrgenteQue(nova)) {
                    throw new Erros.RegraNegocio("Regra de ouro: paciente oncológico não pode ficar abaixo da classe A2. "
                            + "Se o diagnóstico mudou, registre o evento clínico (ex.: nova biópsia) antes.");
                }
            }
            case CONFIRMAR_SAIDA, RECUSAR_SAIDA -> {
                if (antes.getSinalSaida() == null && req.tipo() == TipoAjuste.RECUSAR_SAIDA) {
                    throw new Erros.RegraNegocio("Não há sugestão de saída para recusar");
                }
            }
            case CONFIRMAR_CLASSE -> { }
        }

        AjusteRegulador a = new AjusteRegulador();
        a.setSolicitacaoId(id);
        a.setTipo(req.tipo());
        a.setClasseAnterior(antes.classe());
        a.setClasseNova(req.tipo() == TipoAjuste.ALTERAR_CLASSE ? nova : antes.classe());
        a.setJustificativa(req.justificativa().trim());
        a.setAutor(req.autor() == null || req.autor().isBlank() ? "regulador" : req.autor().trim());
        a.setCriadoEm(LocalDateTime.now());
        ajustes.save(a);

        ResultadoPaciente depois = fila.recalcular().get(id);
        String acao = switch (req.tipo()) {
            case CONFIRMAR_CLASSE -> "regulador confirmou a classe " + depois.classe();
            case ALTERAR_CLASSE -> "regulador definiu a classe " + depois.classe();
            case CONFIRMAR_SAIDA -> "regulador confirmou a saída da fila";
            case RECUSAR_SAIDA -> "regulador recusou a saída; paciente continua na fila";
        };
        Dtos.Impacto impacto = ImpactoCalculadora.impacto(antes, depois, acao + " (\"" + a.getJustificativa() + "\")");
        alertar(antes, depois, Alerta.Tipo.AJUSTE_REGULADOR, impacto.mensagem());
        return impacto;
    }

    private void alertar(ResultadoPaciente antes, ResultadoPaciente depois, Alerta.Tipo tipo, String mensagem) {
        alertas.save(novoAlerta(antes, depois, tipo, mensagem));
        if (tipo == Alerta.Tipo.EVENTO_NOVO && antes.classe() != depois.classe()) {
            alertas.save(novoAlerta(antes, depois, Alerta.Tipo.MUDANCA_CLASSE,
                    "Classe mudou de " + antes.classe() + " para " + depois.classe() + ": "
                            + depois.getClassificacao().regra()));
        }
    }

    private static Alerta novoAlerta(ResultadoPaciente antes, ResultadoPaciente depois, Alerta.Tipo tipo, String msg) {
        Alerta al = new Alerta();
        al.setSolicitacaoId(depois.getCaso().solicitacaoId());
        al.setNome(depois.getCaso().nome());
        al.setTipo(tipo);
        al.setMensagem(msg);
        al.setClasseAntes(antes.classe());
        al.setClasseDepois(depois.classe());
        al.setPosicaoAntes(antes.getPosicao());
        al.setPosicaoDepois(depois.getPosicao());
        al.setCriadoEm(LocalDateTime.now());
        return al;
    }

    /** Volta a demo ao estado do dataset original. */
    public synchronized void resetar() {
        eventos.deleteBySinteticoTrue();
        ajustes.deleteAll();
        alertas.deleteAll();
        fila.recalcular();
    }
}
