package com.hackatona.timevermelho.agente.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.hackatona.timevermelho.agente.provedores.Mensagem;
import com.hackatona.timevermelho.agente.provedores.ProvedorIA;
import com.hackatona.timevermelho.agente.provedores.ProvedoresIA;

/**
 * O chat por HTTP de verdade (Tomcat numa porta aleatória, cliente HTTP real): prova o que o MockMvc não prova,
 * que o fluxo chega em pedaços com os cabeçalhos certos, que o CORS libera só o frontend, e que os erros voltam em
 * JSON mesmo com Accept: text/event-stream (o 500 que só apareceu no navegador).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"agente.exigir-regras=false",
        "agente.limites.mensagens-por-minuto=100000",
        "spring.datasource.url=jdbc:h2:mem:agentehttp;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
class AgenteStreamingHttpTest {

    @MockitoBean ProvedoresIA provedores;

    @Value("${local.server.port}") int porta;

    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void provedorDeMentira() {
        when(provedores.temProvedores()).thenReturn(true);
        when(provedores.algumSuportaModeracao()).thenReturn(true);
        when(provedores.moderar(any())).thenReturn(false);
        when(provedores.gerar(any(), any()))
                .thenReturn(new ProvedorIA.Resposta("{\"categoria\":\"APLICACAO\",\"motivo\":\"ok\"}", 5, 5));
        when(provedores.gerarStreaming(any(), any(), any())).thenAnswer(inv -> {
            Consumer<String> aoReceber = inv.getArgument(2);
            for (String pedaco : List.of("A tela de Alertas ", "mostra o que mudou ", "na fila, em pedaços, para o navegador ver chegando.")) {
                aoReceber.accept(pedaco);
            }
            List<Mensagem> msgs = inv.getArgument(0);
            assertThat(msgs).isNotEmpty();
            return new ProvedorIA.Resposta("ok", 10, 10);
        });
    }

    private HttpRequest.Builder pedido(String corpo) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/api/agente/chat"))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo));
    }

    @Test
    void ofluxoChegaPorHttpComOsCabecalhosEOsEventosCertos() throws Exception {
        var resp = http.send(pedido("{\"mensagem\":\"como uso a tela de alertas?\"}")
                .header("Accept", "text/event-stream").header("Origin", "http://localhost:3000").build(),
                HttpResponse.BodyHandlers.ofLines());

        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
        assertThat(resp.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:3000");
        List<String> linhas = resp.body().toList();
        List<String> eventos = linhas.stream().filter(l -> l.startsWith("event:")).map(l -> l.substring(6).trim()).toList();
        assertThat(eventos).startsWith("sessao").endsWith("fim").contains("trecho");
        // o texto chega em mais de um pedaço (o final fica segurado até confirmar que não vazou o prompt)
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < linhas.size() - 1; i++) {
            if (linhas.get(i).startsWith("event:trecho")) {
                texto.append((String) com.jayway.jsonpath.JsonPath.read(linhas.get(i + 1).substring(5), "$.texto"));
            }
        }
        assertThat(eventos.stream().filter(e -> e.equals("trecho")).count()).isGreaterThanOrEqualTo(2);
        assertThat(texto.toString()).isEqualTo("A tela de Alertas mostra o que mudou na fila, em pedaços, para o navegador ver chegando.");
    }

    @Test
    void erroDeValidacaoEDeAgenteDesligadoVoltamEmJsonMesmoPedindoFluxoDeEventos() throws Exception {
        var vazia = http.send(pedido("{\"mensagem\":\"\"}").header("Accept", "text/event-stream").build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(vazia.statusCode()).isEqualTo(400);
        assertThat(vazia.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(vazia.body()).contains("mensagem");

        var quebrada = http.send(pedido("{isso nao e json").header("Accept", "text/event-stream").build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(quebrada.statusCode()).isEqualTo(400);
        assertThat(quebrada.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");

        when(provedores.temProvedores()).thenReturn(false); // agente desligado
        var desligado = http.send(pedido("{\"mensagem\":\"oi\"}").header("Accept", "text/event-stream").build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(desligado.statusCode()).isEqualTo(503);
        assertThat(desligado.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(desligado.body()).contains("indisponível").doesNotContain("provedor").doesNotContain("chave");
    }

    @Test
    void corsRecusaOutraOrigemTambemNoPedidoDeVerdade() throws Exception {
        var resp = http.send(pedido("{\"mensagem\":\"oi\"}").header("Origin", "http://site-malicioso.example").build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(403);
    }

    @Test
    void corpoGrandeDemaisEhRecusadoAntesDeLer() throws Exception {
        var resp = http.send(pedido("{\"mensagem\":\"" + "x".repeat(20_000) + "\"}").header("Accept", "text/event-stream").build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(413);
        assertThat(resp.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
    }
}
