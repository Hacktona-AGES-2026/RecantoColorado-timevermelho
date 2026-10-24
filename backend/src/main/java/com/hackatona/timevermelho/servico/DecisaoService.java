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
    private final com.hackatona.timevermelho.dominio.DesejoCirurgiaRepository desejos;
    private com.hackatona.timevermelho.dominio.ComunicacaoBranRepository brans;
    private com.hackatona.timevermelho.dominio.RegistroAptidaoRepository registrosAptidao;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setRegistrosAptidao(com.hackatona.timevermelho.dominio.RegistroAptidaoRepository r) {
        this.registrosAptidao = r;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setBrans(com.hackatona.timevermelho.dominio.ComunicacaoBranRepository brans) {
        this.brans = brans;
    }

    public DecisaoService(FilaService fila, SolicitacaoRepository solicitacoes, EventoClinicoRepository eventos,
                          AjusteReguladorRepository ajustes, AlertaRepository alertas,
                          com.hackatona.timevermelho.dominio.DesejoCirurgiaRepository desejos) {
        this.desejos = desejos;
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
        return registrarEvento(req, Origem.REGULADOR);
    }

    /** Registra o evento, recalcula a fila e grava na auditoria quem registrou (regulador ou médico). */
    public synchronized Dtos.Impacto registrarEvento(Dtos.NovoEvento req, Origem origem) {
        String id = resolverId(req.solicitacaoId());
        String autor = req.autor() == null || req.autor().isBlank() ? "regulador" : req.autor().trim();
        if (!TIPOS_EVENTO.contains(req.tipo())) {
            throw new Erros.RegraNegocio("Tipo de evento desconhecido: " + req.tipo());
        }
        ResultadoPaciente antes = fila.atual().get(id);

        EventoClinico e = new EventoClinico();
        e.setId("EVT-NOVO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        e.setSolicitacaoId(id);
        e.setSolicitacaoOriginal(id);
        e.setPacienteId(antes.getCaso().pacienteId());
        // sem data informada: hoje (não a data de referência do dataset); data futura não é aceita
        LocalDate hoje = fila.hoje();
        LocalDate data = req.data() == null ? hoje : req.data();
        if (data.isAfter(hoje)) {
            throw new Erros.RegraNegocio("A data do evento (" + data.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + ") está no futuro. Hoje é " + hoje.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
        }
        e.setData(data);
        e.setTipo(req.tipo());
        e.setResultado(req.resultado());
        e.setValor(req.valorNumerico());
        e.setDescricao(req.descricao());
        e.setOrigem("FilaVIVA (registrado por " + autor + ")");
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

        // auditoria: quem registrou o exame/evento e o que ele causou (não é decisão: não muda a fila)
        StringBuilder texto = new StringBuilder(req.tipo().replace('_', ' '));
        if (req.resultado() != null && !req.resultado().isBlank()) texto.append(" (").append(req.resultado().replace('_', ' ')).append(")");
        if (req.valorNumerico() != null) texto.append(" ").append(String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f", req.valorNumerico()));
        texto.append(" em ").append(data.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        if (req.descricao() != null && !req.descricao().isBlank()) texto.append(": ").append(req.descricao().trim());
        texto.append(" [").append(e.getId()).append("]. Impacto: ").append(impacto.mensagem());
        String justificativa = texto.length() > 2000 ? texto.substring(0, 1997) + "..." : texto.toString();
        AjusteRegulador a = novoAjuste(id, TipoAjuste.REGISTRAR_EVENTO, antes.classe(), depois.classe(), justificativa, autor);
        marcarOrigem(a, origem);
        ajustes.save(a);
        return impacto;
    }

    /**
     * Decisões sobre um paciente. Regras de rebaixamento (FAQ do desafio: "Pode sugerir, com justificativa, para o
     * regulador confirmar. Nunca um oncológico."):
     * - pedir uma classe menos urgente que a vigente não rebaixa: registra um rebaixamento sugerido, e a classe vigente
     *   continua valendo até o regulador confirmar ou rejeitar;
     * - oncológico (CID C ou biópsia positiva) nunca tem a classe reduzida, nem de A1 para A2.
     */
    public synchronized Dtos.Impacto ajustar(Dtos.AjusteRequest req) {
        return ajustar(req, Origem.REGULADOR);
    }

    /**
     * Quem fez a alteração, para a auditoria.
     * @param medicoId médico cadastrado (null = regulador)
     * @param informacaoForaDoSistema o médico sabe de algo que ainda não está registrado no sistema
     */
    public record Origem(Long medicoId, boolean informacaoForaDoSistema) {
        public static final Origem REGULADOR = new Origem(null, false);
    }

    /** Mesmas regras para regulador e médico: a fila é recalculada pelo motor, sem atalho na ordenação. */
    public synchronized Dtos.Impacto ajustar(Dtos.AjusteRequest req, Origem origem) {
        String id = resolverId(req.solicitacaoId());
        ResultadoPaciente antes = fila.atual().get(id);
        ClasseSwalis vigente = antes.classe();
        String justificativa = req.justificativa().trim();
        String autor = req.autor() == null || req.autor().isBlank() ? "regulador" : req.autor().trim();
        ClasseSwalis nova = null;

        switch (req.tipo()) {
            case ALTERAR_CLASSE, SUGERIR_REBAIXAMENTO -> {
                nova = classe(req.classeNova());
                if (vigente.maisUrgenteQue(nova)) return sugerirRebaixamento(antes, nova, justificativa, autor, origem);
                if (req.tipo() == TipoAjuste.SUGERIR_REBAIXAMENTO) {
                    throw new Erros.RegraNegocio("Rebaixamento precisa de uma classe menos urgente que a vigente ("
                            + vigente + "). Para subir a classe, use \"Mudar classe\".");
                }
            }
            case CONFIRMAR_REBAIXAMENTO, REJEITAR_REBAIXAMENTO -> {
                return decidirRebaixamento(antes, req.tipo(), justificativa, autor);
            }
            case REBAIXAMENTO_BLOQUEADO -> throw new Erros.RegraNegocio("Esse registro é feito só pelo sistema");
            case REGISTRAR_EVENTO -> throw new Erros.RegraNegocio("Exames e eventos são registrados em \"Registrar evento "
                    + "clínico\" (POST /api/eventos), que já grava na auditoria quem registrou");
            case CONFIRMAR_SAIDA, RECUSAR_SAIDA -> {
                if (antes.getSinalSaida() == null && req.tipo() == TipoAjuste.RECUSAR_SAIDA) {
                    throw new Erros.RegraNegocio("Não há sugestão de saída para recusar");
                }
            }
            case FIXAR_POSICAO -> validarPosicao(antes, req.posicao());
            case LIBERAR_POSICAO -> {
                if (antes.getPosicaoFixada() == null) throw new Erros.RegraNegocio("Este paciente não tem posição fixada");
            }
            case CONFIRMAR_CLASSE -> { }
        }

        AjusteRegulador a = novoAjuste(id, req.tipo(), vigente,
                req.tipo() == TipoAjuste.ALTERAR_CLASSE ? nova : vigente, justificativa, autor);
        if (req.tipo() == TipoAjuste.FIXAR_POSICAO) a.setPosicaoFixada(req.posicao());
        marcarOrigem(a, origem);
        ajustes.save(a);

        ResultadoPaciente depois = fila.recalcular().get(id);
        String acao = switch (req.tipo()) {
            case CONFIRMAR_CLASSE -> (origem.medicoId() == null ? "regulador" : autor) + " confirmou a classe " + depois.classe();
            case ALTERAR_CLASSE -> (origem.medicoId() == null ? "regulador" : autor) + " definiu a classe " + depois.classe();
            case CONFIRMAR_SAIDA -> "regulador confirmou a saída da fila";
            case RECUSAR_SAIDA -> "regulador recusou a saída; paciente continua na fila";
            case FIXAR_POSICAO -> (origem.medicoId() == null ? "regulador" : autor) + " colocou o paciente na posição "
                    + req.posicao() + (depois.getPosicao() != null && depois.getPosicao() > req.posicao()
                    ? " (ficou na " + depois.getPosicao() + ": " + motivoLimite(depois) + ")" : "");
            case LIBERAR_POSICAO -> (origem.medicoId() == null ? "regulador" : autor)
                    + " liberou a posição escolhida; o paciente volta para a posição calculada";
            default -> req.tipo().name();
        };
        Dtos.Impacto impacto = ImpactoCalculadora.impacto(antes, depois, acao + " (\"" + a.getJustificativa() + "\")");
        alertar(antes, depois, Alerta.Tipo.AJUSTE_REGULADOR, impacto.mensagem());
        return impacto;
    }

    /**
     * Posição escolhida: só para cima. Descer um paciente é rebaixar a prioridade dele, e isso segue o fluxo de
     * rebaixamento (sugestão que o regulador confirma).
     */
    private void validarPosicao(ResultadoPaciente antes, Integer posicao) {
        if (posicao == null) throw new Erros.RegraNegocio("Informe a posição desejada (1 = primeiro da fila)");
        if (antes.getPosicao() == null || !antes.getStatus().naFila()) {
            throw new Erros.RegraNegocio("O paciente não está na fila ativa");
        }
        int total = fila.atual().ativos().size();
        if (posicao < 1 || posicao > total) {
            throw new Erros.RegraNegocio("A posição precisa estar entre 1 e " + total);
        }
        if (posicao >= antes.getPosicao()) {
            throw new Erros.RegraNegocio("O paciente está na posição " + antes.getPosicao() + ". Só dá para escolher uma "
                    + "posição acima dela. Para baixar a prioridade, sugira um rebaixamento de classe: o regulador confirma.");
        }
    }

    /** Por que o paciente não chegou à posição escolhida. */
    private static String motivoLimite(ResultadoPaciente r) {
        return r.isMovidoPelaRegraDeOuro()
                ? "regra de ouro, sem atendimento ou exame de piora registrado ele não passa à frente de quem tem câncer"
                : "outro paciente com posição escolhida antes dele";
    }

    /** Mensagem mostrada ao regulador quando o rebaixamento de um oncológico é recusado. */
    static String bloqueioOncologico(ClasseSwalis vigente, ClasseSwalis pedida) {
        return "Regra de ouro e FAQ do desafio: paciente oncológico (CID C ou biópsia positiva) não pode ter a "
                + "prioridade clínica reduzida, nem de A1 para A2. Pedido de " + vigente + " para " + pedida
                + " recusado; a classe " + vigente + " continua valendo. Se o diagnóstico mudou, registre o evento "
                + "clínico (ex.: nova biópsia).";
    }

    private Dtos.Impacto sugerirRebaixamento(ResultadoPaciente antes, ClasseSwalis proposta, String fatores, String autor,
                                             Origem origem) {
        ClasseSwalis vigente = antes.classe();
        if (antes.oncologico()) throw new Erros.RegraNegocio(bloqueioOncologico(vigente, proposta));
        String id = antes.getCaso().solicitacaoId();
        ClasseSwalis calculada = antes.getClassificacao().classeCalculada();
        AjusteRegulador a = novoAjuste(id, TipoAjuste.SUGERIR_REBAIXAMENTO, vigente, proposta, fatores, autor);
        marcarOrigem(a, origem);
        a.setSeguirCalculada(proposta == calculada);
        a.setEvidencias(FilaService.evidencias(antes) + (proposta == calculada ? ""
                : ". A classe proposta (" + proposta + ") é diferente da calculada (" + calculada + ")"));
        ajustes.save(a);

        ResultadoPaciente depois = fila.recalcular().get(id);
        Dtos.Impacto impacto = ImpactoCalculadora.impacto(antes, depois, "rebaixamento de " + vigente + " para " + proposta
                + " sugerido por " + autor + " (\"" + fatores + "\"). Aguarda o regulador confirmar ou rejeitar em Alertas; "
                + "até lá a classe " + depois.classe() + " continua valendo");
        alertar(antes, depois, Alerta.Tipo.AJUSTE_REGULADOR, impacto.mensagem());
        return impacto;
    }

    private Dtos.Impacto decidirRebaixamento(ResultadoPaciente antes, TipoAjuste tipo, String justificativa, String autor) {
        String id = antes.getCaso().solicitacaoId();
        AjusteRegulador pendente = ajustes.findBySolicitacaoIdOrderByCriadoEmDescIdDesc(id).stream()
                .filter(x -> x.getTipo().ehRebaixamento()).findFirst()
                .filter(x -> x.getTipo() == TipoAjuste.SUGERIR_REBAIXAMENTO)
                .orElseThrow(() -> new Erros.RegraNegocio("Não há rebaixamento pendente para este paciente"));
        ClasseSwalis vigente = antes.classe();
        AjusteRegulador a;
        ResultadoPaciente depois;
        String acao;
        if (tipo == TipoAjuste.CONFIRMAR_REBAIXAMENTO) {
            // o paciente pode ter virado oncológico depois da sugestão: aí o rebaixamento não pode mais acontecer
            if (antes.oncologico()) throw new Erros.RegraNegocio(bloqueioOncologico(vigente, pendente.getClasseNova())
                    + " Rejeite a sugestão para encerrá-la.");
            a = novoAjuste(id, tipo, vigente, pendente.getClasseNova(), justificativa, autor);
            a.setSeguirCalculada(pendente.isSeguirCalculada());
            ajustes.save(a);
            depois = fila.recalcular(Set.of(id)).get(id);
            acao = "regulador confirmou o rebaixamento de " + vigente + " para " + depois.classe();
        } else {
            a = novoAjuste(id, tipo, vigente, vigente, justificativa, autor);
            ajustes.save(a);
            depois = fila.recalcular().get(id);
            acao = "regulador rejeitou o rebaixamento para " + pendente.getClasseNova() + "; classe " + depois.classe()
                    + " mantida";
        }
        Dtos.Impacto impacto = ImpactoCalculadora.impacto(antes, depois, acao + " (\"" + justificativa + "\")");
        alertar(antes, depois, Alerta.Tipo.AJUSTE_REGULADOR, impacto.mensagem());
        return impacto;
    }

    private static ClasseSwalis classe(String valor) {
        if (valor == null) throw new Erros.RegraNegocio("Informe a classe nova");
        try {
            return ClasseSwalis.valueOf(valor);
        } catch (IllegalArgumentException ex) {
            throw new Erros.RegraNegocio("Classe inválida: " + valor);
        }
    }

    private static void marcarOrigem(AjusteRegulador a, Origem origem) {
        a.setMedicoId(origem.medicoId());
        a.setInformacaoForaDoSistema(origem.informacaoForaDoSistema());
    }

    private static AjusteRegulador novoAjuste(String id, TipoAjuste tipo, ClasseSwalis anterior, ClasseSwalis nova,
                                              String justificativa, String autor) {
        AjusteRegulador a = new AjusteRegulador();
        a.setSolicitacaoId(id);
        a.setTipo(tipo);
        a.setClasseAnterior(anterior);
        a.setClasseNova(nova);
        a.setJustificativa(justificativa);
        a.setAutor(autor);
        a.setCriadoEm(LocalDateTime.now());
        return a;
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
        desejos.deleteAll();
        if (brans != null) brans.deleteAll();
        if (registrosAptidao != null) registrosAptidao.deleteAll();
        fila.recalcularComoBase();
    }
}
