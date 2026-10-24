package com.hackatona.timevermelho.agente.api;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.hackatona.timevermelho.agente.PromptsAgente;
import com.hackatona.timevermelho.agente.AgenteProperties;
import com.hackatona.timevermelho.agente.chat.ChatService;
import com.hackatona.timevermelho.agente.chat.Excecoes.ClienteDesconectouException;
import com.hackatona.timevermelho.agente.chat.SessoesChat;
import com.hackatona.timevermelho.agente.provedores.ProvedoresIA;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.json.JsonMapper;

/**
 * Chatbot do FilaVIVA. O navegador só fala com estas rotas: as chaves das APIs de IA ficam no backend e
 * nunca saem dele. A resposta do chat é um fluxo de eventos (text/event-stream): sessao, trecho, recusa,
 * substituir, erro e fim.
 */
@RestController
@RequestMapping("/api/agente")
public class AgenteController {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final long TEMPO_MAXIMO_MS = 120_000;

    /** O histórico fica no servidor: o cliente manda só a mensagem nova (e o id da sessão, se já tiver um). */
    public record ChatRequest(@Size(max = 64) String sessaoId, @NotBlank @Size(max = 10_000) String mensagem) {
    }

    public record StatusAgente(boolean pronto, String motivoDesligado, String versaoPrompt, boolean moderacaoAtiva,
                               int sessoesAtivas, int recusasUltimos10Min, long tokensUltimaHora,
                               List<ProvedoresIA.StatusProvedor> provedores) {
    }

    private final ChatService chat;
    private final ProvedoresIA provedores;
    private final PromptsAgente prompts;
    private final AgenteProperties props;
    private final SessoesChat sessoes;
    private final Executor executor;

    public AgenteController(ChatService chat, ProvedoresIA provedores, PromptsAgente prompts, AgenteProperties props,
                            SessoesChat sessoes, java.util.concurrent.ExecutorService agenteExecutor) {
        this.chat = chat;
        this.provedores = provedores;
        this.prompts = prompts;
        this.props = props;
        this.sessoes = sessoes;
        this.executor = agenteExecutor;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter conversar(@Valid @RequestBody ChatRequest req, HttpServletRequest http) {
        // getRemoteAddr de propósito: X-Forwarded-For pode ser forjado pelo cliente.
        ChatService.Turno turno = chat.iniciar(req.sessaoId(), req.mensagem(), http.getRemoteAddr());
        SseEmitter emissor = new SseEmitter(TEMPO_MAXIMO_MS);
        executor.execute(() -> {
            try {
                chat.executar(turno, evento -> enviar(emissor, evento));
            } finally {
                emissor.complete();
            }
        });
        return emissor;
    }

    private static void enviar(SseEmitter emissor, ChatService.Evento evento) {
        try {
            emissor.send(SseEmitter.event().name(evento.nome()).data(JSON.writeValueAsString(evento.dados())));
        } catch (IOException | IllegalStateException e) {
            throw new ClienteDesconectouException(e);
        }
    }

    /** Situação do agente e dos provedores. Nunca devolve chaves. */
    @GetMapping("/status")
    public StatusAgente status() {
        String motivo = chat.motivoDesligado();
        return new StatusAgente(motivo == null, motivo, prompts.versao(), props.moderacaoAtiva(), sessoes.total(),
                chat.monitor().recusasUltimos10Min(), chat.monitor().tokensUltimaHora(), provedores.status());
    }
}
