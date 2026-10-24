package com.hackatona.timevermelho.agente.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.hackatona.timevermelho.agente.AgenteProperties;
import com.hackatona.timevermelho.agente.PromptsAgente;
import com.hackatona.timevermelho.agente.chat.ChatService;
import com.hackatona.timevermelho.agente.chat.LimitadorUso;
import com.hackatona.timevermelho.agente.chat.SessoesChat;
import com.hackatona.timevermelho.agente.protecao.FiltroEscopo;
import com.hackatona.timevermelho.agente.protecao.ProtecaoEntrada;
import com.hackatona.timevermelho.agente.protecao.TextosAgente;
import com.hackatona.timevermelho.agente.provedores.ProvedorFalso;
import com.hackatona.timevermelho.agente.provedores.ProvedoresIA;
import com.hackatona.timevermelho.agente.provedores.RelogioFalso;
import com.jayway.jsonpath.JsonPath;

/** O endpoint do chat sem subir o Spring inteiro: provedores falsos, limites pequenos. */
class AgenteControllerTest {

    static final String SISTEMA = "Você é o assistente virtual do FilaVIVA, uma plataforma de apoio à fila de cirurgias.";
    static final String REGRAS = "Regra de negócio um: a decisão clínica é sempre de um profissional de saúde.";

    ExecutorService executor;
    RelogioFalso relogio;
    ProvedorFalso provedor;
    AgenteProperties props;

    @BeforeEach
    void preparar() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        relogio = new RelogioFalso();
        provedor = new ProvedorFalso("openai-1");
        props = new AgenteProperties(true, true, 30, 1500, 60, 30, 2, 0, true, List.of(),
                new AgenteProperties.Limites(100, 20, 3, 30, 1000, 16384));
    }

    @AfterEach
    void encerrar() {
        executor.close();
    }

    MockMvc mvc(ProvedorFalso... provedores) {
        var prompts = PromptsAgente.montar(SISTEMA, REGRAS, "FILTRO", true);
        var roteador = new ProvedoresIA(Arrays.asList(provedores), Duration.ofMinutes(60), Duration.ofSeconds(30), 2, 0, relogio);
        var sessoes = new SessoesChat(Duration.ofMinutes(30), 1000, 20, relogio);
        var chat = new ChatService(props, prompts, roteador, new ProtecaoEntrada(), new FiltroEscopo(roteador, prompts),
                sessoes, new LimitadorUso(props.limites().mensagensPorMinuto(), relogio));
        var controller = new AgenteController(chat, roteador, prompts, props, sessoes, executor);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AgenteTratamentoDeErros())
                .addFilters(new LimiteCorpoFilter(props))
                .build();
    }

    String corpo(String mensagem) {
        return "{\"mensagem\":\"" + mensagem + "\"}";
    }

    /** Dispara o chat, espera o fluxo terminar e devolve o texto bruto dos eventos. */
    String chat(MockMvc mvc, String json) throws Exception {
        MvcResult r = mvc.perform(post("/api/agente/chat").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(request().asyncStarted()).andReturn();
        r.getAsyncResult(5000);
        return r.getResponse().getContentAsString();
    }

    static List<String> eventos(String sse) {
        return sse.lines().filter(l -> l.startsWith("event:")).map(l -> l.substring(6).trim()).toList();
    }

    static List<String> dados(String sse) {
        return sse.lines().filter(l -> l.startsWith("data:")).map(l -> l.substring(5).trim()).toList();
    }

    // ---------- /chat ----------

    @Test
    void chatDevolveUmFluxoDeEventosComSessaoTrechosEFim() throws Exception {
        String sse = chat(mvc(provedor), corpo("como uso a tela de alertas?"));

        assertThat(eventos(sse)).startsWith("sessao").contains("trecho").endsWith("fim");
        assertThat(sse).contains("Resposta de teste.");
        String sessaoId = JsonPath.read(dados(sse).getFirst(), "$.sessaoId");
        assertThat(sessaoId).hasSize(32);
    }

    @Test
    void aRespostaDeChatTemTipoDeFluxoDeEventos() throws Exception {
        MvcResult r = mvc(provedor).perform(post("/api/agente/chat").contentType(MediaType.APPLICATION_JSON)
                .content(corpo("oi"))).andExpect(request().asyncStarted()).andReturn();
        r.getAsyncResult(5000);
        assertThat(r.getResponse().getContentType()).startsWith("text/event-stream");
    }

    @Test
    void recusaChegaComoEventoERecusaEmergenciaComOrientacao() throws Exception {
        String sse = chat(mvc(provedor), corpo("estou com dor forte no peito"));
        assertThat(eventos(sse)).containsExactly("sessao", "recusa", "fim");
        assertThat(sse).contains("SAMU (192)");
    }

    @Test
    void mensagemVaziaEh400() throws Exception {
        mvc(provedor).perform(post("/api/agente/chat").contentType(MediaType.APPLICATION_JSON).content(corpo("")))
                .andExpect(status().isBadRequest());
        mvc(provedor).perform(post("/api/agente/chat").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mensagemMaiorQueOLimiteEh400ComMensagemClara() throws Exception {
        var r = mvc(provedor).perform(post("/api/agente/chat").contentType(MediaType.APPLICATION_JSON)
                .content(corpo("x".repeat(101)))).andExpect(status().isBadRequest()).andReturn();
        assertThat((String) JsonPath.read(r.getResponse().getContentAsString(), "$.mensagem")).contains("100 caracteres");
        assertThat(provedor.chamadasGerar).isEmpty();
    }

    @Test
    void corpoGrandeDemaisEhRecusadoEm413AntesDeLer() throws Exception {
        mvc(provedor).perform(post("/api/agente/chat").contentType(MediaType.APPLICATION_JSON)
                .content(corpo("x".repeat(20_000)))).andExpect(status().isPayloadTooLarge());
        assertThat(provedor.chamadasGerar).isEmpty();
    }

    @Test
    void jsonQueNaoEJsonEh400() throws Exception {
        mvc(provedor).perform(post("/api/agente/chat").contentType(MediaType.APPLICATION_JSON).content("{isso nao e json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void agenteDesligadoDevolve503ComMensagemGenericaSemDetalheTecnico() throws Exception {
        var r = mvc().perform(post("/api/agente/chat").contentType(MediaType.APPLICATION_JSON).content(corpo("oi")))
                .andExpect(status().isServiceUnavailable()).andReturn();
        String msg = JsonPath.read(r.getResponse().getContentAsString(), "$.mensagem");
        assertThat(msg).isEqualTo(TextosAgente.INDISPONIVEL).doesNotContain("chave").doesNotContain("provedor");
    }

    @Test
    void passandoDoLimiteDevolve429() throws Exception {
        var mvc = mvc(provedor);
        for (int i = 0; i < 3; i++) chat(mvc, corpo("pergunta " + i));
        mvc.perform(post("/api/agente/chat").contentType(MediaType.APPLICATION_JSON).content(corpo("mais uma")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void cabecalhoXForwardedForNaoDeixaContornarOLimite() throws Exception {
        var mvc = mvc(provedor);
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/agente/chat").contentType(MediaType.APPLICATION_JSON).content(corpo("p" + i))
                    .header("X-Forwarded-For", "10.0.0." + i)).andExpect(request().asyncStarted());
        }
        mvc.perform(post("/api/agente/chat").contentType(MediaType.APPLICATION_JSON).content(corpo("outra"))
                .header("X-Forwarded-For", "10.9.9.9")).andExpect(status().isTooManyRequests());
    }

    @Test
    void naoExistemOutrosMetodosNemRotasParaMudarAsRegras() throws Exception {
        var mvc = mvc(provedor);
        for (String rota : List.of("/api/agente/regras", "/api/agente/prompt", "/api/agente/system-prompt",
                "/api/agente/config", "/api/agente/provedores")) {
            mvc.perform(get(rota)).andExpect(status().isNotFound());
            mvc.perform(post(rota).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().is4xxClientError());
        }
    }

    // ---------- /status ----------

    @Test
    void statusMostraProntoVersaoEProvedoresSemSegredos() throws Exception {
        var r = mvc(provedor).perform(get("/api/agente/status")).andExpect(status().isOk()).andReturn();
        String json = r.getResponse().getContentAsString();

        assertThat((Boolean) JsonPath.read(json, "$.pronto")).isTrue();
        assertThat((String) JsonPath.read(json, "$.versaoPrompt")).hasSize(12);
        assertThat((Boolean) JsonPath.read(json, "$.moderacaoAtiva")).isTrue();
        assertThat((String) JsonPath.read(json, "$.provedores[0].nome")).isEqualTo("openai-1");
        assertThat((String) JsonPath.read(json, "$.provedores[0].situacao")).isEqualTo("ATIVO");
        assertThat(json).doesNotContainIgnoringCase("chave").doesNotContainIgnoringCase("apikey")
                .doesNotContainIgnoringCase("authorization");
    }

    @Test
    void statusMostraProvedorSemCreditoEOMotivoDoDesligamento() throws Exception {
        var semCredito = ProvedorFalso.que("openai-1", com.hackatona.timevermelho.agente.provedores.ErroProvedor.Tipo.CREDITO_ESGOTADO);
        var reserva = new ProvedorFalso("openai-2");
        var mvc = mvc(semCredito, reserva);
        chat(mvc, corpo("oi"));

        String json = mvc.perform(get("/api/agente/status")).andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(json, "$.provedores[0].situacao")).isEqualTo("INDISPONIVEL");
        assertThat((String) JsonPath.read(json, "$.provedores[0].motivo")).isEqualTo("crédito esgotado");
        assertThat((String) JsonPath.read(json, "$.provedores[1].situacao")).isEqualTo("ATIVO");

        String desligado = mvc().perform(get("/api/agente/status")).andReturn().getResponse().getContentAsString();
        assertThat((Boolean) JsonPath.read(desligado, "$.pronto")).isFalse();
        assertThat((String) JsonPath.read(desligado, "$.motivoDesligado")).contains("nenhum provedor");
    }
}
