package com.hackatona.timevermelho.agente.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hackatona.timevermelho.agente.AgenteProperties;
import com.hackatona.timevermelho.agente.PromptsAgente;
import com.hackatona.timevermelho.agente.contexto.ContextoDados;
import com.hackatona.timevermelho.agente.chat.Excecoes.AgenteDesligadoException;
import com.hackatona.timevermelho.agente.chat.Excecoes.ClienteDesconectouException;
import com.hackatona.timevermelho.agente.chat.Excecoes.LimiteExcedidoException;
import com.hackatona.timevermelho.agente.chat.Excecoes.MensagemInvalidaException;
import com.hackatona.timevermelho.agente.chat.Excecoes.RespostaBloqueadaException;
import com.hackatona.timevermelho.agente.protecao.FiltroEscopo;
import com.hackatona.timevermelho.agente.protecao.LimitesDoContexto;
import com.hackatona.timevermelho.agente.protecao.ProtecaoEntrada;
import com.hackatona.timevermelho.agente.protecao.TextosAgente;
import com.hackatona.timevermelho.agente.protecao.VazamentoDePrompt;
import com.hackatona.timevermelho.agente.provedores.AgenteIndisponivelException;
import com.hackatona.timevermelho.agente.provedores.ErroProvedor;
import com.hackatona.timevermelho.agente.provedores.Mensagem;
import com.hackatona.timevermelho.agente.provedores.ProvedorIA;
import com.hackatona.timevermelho.agente.provedores.ProvedoresIA;

/**
 * Uma volta do chat, na ordem das barreiras: emergência e tentativa óbvia de burlar (sem IA) → limites do
 * contexto da fila: dado pessoal, paciente específico, rebaixar oncológico, furar a fila (sem IA) → moderação da
 * pergunta → filtro de escopo → geração em streaming (com trava contra vazamento do prompt) → moderação da
 * resposta → aviso de saúde. Qualquer barreira que falhe recusa (a falha fecha, não abre).
 *
 * <p>Privacidade: o conteúdo das conversas nunca vai para o log; só metadados (sessão abreviada, categoria,
 * resultado, tokens, versão do prompt).
 */
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    /** Letras seguradas antes de liberar ao usuário, maior que a janela de 60 do detector de vazamento. */
    private static final int RETENCAO = 80;
    private static final int CONTEXTO_DO_FILTRO = 4;

    public record Evento(String nome, Object dados) {
    }

    public record Turno(SessoesChat.Sessao sessao, String mensagem, String ip) {
    }

    private final AgenteProperties props;
    private final PromptsAgente prompts;
    private final ProvedoresIA provedores;
    private final ProtecaoEntrada protecao;
    private final FiltroEscopo filtro;
    private final SessoesChat sessoes;
    private final LimitadorUso limitador;
    private final ContextoDados contexto;
    private final BloqueioTemporario bloqueio;
    private final MonitorUso monitor;
    private final VazamentoDePrompt vazamento;
    private final LimitesDoContexto limites = new LimitesDoContexto();

    /** Versão enxuta: sem dados da aplicação, com bloqueio e monitor padrão (usada nos testes simples). */
    public ChatService(AgenteProperties props, PromptsAgente prompts, ProvedoresIA provedores,
                       ProtecaoEntrada protecao, FiltroEscopo filtro, SessoesChat sessoes, LimitadorUso limitador) {
        this(props, prompts, provedores, protecao, filtro, sessoes, limitador, ContextoDados.vazio(),
                new BloqueioTemporario(5, java.time.Duration.ofMinutes(10), java.time.Duration.ofMinutes(5),
                        java.time.Clock.systemUTC()),
                new MonitorUso(30, 200_000, java.time.Clock.systemUTC()));
    }

    public ChatService(AgenteProperties props, PromptsAgente prompts, ProvedoresIA provedores,
                       ProtecaoEntrada protecao, FiltroEscopo filtro, SessoesChat sessoes, LimitadorUso limitador,
                       ContextoDados contexto, BloqueioTemporario bloqueio, MonitorUso monitor) {
        this.props = props;
        this.prompts = prompts;
        this.provedores = provedores;
        this.protecao = protecao;
        this.filtro = filtro;
        this.sessoes = sessoes;
        this.limitador = limitador;
        this.contexto = contexto;
        this.bloqueio = bloqueio;
        this.monitor = monitor;
        this.vazamento = new VazamentoDePrompt(prompts.textoSistema());
    }

    public MonitorUso monitor() {
        return monitor;
    }

    /** Por que o agente está desligado, ou {@code null} se está pronto para conversar. */
    public String motivoDesligado() {
        if (!props.ativo()) return "agente desligado por configuração (agente.ativo=false)";
        if (!prompts.pronto()) return prompts.motivoDesligado();
        if (!provedores.temProvedores()) return "nenhum provedor de IA configurado (faltam as chaves de API)";
        if (props.moderacaoAtiva() && !provedores.algumSuportaModeracao()) {
            return "moderação ativa, mas nenhum provedor suporta moderação";
        }
        return null;
    }

    /** Validações rápidas, antes de começar a transmitir: viram erro HTTP comum. */
    public Turno iniciar(String sessaoId, String mensagem, String ip) {
        String motivo = motivoDesligado();
        if (motivo != null) throw new AgenteDesligadoException(motivo);
        String texto = mensagem == null ? "" : mensagem.strip();
        if (texto.isEmpty()) throw new MensagemInvalidaException("A mensagem não pode ser vazia.");
        if (texto.length() > props.limites().maxMensagem()) {
            throw new MensagemInvalidaException("A mensagem pode ter no máximo " + props.limites().maxMensagem()
                    + " caracteres.");
        }
        // Bloqueio por abuso: nunca vale para pedido de ajuda em emergência de saúde.
        boolean emergencia = protecao.avaliar(texto)
                .filter(r -> r.tipo() == ProtecaoEntrada.Tipo.RISCO_A_SAUDE).isPresent();
        if (!emergencia) {
            var restante = bloqueio.bloqueadoPor("ip:" + ip);
            if (restante.isPresent()) {
                long minutos = Math.max(1, restante.get().toMinutes() + 1);
                throw new LimiteExcedidoException("Muitas perguntas recusadas em pouco tempo. Tente de novo em "
                        + minutos + " minuto(s).");
            }
        }
        if (!limitador.permitir("ip:" + ip)) throw new LimiteExcedidoException();
        SessoesChat.Sessao sessao = sessoes.obter(sessaoId);
        if (!limitador.permitir("sessao:" + sessao.id())) throw new LimiteExcedidoException();
        return new Turno(sessao, texto, ip);
    }

    /** Roda a volta inteira e vai entregando eventos. Nunca lança: erros viram evento "erro". */
    public void executar(Turno turno, Consumer<Evento> emitir) {
        try {
            synchronized (turno.sessao()) {
                volta(turno, emitir);
            }
        } catch (ClienteDesconectouException e) {
            log.debug("agente: cliente desconectou (sessao {})", abreviar(turno.sessao().id()));
        } catch (AgenteIndisponivelException | ErroProvedor e) {
            log.warn("agente: sem resposta (sessao {}): {}", abreviar(turno.sessao().id()), e.getMessage());
            avisarErro(emitir, TextosAgente.INDISPONIVEL);
        } catch (RuntimeException e) {
            log.error("agente: erro inesperado ({})", e.getClass().getSimpleName());
            avisarErro(emitir, TextosAgente.INDISPONIVEL);
        }
    }

    private void volta(Turno turno, Consumer<Evento> emitir) {
        String id = abreviar(turno.sessao().id());
        String msg = turno.mensagem();
        emitir.accept(new Evento("sessao", Map.of("sessaoId", turno.sessao().id())));

        // 1. Barreiras sem IA
        var barreira = protecao.avaliar(msg);
        if (barreira.isPresent()) {
            recusar(emitir, barreira.get().tipo().name(), barreira.get().texto());
            registrar(id, barreira.get().tipo().name(), "recusada", 0, 0);
            // emergência não conta como abuso: a pessoa precisa de ajuda, não de bloqueio
            if (barreira.get().tipo() != ProtecaoEntrada.Tipo.RISCO_A_SAUDE) contarRecusa(turno);
            return;
        }

        // 1b. Limites do contexto da fila (sem IA): nada disso sai da aplicação nem entra no histórico.
        // Não conta como abuso: são perguntas honestas que recebem uma resposta fixa e segura.
        var limite = limites.avaliar(msg);
        if (limite.isPresent()) {
            recusar(emitir, limite.get().tipo().name(), limite.get().texto());
            registrar(id, limite.get().tipo().name(), "recusada", 0, 0);
            return;
        }

        // 2. Moderação da pergunta
        if (props.moderacaoAtiva()) {
            boolean sinalizada;
            try {
                sinalizada = provedores.moderar(msg);
            } catch (ErroProvedor e) {
                recusar(emitir, "MODERACAO_INDISPONIVEL", TextosAgente.NAO_VALIDADO);
                registrar(id, "MODERACAO_INDISPONIVEL", "recusada", 0, 0);
                return;
            }
            if (sinalizada) {
                recusar(emitir, "MODERACAO", TextosAgente.MODERACAO_ENTRADA);
                registrar(id, "MODERACAO", "recusada", 0, 0);
                contarRecusa(turno);
                return;
            }
        }

        // 3. Filtro de escopo
        FiltroEscopo.Classificacao classe;
        try {
            classe = filtro.classificar(msg, turno.sessao().ultimas(CONTEXTO_DO_FILTRO));
        } catch (ErroProvedor e) {
            recusar(emitir, "FILTRO_INDISPONIVEL", TextosAgente.NAO_VALIDADO);
            registrar(id, "FILTRO_INDISPONIVEL", "recusada", 0, 0);
            return;
        }
        if (!classe.dentroDoEscopo()) {
            String texto = switch (classe.categoria()) {
                case INFRA_OU_CODIGO -> TextosAgente.INFRA_OU_CODIGO;
                case PACIENTE_ESPECIFICO -> TextosAgente.PACIENTE_ESPECIFICO;
                case TENTATIVA_DE_BURLAR -> TextosAgente.TENTATIVA_DE_BURLAR;
                default -> TextosAgente.FORA_DO_ESCOPO;
            };
            recusar(emitir, classe.categoria().name(), texto);
            registrar(id, classe.categoria().name(), "recusada", 0, 0);
            // perguntar sobre a própria situação não é abuso: só os outros desvios contam para o bloqueio
            if (classe.categoria() != FiltroEscopo.Categoria.PACIENTE_ESPECIFICO) contarRecusa(turno);
            return;
        }

        // 4. Geração em streaming, segurando o final até confirmar que não há vazamento do prompt
        List<Mensagem> msgs = new ArrayList<>();
        msgs.add(Mensagem.sistema(prompts.textoSistema()));
        msgs.addAll(turno.sessao().ultimas(props.limites().maxHistorico()));
        // Dados agregados da aplicação, lidos agora (só quando a pergunta pede), marcados como conteúdo externo.
        String dados = contexto.montar(msg);
        msgs.add(Mensagem.usuario(dados.isEmpty() ? msg : dados + "\n\nPergunta do usuário:\n" + msg));

        StringBuilder acumulado = new StringBuilder();
        int[] liberado = {0};
        Consumer<String> aoReceber = trecho -> {
            acumulado.append(trecho);
            if (vazamento.vazou(acumulado.toString())) throw new RespostaBloqueadaException();
            int ate = acumulado.length() - RETENCAO;
            if (ate > liberado[0]) {
                enviarTrecho(emitir, acumulado.substring(liberado[0], ate));
                liberado[0] = ate;
            }
        };
        ProvedorIA.Resposta resposta;
        try {
            resposta = provedores.gerarStreaming(msgs, ProvedorIA.Opcoes.texto(props.maxTokens()), aoReceber);
        } catch (RespostaBloqueadaException e) {
            substituir(emitir, TextosAgente.TENTATIVA_DE_BURLAR);
            registrar(id, classe.categoria().name(), "resposta_bloqueada", 0, 0);
            return;
        }
        String completa = acumulado.toString();
        if (vazamento.vazou(completa)) {
            substituir(emitir, TextosAgente.TENTATIVA_DE_BURLAR);
            registrar(id, classe.categoria().name(), "resposta_bloqueada", resposta.tokensEntrada(), resposta.tokensSaida());
            return;
        }

        // 5. Moderação da resposta (falha fecha: se não der para validar, troca por recusa)
        if (props.moderacaoAtiva()) {
            boolean sinalizada;
            try {
                sinalizada = completa.isBlank() ? false : provedores.moderar(completa);
            } catch (ErroProvedor e) {
                sinalizada = true;
            }
            if (sinalizada) {
                substituir(emitir, TextosAgente.MODERACAO_SAIDA);
                registrar(id, classe.categoria().name(), "resposta_moderada", resposta.tokensEntrada(), resposta.tokensSaida());
                return;
            }
        }

        // 6. Libera o que faltava e fecha com o aviso de saúde
        if (liberado[0] < completa.length()) enviarTrecho(emitir, completa.substring(liberado[0]));
        String finalComAviso = completa;
        if (classe.categoria() == FiltroEscopo.Categoria.SAUDE) {
            enviarTrecho(emitir, TextosAgente.AVISO_SAUDE);
            finalComAviso = completa + TextosAgente.AVISO_SAUDE;
        }
        sessoes.registrar(turno.sessao(), msg, finalComAviso);
        emitir.accept(new Evento("fim", Map.of("versaoPrompt", prompts.versao())));
        registrar(id, classe.categoria().name(), "respondida", resposta.tokensEntrada(), resposta.tokensSaida());
    }

    /** Recusa "de verdade" (a pessoa pediu algo que não pode): conta para o bloqueio por abuso e para o monitor. */
    private void contarRecusa(Turno turno) {
        bloqueio.registrarRecusa("ip:" + turno.ip());
        monitor.registrarRecusa();
    }

    private void enviarTrecho(Consumer<Evento> emitir, String texto) {
        emitir.accept(new Evento("trecho", Map.of("texto", texto)));
    }

    private void recusar(Consumer<Evento> emitir, String motivo, String texto) {
        emitir.accept(new Evento("recusa", Map.of("motivo", motivo, "texto", texto)));
        emitir.accept(new Evento("fim", Map.of("versaoPrompt", prompts.versao())));
    }

    /** Pede ao cliente para trocar o que já mostrou por este texto. */
    private void substituir(Consumer<Evento> emitir, String texto) {
        emitir.accept(new Evento("substituir", Map.of("texto", texto)));
        emitir.accept(new Evento("fim", Map.of("versaoPrompt", prompts.versao())));
    }

    private void avisarErro(Consumer<Evento> emitir, String texto) {
        try {
            emitir.accept(new Evento("erro", Map.of("texto", texto)));
        } catch (ClienteDesconectouException ignorado) {
            // ninguém para receber
        }
    }

    private void registrar(String sessao, String categoria, String resultado, long tokensEntrada, long tokensSaida) {
        log.info("agente: sessao={} categoria={} resultado={} tokens_entrada={} tokens_saida={} versao={}", sessao,
                categoria, resultado, tokensEntrada, tokensSaida, prompts.versao());
    }

    private static String abreviar(String id) {
        return id.length() <= 8 ? id : id.substring(0, 8);
    }
}
