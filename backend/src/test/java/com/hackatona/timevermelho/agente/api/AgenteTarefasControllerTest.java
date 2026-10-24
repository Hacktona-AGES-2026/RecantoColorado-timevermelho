package com.hackatona.timevermelho.agente.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.hackatona.timevermelho.agente.PromptsAgente;
import com.hackatona.timevermelho.agente.PromptsTarefas;
import com.hackatona.timevermelho.agente.categorizacao.CategorizacaoRepository;
import com.hackatona.timevermelho.agente.categorizacao.CategorizacaoService;
import com.hackatona.timevermelho.agente.categorizacao.CategorizacaoSugerida;
import com.hackatona.timevermelho.agente.chat.LimitadorUso;
import com.hackatona.timevermelho.agente.contexto.LeituraFalsa;
import com.hackatona.timevermelho.agente.protecao.ProtecaoEntrada;
import com.hackatona.timevermelho.agente.protecao.TextosAgente;
import com.hackatona.timevermelho.agente.provedores.ProvedorFalso;
import com.hackatona.timevermelho.agente.provedores.ProvedoresIA;
import com.hackatona.timevermelho.agente.provedores.RelogioFalso;
import com.hackatona.timevermelho.agente.relatorios.RelatorioIA;
import com.hackatona.timevermelho.agente.relatorios.RelatorioRepository;
import com.hackatona.timevermelho.agente.relatorios.RelatorioService;
import com.hackatona.timevermelho.api.TratamentoDeErros;
import com.jayway.jsonpath.JsonPath;

/** Os endpoints de categorização e de relatórios, de ponta a ponta no HTTP, com IA e banco de mentira. */
@SuppressWarnings("unchecked")
class AgenteTarefasControllerTest {

    RelogioFalso relogio = new RelogioFalso();
    ProvedorFalso provedor = new ProvedorFalso("a");
    Map<String, CategorizacaoSugerida> bancoCat = new HashMap<>();
    Map<String, RelatorioIA> bancoRel = new HashMap<>();
    String motivoDesligado;
    MockMvc mvc;

    @BeforeEach
    void montar() {
        provedor.aoGerar = m -> m.getFirst().conteudo().startsWith("PROMPT DE RELATORIO")
                ? "## Resumo\nA fila tem 919 pacientes."
                : "{\"categoria\":\"DUVIDA_CONSULTA\",\"subtemas\":[\"remarcação\"],\"confianca\":0.9,\"justificativa\":\"Remarcar consulta.\"}";
        mvc = montarMvc(100, 100);
    }

    MockMvc montarMvc(int catPorMinuto, int relPorMinuto) {
        var repoCat = mock(CategorizacaoRepository.class);
        when(repoCat.save(any())).thenAnswer(i -> {
            CategorizacaoSugerida s = i.getArgument(0);
            bancoCat.put(s.getId(), s);
            return s;
        });
        when(repoCat.findById(any())).thenAnswer(i -> Optional.ofNullable(bancoCat.get((String) i.getArgument(0))));
        when(repoCat.findTop50ByOrderByCriadoEmDesc()).thenAnswer(i -> new ArrayList<>(bancoCat.values()));
        when(repoCat.findTop50ByStatusRevisaoOrderByCriadoEmDesc(any())).thenAnswer(i -> bancoCat.values().stream()
                .filter(s -> s.getStatusRevisao() == i.getArgument(0)).toList());
        var repoRel = mock(RelatorioRepository.class);
        when(repoRel.save(any())).thenAnswer(i -> {
            RelatorioIA r = i.getArgument(0);
            bancoRel.put(r.getId(), r);
            return r;
        });
        when(repoRel.findById(any())).thenAnswer(i -> Optional.ofNullable(bancoRel.get((String) i.getArgument(0))));
        when(repoRel.findTop50ByOrderByCriadoEmDesc()).thenAnswer(i -> new ArrayList<>(bancoRel.values()));

        var roteador = new ProvedoresIA(List.of(provedor), Duration.ofMinutes(60), Duration.ofSeconds(30), 2, 0, relogio);
        var prompts = PromptsAgente.montar("Sistema do assistente do FilaVIVA para testes.", "Regra de negócio um: o regulador decide.", "F", true);
        var tarefas = PromptsTarefas.montar("PROMPT DE CATEGORIZACAO", "PROMPT DE RELATORIO");
        var categorizacao = new CategorizacaoService(roteador, prompts, tarefas, new ProtecaoEntrada(), repoCat,
                new LimitadorUso(catPorMinuto, relogio), () -> motivoDesligado, relogio);
        var relatorios = new RelatorioService(roteador, prompts, tarefas, new LeituraFalsa(), repoRel,
                new LimitadorUso(relPorMinuto, relogio), () -> motivoDesligado, relogio);
        return MockMvcBuilders.standaloneSetup(new CategorizacaoController(categorizacao), new RelatorioController(relatorios))
                .setControllerAdvice(new AgenteTratamentoDeErros(), new TratamentoDeErros()).build();
    }

    ResultActions postar(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    String corpo(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    String categorizar(String texto) throws Exception {
        return corpo(postar("/api/agente/categorizar", "{\"texto\":\"" + texto + "\"}").andExpect(status().isOk()));
    }

    // ---------- categorização ----------

    @Test
    void categorizarDevolveASugestaoMarcadaComoIAParaRevisao() throws Exception {
        String json = categorizar("Preciso remarcar minha consulta.");
        assertThat((String) JsonPath.read(json, "$.categoria")).isEqualTo("DUVIDA_CONSULTA");
        assertThat((String) JsonPath.read(json, "$.categoriaRotulo")).isEqualTo("Dúvida sobre consulta");
        assertThat((String) JsonPath.read(json, "$.origem")).isEqualTo("IA");
        assertThat((Boolean) JsonPath.read(json, "$.sugestaoDeIA")).isTrue();
        assertThat((String) JsonPath.read(json, "$.statusRevisao")).isEqualTo("PENDENTE");
        assertThat((String) JsonPath.read(json, "$.aviso")).contains("Não altera classe nem posição na fila");
        assertThat((List<String>) JsonPath.read(json, "$.subtemas")).containsExactly("remarcação");
    }

    @Test
    void categorizarValidaOCorpo() throws Exception {
        postar("/api/agente/categorizar", "{}").andExpect(status().isBadRequest());
        postar("/api/agente/categorizar", "{\"texto\":\"\"}").andExpect(status().isBadRequest());
        postar("/api/agente/categorizar", "{\"texto\":\"" + "x".repeat(2001) + "\"}").andExpect(status().isBadRequest());
        postar("/api/agente/categorizar", "{isso nao e json").andExpect(status().isBadRequest());
    }

    @Test
    void categorizarComEmergenciaMarcaAtencaoHumanaSemChamarAIA() throws Exception {
        String json = categorizar("estou com dor forte no peito");
        assertThat((Boolean) JsonPath.read(json, "$.atencaoHumana")).isTrue();
        assertThat((String) JsonPath.read(json, "$.origem")).isEqualTo("REGRA_FIXA");
        assertThat(provedor.chamadasGerar).isEmpty();
    }

    @Test
    void revisaoHumanaAceitarCorrigirEBloqueioDeSegundaRevisao() throws Exception {
        String id = JsonPath.read(categorizar("Preciso remarcar minha consulta."), "$.id");

        String aceita = corpo(postar("/api/agente/categorizacoes/" + id + "/revisao", "{\"acao\":\"ACEITAR\",\"autor\":\"Dra. Ana\"}")
                .andExpect(status().isOk()));
        assertThat((String) JsonPath.read(aceita, "$.statusRevisao")).isEqualTo("ACEITA");
        assertThat((String) JsonPath.read(aceita, "$.categoriaFinal")).isEqualTo("DUVIDA_CONSULTA");
        assertThat((String) JsonPath.read(aceita, "$.revisadoPor")).isEqualTo("Dra. Ana");

        postar("/api/agente/categorizacoes/" + id + "/revisao", "{\"acao\":\"ACEITAR\"}").andExpect(status().isUnprocessableContent());
    }

    @Test
    void correcaoGuardaASugestaoDaIAEACategoriaFinalDoRevisor() throws Exception {
        String id = JsonPath.read(categorizar("Preciso remarcar minha consulta."), "$.id");
        String json = corpo(postar("/api/agente/categorizacoes/" + id + "/revisao",
                "{\"acao\":\"CORRIGIR\",\"categoriaCorrigida\":\"ASSUNTO_ADMINISTRATIVO\",\"justificativa\":\"É sobre transporte\"}")
                .andExpect(status().isOk()));
        assertThat((String) JsonPath.read(json, "$.categoria")).isEqualTo("DUVIDA_CONSULTA");
        assertThat((String) JsonPath.read(json, "$.categoriaFinal")).isEqualTo("ASSUNTO_ADMINISTRATIVO");
        assertThat((String) JsonPath.read(json, "$.statusRevisao")).isEqualTo("CORRIGIDA");
    }

    @Test
    void revisaoComPedidoInvalido() throws Exception {
        String id = JsonPath.read(categorizar("texto"), "$.id");
        postar("/api/agente/categorizacoes/" + id + "/revisao", "{\"acao\":\"CORRIGIR\"}").andExpect(status().isUnprocessableContent());
        postar("/api/agente/categorizacoes/" + id + "/revisao", "{\"acao\":\"VOAR\"}").andExpect(status().isBadRequest());
        postar("/api/agente/categorizacoes/" + id + "/revisao", "{}").andExpect(status().isBadRequest());
        postar("/api/agente/categorizacoes/" + id + "/revisao",
                "{\"acao\":\"CORRIGIR\",\"categoriaCorrigida\":\"URGENTE\",\"justificativa\":\"motivo bom\"}").andExpect(status().isBadRequest());
        postar("/api/agente/categorizacoes/nao-existe/revisao", "{\"acao\":\"ACEITAR\"}").andExpect(status().isNotFound());
    }

    @Test
    void listaEBuscaDasCategorizacoes() throws Exception {
        String id = JsonPath.read(categorizar("primeiro"), "$.id");
        categorizar("segundo");
        postar("/api/agente/categorizacoes/" + id + "/revisao", "{\"acao\":\"ACEITAR\"}").andExpect(status().isOk());

        assertThat((List<?>) JsonPath.read(corpo(mvc.perform(get("/api/agente/categorizacoes")).andExpect(status().isOk())), "$")).hasSize(2);
        assertThat((List<?>) JsonPath.read(corpo(mvc.perform(get("/api/agente/categorizacoes?status=PENDENTE"))), "$")).hasSize(1);
        assertThat((List<?>) JsonPath.read(corpo(mvc.perform(get("/api/agente/categorizacoes?status=ACEITA"))), "$")).hasSize(1);
        mvc.perform(get("/api/agente/categorizacoes?status=INVENTADO")).andExpect(status().isBadRequest());
        assertThat((String) JsonPath.read(corpo(mvc.perform(get("/api/agente/categorizacoes/" + id))), "$.id")).isEqualTo(id);
        mvc.perform(get("/api/agente/categorizacoes/nao-existe")).andExpect(status().isNotFound());
    }

    // ---------- relatórios ----------

    @Test
    void gerarRelatorioGeralDevolveOTextoComCabecalhoDeIA() throws Exception {
        String json = corpo(postar("/api/agente/relatorios", "{\"tipo\":\"VISAO_GERAL\",\"solicitante\":\"Dra. Ana\"}")
                .andExpect(status().isOk()));
        assertThat((String) JsonPath.read(json, "$.texto")).startsWith("> **Gerado por IA — revisar antes de usar.**")
                .contains("A fila tem 919 pacientes.").contains("## Dados usados (lidos da aplicação)");
        assertThat((Boolean) JsonPath.read(json, "$.geradoPorIA")).isTrue();
        assertThat((String) JsonPath.read(json, "$.titulo")).isEqualTo("Relatório da fila — visão geral");
        assertThat((String) JsonPath.read(json, "$.solicitante")).isEqualTo("Dra. Ana");
        assertThat((String) JsonPath.read(json, "$.dadosUsados")).contains("Pacientes na fila: 919");
    }

    @Test
    void relatorioPorEspecialidadeEListaDeEspecialidades() throws Exception {
        var esp = corpo(mvc.perform(get("/api/agente/relatorios/especialidades")).andExpect(status().isOk()));
        assertThat((List<String>) JsonPath.read(esp, "$.especialidades")).containsExactly("Cirurgia Geral", "Urologia");

        String json = corpo(postar("/api/agente/relatorios", "{\"tipo\":\"ESPECIALIDADE\",\"especialidade\":\"Urologia\"}")
                .andExpect(status().isOk()));
        assertThat((String) JsonPath.read(json, "$.especialidade")).isEqualTo("Urologia");
    }

    @Test
    void relatorioComPedidoInvalido() throws Exception {
        postar("/api/agente/relatorios", "{}").andExpect(status().isBadRequest());
        postar("/api/agente/relatorios", "{\"tipo\":\"TUDO\"}").andExpect(status().isBadRequest());
        postar("/api/agente/relatorios", "{\"tipo\":\"ESPECIALIDADE\",\"especialidade\":\"Cardiologia\"}").andExpect(status().isUnprocessableContent());
        postar("/api/agente/relatorios", "{\"tipo\":\"ESPECIALIDADE\"}").andExpect(status().isUnprocessableContent());
        assertThat(provedor.chamadasGerar).isEmpty();
    }

    @Test
    void historicoBuscaEDownloadEmMarkdown() throws Exception {
        String json = corpo(postar("/api/agente/relatorios", "{\"tipo\":\"VISAO_GERAL\"}").andExpect(status().isOk()));
        String id = JsonPath.read(json, "$.id");
        String texto = JsonPath.read(json, "$.texto");

        assertThat((List<?>) JsonPath.read(corpo(mvc.perform(get("/api/agente/relatorios")).andExpect(status().isOk())), "$")).hasSize(1);
        String lista = corpo(mvc.perform(get("/api/agente/relatorios")));
        assertThat(lista).doesNotContain("A fila tem 919 pacientes."); // a lista não traz o texto, só o resumo
        assertThat((String) JsonPath.read(corpo(mvc.perform(get("/api/agente/relatorios/" + id))), "$.id")).isEqualTo(id);

        var download = mvc.perform(get("/api/agente/relatorios/" + id + "/download")).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"relatorio-" + id.substring(0, 8) + ".md\""))
                .andReturn().getResponse();
        assertThat(download.getContentType()).startsWith("text/markdown");
        assertThat(download.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(texto);

        mvc.perform(get("/api/agente/relatorios/nao-existe")).andExpect(status().isNotFound());
        mvc.perform(get("/api/agente/relatorios/nao-existe/download")).andExpect(status().isNotFound());
    }

    // ---------- estado e limites ----------

    @Test
    void agenteDesligadoDevolve503ComMensagemGenerica() throws Exception {
        motivoDesligado = "sem chaves de API";
        for (String url : List.of("/api/agente/categorizar", "/api/agente/relatorios")) {
            String json = url.endsWith("categorizar") ? "{\"texto\":\"oi\"}" : "{\"tipo\":\"VISAO_GERAL\"}";
            String r = corpo(postar(url, json).andExpect(status().isServiceUnavailable()));
            assertThat((String) JsonPath.read(r, "$.mensagem")).isEqualTo(TextosAgente.INDISPONIVEL).doesNotContain("chaves");
        }
    }

    @Test
    void passandoDoLimitePorMinutoDevolve429() throws Exception {
        mvc = montarMvc(1, 1);
        categorizar("um");
        postar("/api/agente/categorizar", "{\"texto\":\"dois\"}").andExpect(status().isTooManyRequests());
        postar("/api/agente/relatorios", "{\"tipo\":\"VISAO_GERAL\"}").andExpect(status().isOk());
        postar("/api/agente/relatorios", "{\"tipo\":\"VISAO_GERAL\"}").andExpect(status().isTooManyRequests());
    }
}
