package com.hackatona.timevermelho.agente.provedores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.hackatona.timevermelho.agente.AgenteProperties;
import com.hackatona.timevermelho.agente.provedores.ErroProvedor.Tipo;
import com.sun.net.httpserver.HttpServer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Fala com um servidor HTTP falso na própria máquina, no formato da API da OpenAI. Nenhuma chamada real,
 * nenhum crédito gasto. Também prova que a chave não vaza em exceções, logs nem no status (T26).
 */
class ProvedorOpenAiCompativelTest {

    static final String CHAVE = "sk-teste-SEGREDO-123456";

    HttpServer servidor;
    final List<String> corpos = new CopyOnWriteArrayList<>();
    final List<String> autorizacoes = new CopyOnWriteArrayList<>();
    final List<String> caminhos = new CopyOnWriteArrayList<>();

    int statusDaResposta = 200;
    String corpoDaResposta = "{}";
    String tipoDaResposta = "application/json";

    @BeforeEach
    void subirServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            caminhos.add(troca.getRequestURI().getPath());
            autorizacoes.add(troca.getRequestHeaders().getFirst("Authorization"));
            corpos.add(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = corpoDaResposta.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", tipoDaResposta);
            troca.sendResponseHeaders(statusDaResposta, bytes.length);
            try (OutputStream os = troca.getResponseBody()) {
                os.write(bytes);
            }
        });
        servidor.start();
    }

    @AfterEach
    void derrubarServidor() {
        try {
            servidor.stop(0);
        } catch (RuntimeException jaParado) {
            // alguns testes derrubam o servidor de propósito
        }
    }

    private ProvedorOpenAiCompativel provedor(String baseUrl) {
        return provedor(baseUrl, true);
    }

    private ProvedorOpenAiCompativel provedor(String baseUrl, boolean naoArmazenar) {
        return new ProvedorOpenAiCompativel(new AgenteProperties.Provedor("openai-teste", baseUrl, CHAVE, "modelo-teste",
                "moderacao-teste", "max_completion_tokens", true, naoArmazenar), 5);
    }

    @Test
    void pedeAoProvedorParaNaoGuardarAConversa() {
        corpoDaResposta = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}";
        provedor().gerar(PEDIDO, ProvedorIA.Opcoes.texto(10));
        assertThat(corpos.getFirst()).contains("\"store\":false");
    }

    @Test
    void storeFalsePodeSerDesligadoParaApisQueRecusamOCampo() {
        corpoDaResposta = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}";
        provedor("http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1", false).gerar(PEDIDO, ProvedorIA.Opcoes.texto(10));
        assertThat(corpos.getFirst()).doesNotContain("\"store\"");
    }

    @Test
    void streamingTambemPedeParaNaoGuardar() {
        tipoDaResposta = "text/event-stream";
        corpoDaResposta = "data: [DONE]\n\n";
        provedor().gerarStreaming(PEDIDO, ProvedorIA.Opcoes.texto(10), t -> { });
        assertThat(corpos.getFirst()).contains("\"store\":false");
    }

    private ProvedorOpenAiCompativel provedor() {
        return provedor("http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1");
    }

    private static final List<Mensagem> PEDIDO = List.of(Mensagem.sistema("Seja breve."), Mensagem.usuario("Olá"));

    // ---------- resposta completa ----------

    @Test
    void geraRespostaEEnviaChaveModeloMensagensELimiteDeTokens() {
        corpoDaResposta = """
                {"choices":[{"message":{"role":"assistant","content":"Olá! Como posso ajudar?"}}],
                 "usage":{"prompt_tokens":12,"completion_tokens":7}}""";

        var r = provedor().gerar(PEDIDO, ProvedorIA.Opcoes.texto(321));

        assertThat(r.texto()).isEqualTo("Olá! Como posso ajudar?");
        assertThat(r.tokensEntrada()).isEqualTo(12);
        assertThat(r.tokensSaida()).isEqualTo(7);
        assertThat(caminhos).containsExactly("/v1/chat/completions");
        assertThat(autorizacoes).containsExactly("Bearer " + CHAVE);
        String corpo = corpos.getFirst();
        assertThat(corpo).contains("\"model\":\"modelo-teste\"").contains("\"max_completion_tokens\":321")
                .contains("\"role\":\"system\"").contains("Seja breve.").doesNotContain("response_format")
                .doesNotContain("\"stream\"");
        assertThat(corpo).doesNotContain(CHAVE); // a chave só vai no cabeçalho
    }

    @Test
    void pedeJsonQuandoAsOpcoesMandam() {
        corpoDaResposta = "{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}";
        provedor().gerar(PEDIDO, ProvedorIA.Opcoes.json(50));
        assertThat(corpos.getFirst()).contains("\"response_format\":{\"type\":\"json_object\"}");
    }

    @Test
    void aceitaBaseUrlComBarraNoFinal() {
        corpoDaResposta = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}";
        provedor("http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1/").gerar(PEDIDO, ProvedorIA.Opcoes.texto(10));
        assertThat(caminhos).containsExactly("/v1/chat/completions");
    }

    @Test
    void respostaSemUsoDevolveZeroTokens() {
        corpoDaResposta = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}";
        var r = provedor().gerar(PEDIDO, ProvedorIA.Opcoes.texto(10));
        assertThat(r.tokensEntrada()).isZero();
        assertThat(r.tokensSaida()).isZero();
    }

    // ---------- streaming ----------

    @Test
    void streamingEntregaOsTrechosNaOrdemELeOUso() {
        tipoDaResposta = "text/event-stream";
        corpoDaResposta = """
                data: {"choices":[{"delta":{"role":"assistant","content":""}}]}

                data: {"choices":[{"delta":{"content":"Olá"}}]}

                data: {"choices":[{"delta":{"content":", tudo bem?"}}]}

                data: {"choices":[],"usage":{"prompt_tokens":30,"completion_tokens":4}}

                data: [DONE]

                """;
        List<String> trechos = new ArrayList<>();

        var r = provedor().gerarStreaming(PEDIDO, ProvedorIA.Opcoes.texto(100), trechos::add);

        assertThat(trechos).containsExactly("Olá", ", tudo bem?");
        assertThat(r.texto()).isEqualTo("Olá, tudo bem?");
        assertThat(r.tokensEntrada()).isEqualTo(30);
        assertThat(r.tokensSaida()).isEqualTo(4);
        assertThat(corpos.getFirst()).contains("\"stream\":true").contains("\"include_usage\":true");
    }

    @Test
    void streamingComErroDeStatusLancaErroTipado() {
        statusDaResposta = 429;
        corpoDaResposta = "{\"error\":{\"code\":\"insufficient_quota\",\"message\":\"You exceeded your current quota\"}}";
        assertThatThrownBy(() -> provedor().gerarStreaming(PEDIDO, ProvedorIA.Opcoes.texto(10), t -> { }))
                .isInstanceOf(ErroProvedor.class)
                .extracting(e -> ((ErroProvedor) e).tipo()).isEqualTo(Tipo.CREDITO_ESGOTADO);
    }

    // ---------- erros ----------

    private Tipo tipoDoErro(int status, String corpo) {
        statusDaResposta = status;
        corpoDaResposta = corpo;
        return ((ErroProvedor) org.junit.jupiter.api.Assertions.assertThrows(ErroProvedor.class,
                () -> provedor().gerar(PEDIDO, ProvedorIA.Opcoes.texto(10)))).tipo();
    }

    @Test
    void cotaEsgotadaNaOpenAiEh429ComInsufficientQuota() {
        assertThat(tipoDoErro(429, "{\"error\":{\"code\":\"insufficient_quota\",\"type\":\"insufficient_quota\"}}"))
                .isEqualTo(Tipo.CREDITO_ESGOTADO);
    }

    @Test
    void limiteDeRequisicoesEh429ComOutroCodigo() {
        assertThat(tipoDoErro(429, "{\"error\":{\"code\":\"rate_limit_exceeded\",\"type\":\"requests\"}}"))
                .isEqualTo(Tipo.LIMITE_TEMPORARIO);
        assertThat(tipoDoErro(429, "")).isEqualTo(Tipo.LIMITE_TEMPORARIO);
    }

    @Test
    void outrosErrosDeCreditoOuCobranca() {
        assertThat(tipoDoErro(402, "{}")).isEqualTo(Tipo.CREDITO_ESGOTADO);
        assertThat(tipoDoErro(429, "{\"error\":{\"code\":\"billing_hard_limit_reached\"}}")).isEqualTo(Tipo.CREDITO_ESGOTADO);
    }

    @Test
    void chaveRecusadaEhAutenticacao() {
        assertThat(tipoDoErro(401, "{\"error\":{\"code\":\"invalid_api_key\"}}")).isEqualTo(Tipo.AUTENTICACAO);
        assertThat(tipoDoErro(403, "{}")).isEqualTo(Tipo.AUTENTICACAO);
    }

    @Test
    void erroDeServidorEhIndisponivel() {
        assertThat(tipoDoErro(500, "oops")).isEqualTo(Tipo.INDISPONIVEL);
        assertThat(tipoDoErro(503, "{}")).isEqualTo(Tipo.INDISPONIVEL);
        assertThat(tipoDoErro(408, "{}")).isEqualTo(Tipo.INDISPONIVEL);
    }

    @Test
    void pedidoRuimEhPedidoInvalido() {
        assertThat(tipoDoErro(400, "{\"error\":{\"code\":\"context_length_exceeded\"}}")).isEqualTo(Tipo.PEDIDO_INVALIDO);
        assertThat(tipoDoErro(404, "{}")).isEqualTo(Tipo.PEDIDO_INVALIDO);
        assertThat(tipoDoErro(422, "{}")).isEqualTo(Tipo.PEDIDO_INVALIDO);
    }

    @Test
    void respostaIlegivelEhIndisponivel() {
        corpoDaResposta = "isto não é json";
        assertThatThrownBy(() -> provedor().gerar(PEDIDO, ProvedorIA.Opcoes.texto(10)))
                .isInstanceOf(ErroProvedor.class)
                .extracting(e -> ((ErroProvedor) e).tipo()).isEqualTo(Tipo.INDISPONIVEL);
    }

    @Test
    void servidorForaDoArEhIndisponivelEAMensagemNaoTrazAUrl() {
        int porta = servidor.getAddress().getPort();
        servidor.stop(0);
        assertThatThrownBy(() -> provedor("http://127.0.0.1:" + porta + "/v1").gerar(PEDIDO, ProvedorIA.Opcoes.texto(10)))
                .isInstanceOf(ErroProvedor.class)
                .satisfies(e -> {
                    assertThat(((ErroProvedor) e).tipo()).isEqualTo(Tipo.INDISPONIVEL);
                    assertThat(e.getMessage()).doesNotContain("127.0.0.1").doesNotContain(String.valueOf(porta));
                });
    }

    // ---------- moderação ----------

    @Test
    void moderacaoDevolveSeOTextoFoiSinalizado() {
        corpoDaResposta = "{\"results\":[{\"flagged\":true}]}";
        assertThat(provedor().moderar("texto ofensivo")).isTrue();
        assertThat(caminhos).containsExactly("/v1/moderations");
        assertThat(corpos.getFirst()).contains("\"model\":\"moderacao-teste\"").contains("texto ofensivo");

        corpoDaResposta = "{\"results\":[{\"flagged\":false}]}";
        assertThat(provedor().moderar("bom dia")).isFalse();
    }

    @Test
    void moderacaoComErroLancaErroTipado() {
        statusDaResposta = 500;
        assertThatThrownBy(() -> provedor().moderar("x")).isInstanceOf(ErroProvedor.class);
    }

    // ---------- T26: a chave nunca vaza ----------

    @Test
    void chaveNaoApareceNaExcecaoMesmoSeOProvedorDevolverEla() {
        statusDaResposta = 401;
        // A OpenAI de verdade devolve "Incorrect API key provided: sk-...": o corpo pode conter a chave.
        corpoDaResposta = "{\"error\":{\"message\":\"Incorrect API key provided: " + CHAVE
                + "\",\"type\":\"invalid_request_error\",\"code\":\"invalid_api_key\"}}";

        assertThatThrownBy(() -> provedor().gerar(PEDIDO, ProvedorIA.Opcoes.texto(10)))
                .isInstanceOf(ErroProvedor.class)
                .satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain(CHAVE).doesNotContain("Incorrect API key")
                            .contains("HTTP 401").contains("invalid_api_key");
                    assertThat(e.getCause()).isNull();
                });
    }

    @Test
    void codigoDeErroEstranhoNaoEhCopiadoParaAMensagem() {
        statusDaResposta = 400;
        corpoDaResposta = "{\"error\":{\"code\":\"" + CHAVE + " com espaço\"}}";
        assertThatThrownBy(() -> provedor().gerar(PEDIDO, ProvedorIA.Opcoes.texto(10)))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(CHAVE));
    }

    @Test
    void chaveNaoApareceNosLogsNemNoStatusDoRoteador() {
        statusDaResposta = 401;
        corpoDaResposta = "{\"error\":{\"message\":\"chave " + CHAVE + " invalida\",\"code\":\"invalid_api_key\"}}";
        Logger raiz = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        raiz.addAppender(logs);
        Level nivelAntes = raiz.getLevel();
        raiz.setLevel(Level.ALL);
        try {
            var roteador = new ProvedoresIA(List.of(provedor()), Duration.ofMinutes(60), Duration.ofSeconds(30), 2, 0,
                    Clock.systemUTC());
            assertThatThrownBy(() -> roteador.gerar(PEDIDO, ProvedorIA.Opcoes.texto(10)))
                    .isInstanceOf(AgenteIndisponivelException.class)
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain(CHAVE));

            assertThat(roteador.status().toString()).doesNotContain(CHAVE);
            assertThat(logs.list).isNotEmpty();
            assertThat(logs.list).allSatisfy(ev -> {
                assertThat(ev.getFormattedMessage()).doesNotContain(CHAVE);
                assertThat(String.valueOf(ev.getThrowableProxy())).doesNotContain(CHAVE);
            });
        } finally {
            raiz.detachAppender(logs);
            raiz.setLevel(nivelAntes);
        }
    }
}
