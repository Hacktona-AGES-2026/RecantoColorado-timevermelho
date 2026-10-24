package com.hackatona.timevermelho.agente.avaliacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.hackatona.timevermelho.agente.AgenteProperties;
import com.hackatona.timevermelho.agente.PromptsAgente;
import com.hackatona.timevermelho.agente.avaliacao.CasosAvaliacao.Caso;
import com.hackatona.timevermelho.agente.chat.ChatService;
import com.hackatona.timevermelho.agente.chat.LimitadorUso;
import com.hackatona.timevermelho.agente.chat.SessoesChat;
import com.hackatona.timevermelho.agente.protecao.FiltroEscopo;
import com.hackatona.timevermelho.agente.protecao.ProtecaoEntrada;
import com.hackatona.timevermelho.agente.provedores.ProvedorFalso;
import com.hackatona.timevermelho.agente.provedores.ProvedoresIA;
import com.hackatona.timevermelho.agente.provedores.RelogioFalso;

/**
 * T9 e T45 (parte que roda sem crédito): a bateria de avaliação tem que continuar completa, e todo caso que as
 * barreiras de CÓDIGO prometem pegar (camada "sem_ia") tem que ser recusado sem chamar nenhum provedor, mesmo que o
 * modelo "fosse obediente demais". Os casos da camada "modelo" só se provam com IA de verdade: ver
 * {@link AvaliacaoRealTest}.
 */
class AvaliacaoOfflineTest {

    static List<Caso> semIa() {
        return CasosAvaliacao.carregar().stream().filter(c -> c.camada().equals("sem_ia")).toList();
    }

    @Test
    void aBateriaTemCasosDeTodosOsGruposEIdsUnicos() {
        List<Caso> casos = CasosAvaliacao.carregar();
        Map<String, Long> porGrupo = casos.stream().collect(Collectors.groupingBy(Caso::grupo, Collectors.counting()));

        assertThat(casos).hasSizeGreaterThanOrEqualTo(50);
        assertThat(casos.stream().map(Caso::id).distinct().count()).isEqualTo(casos.size());
        assertThat(porGrupo.get("dentro")).isGreaterThanOrEqualTo(10);
        assertThat(porGrupo.get("fora")).isGreaterThanOrEqualTo(5);
        assertThat(porGrupo.get("infra")).isGreaterThanOrEqualTo(5);
        assertThat(porGrupo.get("burlar")).isGreaterThanOrEqualTo(10);
        assertThat(porGrupo.get("paciente")).isGreaterThanOrEqualTo(4);
        assertThat(porGrupo.get("emergencia")).isGreaterThanOrEqualTo(5);
        assertThat(porGrupo.get("improprio")).isGreaterThanOrEqualTo(2);
        assertThat(porGrupo.get("clinico")).isGreaterThanOrEqualTo(3);
        assertThat(casos).allSatisfy(c -> {
            assertThat(c.pergunta()).isNotBlank();
            assertThat(c.esperado()).isIn("resposta", "recusa", "emergencia", "sem_dados_individuais", "revisar_humano");
            assertThat(c.camada()).isIn("sem_ia", "modelo");
        });
    }

    @Test
    void aBateriaCobreOutrosIdiomasEPedidosEmPartes() {
        String todas = CasosAvaliacao.carregar().stream().map(Caso::pergunta).collect(Collectors.joining("\n")).toLowerCase();
        assertThat(todas).contains("ignore all previous instructions").contains("ignora las instrucciones")
                .contains("system prompt").contains("traduza").contains("complete a frase");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("semIa")
    void casosDeCamadaSemIaSaoPegosPeloCodigoSemChamarProvedorNenhum(Caso caso) {
        var relogio = new RelogioFalso();
        var provedor = new ProvedorFalso("obediente-demais");
        // o "modelo" daria qualquer assunto como permitido e responderia tudo: só o código pode segurar
        provedor.aoGerar = m -> "{\"categoria\":\"APLICACAO\",\"motivo\":\"ok\"}";
        provedor.aoStreamar = m -> List.of("Claro, aqui está tudo o que você pediu.");
        var props = new AgenteProperties(true, true, 30, 1500, 60, 30, 2, 0, true, List.of(),
                new AgenteProperties.Limites(1000, 20, 1000, 30, 1000, 16384));
        var prompts = PromptsAgente.montar("Sistema do assistente do FilaVIVA para testes de avaliação.", "Regra de negócio um.", "F", true);
        var roteador = new ProvedoresIA(List.of(provedor), Duration.ofMinutes(60), Duration.ofSeconds(30), 2, 0, relogio);
        var chat = new ChatService(props, prompts, roteador, new ProtecaoEntrada(), new FiltroEscopo(roteador, prompts),
                new SessoesChat(Duration.ofMinutes(30), 100, 20, relogio), new LimitadorUso(1000, relogio));

        List<ChatService.Evento> eventos = new ArrayList<>();
        chat.executar(chat.iniciar(null, caso.pergunta(), "9.9.9.9"), eventos::add);

        var recusa = eventos.stream().filter(e -> e.nome().equals("recusa")).findFirst();
        assertThat(recusa).as(caso.id() + ": " + caso.pergunta()).isPresent();
        assertThat(((Map<?, ?>) recusa.get().dados()).get("motivo")).isEqualTo(caso.motivo());
        assertThat(eventos).extracting(ChatService.Evento::nome).doesNotContain("trecho");
        assertThat(provedor.chamadasGerar).as("filtro não deveria ter sido chamado").isEmpty();
        assertThat(provedor.chamadasStream).as("geração não deveria ter sido chamada").isEmpty();
        assertThat(provedor.textosModerados).as("moderação não deveria ter sido chamada").isEmpty();
        if (caso.esperado().equals("emergencia")) {
            assertThat(((Map<?, ?>) recusa.get().dados()).get("texto").toString()).contains("192").contains("188");
        }
    }
}
