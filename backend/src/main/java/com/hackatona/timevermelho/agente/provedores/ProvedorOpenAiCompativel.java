package com.hackatona.timevermelho.agente.provedores;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.hackatona.timevermelho.agente.AgenteProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fala com qualquer API no formato "chat/completions" da OpenAI (ChatGPT). Usa só o HttpClient do JDK.
 *
 * <p>Segurança: a chave só vai no cabeçalho Authorization. Nenhuma mensagem de erro, log ou exceção desta
 * classe inclui a chave, cabeçalhos ou o corpo devolvido pelo provedor; só o status HTTP e o código do erro.
 */
public class ProvedorOpenAiCompativel implements ProvedorIA {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String nome;
    private final String baseUrl;
    private final String chave;
    private final String modelo;
    private final String modeloModeracao;
    private final String campoLimiteTokens;
    private final boolean moderacao;
    private final boolean naoArmazenar;
    private final Duration timeout;
    private final HttpClient http;

    public ProvedorOpenAiCompativel(AgenteProperties.Provedor config, int timeoutSegundos) {
        this.nome = config.nome() == null || config.nome().isBlank() ? "provedor" : config.nome();
        String base = config.baseUrl().trim();
        this.baseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.chave = config.chave().trim();
        this.modelo = config.modelo().trim();
        this.modeloModeracao = config.modeloModeracao();
        this.campoLimiteTokens = config.campoLimiteTokens();
        this.moderacao = config.moderacao();
        this.naoArmazenar = config.naoArmazenar();
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSegundos));
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public String nome() {
        return nome;
    }

    @Override
    public boolean suportaModeracao() {
        return moderacao;
    }

    @Override
    public Resposta gerar(List<Mensagem> mensagens, Opcoes opcoes) {
        HttpRequest req = requisicao("/chat/completions", corpoChat(mensagens, opcoes, false));
        HttpResponse<String> resp = enviar(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw erroDeStatus(resp.statusCode(), resp.body());
        JsonNode raiz = ler(resp.body());
        JsonNode escolha = raiz.path("choices").path(0);
        String texto = escolha.path("message").path("content").asString("");
        JsonNode uso = raiz.path("usage");
        return new Resposta(texto, uso.path("prompt_tokens").asLong(0), uso.path("completion_tokens").asLong(0));
    }

    @Override
    public Resposta gerarStreaming(List<Mensagem> mensagens, Opcoes opcoes, Consumer<String> aoReceber) {
        HttpRequest req = requisicao("/chat/completions", corpoChat(mensagens, opcoes, true));
        HttpResponse<Stream<String>> resp = enviar(req, HttpResponse.BodyHandlers.ofLines());
        try (Stream<String> linhas = resp.body()) {
            if (resp.statusCode() != 200) {
                String corpo = linhas.limit(200).collect(Collectors.joining("\n"));
                throw erroDeStatus(resp.statusCode(), corpo);
            }
            StringBuilder texto = new StringBuilder();
            long[] tokens = {0, 0};
            boolean[] fim = {false};
            linhas.takeWhile(l -> !fim[0]).forEach(linha -> {
                if (!linha.startsWith("data:")) return;
                String dados = linha.substring(5).trim();
                if (dados.equals("[DONE]")) {
                    fim[0] = true;
                    return;
                }
                if (dados.isEmpty()) return;
                JsonNode no = ler(dados);
                JsonNode trecho = no.path("choices").path(0).path("delta").path("content");
                if (trecho.isString() && !trecho.asString().isEmpty()) {
                    texto.append(trecho.asString());
                    aoReceber.accept(trecho.asString());
                }
                JsonNode uso = no.path("usage");
                if (uso.isObject()) {
                    tokens[0] = uso.path("prompt_tokens").asLong(tokens[0]);
                    tokens[1] = uso.path("completion_tokens").asLong(tokens[1]);
                }
            });
            return new Resposta(texto.toString(), tokens[0], tokens[1]);
        }
    }

    @Override
    public boolean moderar(String texto) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("model", modeloModeracao);
        corpo.put("input", texto);
        HttpResponse<String> resp = enviar(requisicao("/moderations", JSON.writeValueAsString(corpo)),
                HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw erroDeStatus(resp.statusCode(), resp.body());
        return ler(resp.body()).path("results").path(0).path("flagged").asBoolean(false);
    }

    // ---------- requisições ----------

    private String corpoChat(List<Mensagem> mensagens, Opcoes opcoes, boolean streaming) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("model", modelo);
        List<Map<String, String>> msgs = new ArrayList<>();
        for (Mensagem m : mensagens) msgs.add(Map.of("role", m.papel(), "content", m.conteudo()));
        corpo.put("messages", msgs);
        corpo.put(campoLimiteTokens, opcoes.maxTokens());
        if (naoArmazenar) corpo.put("store", false); // pede ao provedor para não guardar a conversa
        if (opcoes.json()) corpo.put("response_format", Map.of("type", "json_object"));
        if (streaming) {
            corpo.put("stream", true);
            corpo.put("stream_options", Map.of("include_usage", true));
        }
        return JSON.writeValueAsString(corpo);
    }

    private HttpRequest requisicao(String caminho, String corpo) {
        return HttpRequest.newBuilder(URI.create(baseUrl + caminho))
                .timeout(timeout)
                .header("Authorization", "Bearer " + chave)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo))
                .build();
    }

    private <T> HttpResponse<T> enviar(HttpRequest req, HttpResponse.BodyHandler<T> handler) {
        try {
            return http.send(req, handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErroProvedor(ErroProvedor.Tipo.INDISPONIVEL, nome + ": chamada interrompida");
        } catch (IOException e) {
            // Só o tipo da exceção: a mensagem pode trazer a URL ou detalhes da conexão.
            throw new ErroProvedor(ErroProvedor.Tipo.INDISPONIVEL,
                    nome + ": falha de rede (" + e.getClass().getSimpleName() + ")");
        }
    }

    private JsonNode ler(String json) {
        try {
            return JSON.readTree(json);
        } catch (RuntimeException e) {
            throw new ErroProvedor(ErroProvedor.Tipo.INDISPONIVEL, nome + ": resposta ilegível");
        }
    }

    // ---------- erros ----------

    ErroProvedor erroDeStatus(int status, String corpo) {
        String codigo = codigoDoErro(corpo);
        String resumo = nome + ": HTTP " + status + (codigo.isEmpty() ? "" : " " + codigo);
        ErroProvedor.Tipo tipo;
        if (status == 402 || (status == 429 && cotaEsgotada(codigo))) {
            tipo = ErroProvedor.Tipo.CREDITO_ESGOTADO;
        } else if (status == 429) {
            tipo = ErroProvedor.Tipo.LIMITE_TEMPORARIO;
        } else if (status == 401 || status == 403) {
            tipo = ErroProvedor.Tipo.AUTENTICACAO;
        } else if (status == 408 || status >= 500) {
            tipo = ErroProvedor.Tipo.INDISPONIVEL;
        } else {
            tipo = ErroProvedor.Tipo.PEDIDO_INVALIDO;
        }
        return new ErroProvedor(tipo, resumo);
    }

    private static boolean cotaEsgotada(String codigo) {
        String c = codigo.toLowerCase(Locale.ROOT);
        return c.contains("insufficient_quota") || c.contains("billing") || c.contains("quota_exceeded");
    }

    /** Extrai só {@code error.code}/{@code error.type}, que são códigos curtos e sem dados sensíveis. */
    private static String codigoDoErro(String corpo) {
        if (corpo == null || corpo.isBlank()) return "";
        try {
            JsonNode erro = JSON.readTree(corpo).path("error");
            String codigo = erro.path("code").asString("");
            if (codigo.isEmpty()) codigo = erro.path("type").asString("");
            return codigo.length() > 60 || !codigo.matches("[A-Za-z0-9_.\\-]*") ? "" : codigo;
        } catch (RuntimeException e) {
            return "";
        }
    }
}
