package com.hackatona.timevermelho.agente.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.hackatona.timevermelho.agente.categorizacao.CategorizacaoRepository;
import com.hackatona.timevermelho.agente.provedores.Mensagem;
import com.hackatona.timevermelho.agente.provedores.ProvedorIA;
import com.hackatona.timevermelho.agente.provedores.ProvedoresIA;
import com.hackatona.timevermelho.agente.relatorios.RelatorioRepository;
import com.hackatona.timevermelho.dominio.AjusteReguladorRepository;
import com.hackatona.timevermelho.dominio.AlertaRepository;
import com.hackatona.timevermelho.dominio.EventoClinicoRepository;
import com.hackatona.timevermelho.dominio.SolicitacaoRepository;
import com.hackatona.timevermelho.servico.FilaService;
import com.jayway.jsonpath.JsonPath;

/**
 * T39 e T31, com o dataset inteiro e um provedor de IA de mentira: chat, categorização e relatórios não mudam a
 * fila nem nenhuma tabela oficial, gravam só nas tabelas do agente, usam números reais e não deixam nenhum nome
 * de paciente chegar à IA nem aos relatórios. Banco H2 próprio para não misturar com os outros testes.
 */
@SpringBootTest(properties = {"agente.exigir-regras=false",
        "agente.limites.mensagens-por-minuto=100000", "agente.abuso.relatorios-por-minuto=100000",
        "agente.abuso.categorizacoes-por-minuto=100000",
        "spring.datasource.url=jdbc:h2:mem:agentetarefas;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class AgenteNaoAlteraDadosOficiaisTest {

    @MockitoBean ProvedoresIA provedores;

    @Autowired MockMvc mvc;
    @Autowired FilaService fila;
    @Autowired SolicitacaoRepository solicitacoes;
    @Autowired EventoClinicoRepository eventos;
    @Autowired AjusteReguladorRepository ajustes;
    @Autowired AlertaRepository alertas;
    @Autowired CategorizacaoRepository categorizacoes;
    @Autowired RelatorioRepository relatorios;

    final List<List<Mensagem>> enviadosAoModelo = new ArrayList<>();

    @BeforeEach
    void provedorDeMentira() {
        enviadosAoModelo.clear();
        when(provedores.temProvedores()).thenReturn(true);
        when(provedores.algumSuportaModeracao()).thenReturn(true);
        when(provedores.moderar(any())).thenReturn(false);
        when(provedores.gerar(any(), any())).thenAnswer(inv -> {
            List<Mensagem> msgs = inv.getArgument(0);
            enviadosAoModelo.add(msgs);
            String sistema = msgs.getFirst().conteudo();
            String resposta = sistema.startsWith("Você organiza textos")
                    ? "{\"categoria\":\"USO_DA_APLICACAO\",\"subtemas\":[\"fila\"],\"confianca\":0.8,\"justificativa\":\"Uso da fila.\"}"
                    : sistema.startsWith("Você escreve relatórios")
                            ? "## Resumo\nRelatório de teste."
                            : "{\"categoria\":\"APLICACAO\",\"motivo\":\"ok\"}";
            return new ProvedorIA.Resposta(resposta, 10, 5);
        });
        when(provedores.gerarStreaming(any(), any(), any())).thenAnswer(inv -> {
            List<Mensagem> msgs = inv.getArgument(0);
            enviadosAoModelo.add(msgs);
            Consumer<String> aoReceber = inv.getArgument(2);
            aoReceber.accept("Resposta de teste do chat.");
            return new ProvedorIA.Resposta("Resposta de teste do chat.", 20, 6);
        });
    }

    private String corpo(String url) throws Exception {
        return mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String postar(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String chat(String mensagem) throws Exception {
        MvcResult r = mvc.perform(post("/api/agente/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mensagem\":\"" + mensagem + "\"}")).andExpect(request().asyncStarted()).andReturn();
        r.getAsyncResult(5000);
        return r.getResponse().getContentAsString();
    }

    @Test
    void agenteProntoComOProvedorDeMentira() throws Exception {
        assertThat((Boolean) JsonPath.read(corpo("/api/agente/status"), "$.pronto")).isTrue();
    }

    @Test
    void chatCategorizacaoERelatoriosNaoAlteramNadaOficialEGravamSoNasTabelasDoAgente() throws Exception {
        // foto do estado oficial antes
        String filaAntes = corpo("/api/fila?tamanho=500");
        String alertasAntes = corpo("/api/alertas");
        long sol = solicitacoes.count(), ev = eventos.count(), aj = ajustes.count(), al = alertas.count();
        var snapshotAntes = fila.atual();
        long cats = categorizacoes.count(), rels = relatorios.count();

        // usa o agente de todas as formas
        assertThat(chat("quantos pacientes estão na fila por classe?")).contains("event:fim");
        String cat = postar("/api/agente/categorizar", "{\"texto\":\"Como uso a tela da fila?\"}");
        postar("/api/agente/categorizacoes/" + JsonPath.read(cat, "$.id") + "/revisao", "{\"acao\":\"ACEITAR\"}");
        postar("/api/agente/relatorios", "{\"tipo\":\"VISAO_GERAL\"}");
        String esp = JsonPath.read(corpo("/api/agente/relatorios/especialidades"), "$.especialidades[0]");
        postar("/api/agente/relatorios", "{\"tipo\":\"ESPECIALIDADE\",\"especialidade\":\"" + esp + "\"}");

        // nada oficial mudou
        assertThat(corpo("/api/fila?tamanho=500")).isEqualTo(filaAntes);
        assertThat(corpo("/api/alertas")).isEqualTo(alertasAntes);
        assertThat(fila.atual()).as("a fila oficial não pode ter sido recalculada").isSameAs(snapshotAntes);
        assertThat(solicitacoes.count()).isEqualTo(sol);
        assertThat(eventos.count()).isEqualTo(ev);
        assertThat(ajustes.count()).isEqualTo(aj);
        assertThat(alertas.count()).isEqualTo(al);
        // só as tabelas do agente cresceram
        assertThat(categorizacoes.count()).isEqualTo(cats + 1);
        assertThat(relatorios.count()).isEqualTo(rels + 2);
    }

    @Test
    void osNumerosDosRelatoriosEDoChatSaoOsDaFilaDeVerdade() throws Exception {
        var ativos = fila.atual().ativos().size();
        String porClasseDaFila = JsonPath.read(corpo("/api/fila?tamanho=1"), "$.porClasse.A1").toString();

        String relatorio = postar("/api/agente/relatorios", "{\"tipo\":\"VISAO_GERAL\"}");
        assertThat((String) JsonPath.read(relatorio, "$.dadosUsados")).contains("Pacientes na fila: " + ativos)
                .contains("A1 " + porClasseDaFila);

        enviadosAoModelo.clear();
        chat("quantos pacientes estão na fila por classe?");
        String enviado = enviadosAoModelo.getLast().getLast().conteudo(); // a volta de geração (a última)
        assertThat(enviado).contains("Pacientes na fila: " + ativos).contains("A1 " + porClasseDaFila);
    }

    @Test
    void nenhumNomeNemIdentificadorDePacienteChegaAIAOuAosRelatorios() throws Exception {
        String pagina = corpo("/api/fila?tamanho=200");
        List<String> nomes = JsonPath.read(pagina, "$.itens[*].nome");
        List<String> ids = JsonPath.read(pagina, "$.itens[*].solicitacaoId");
        List<String> cids = JsonPath.read(pagina, "$.itens[*].cid");
        assertThat(nomes).hasSize(200);

        enviadosAoModelo.clear();
        chat("quantos pacientes estão na fila por classe e quais as reavaliações pendentes e os indicadores?");
        String relatorio = postar("/api/agente/relatorios", "{\"tipo\":\"VISAO_GERAL\"}");
        String textoDoRelatorio = JsonPath.read(relatorio, "$.texto");

        StringBuilder tudo = new StringBuilder(textoDoRelatorio);
        enviadosAoModelo.forEach(msgs -> msgs.forEach(m -> tudo.append('\n').append(m.conteudo())));
        String visto = tudo.toString();
        assertThat(visto).contains("Pacientes na fila"); // o bloco de dados de fato foi enviado

        for (String nome : nomes) assertThat(visto).as("nome de paciente vazou").doesNotContain(nome);
        for (String id : ids) assertThat(visto).as("identificador de solicitação vazou").doesNotContain(id);
        for (String cid : cids) {
            if (cid != null && cid.length() >= 4) assertThat(visto).as("CID de paciente vazou: " + cid).doesNotContain(cid);
        }
    }

    @Test
    void relatorioPorEspecialidadeSoAceitaEspecialidadesQueExistem() throws Exception {
        mvc.perform(post("/api/agente/relatorios").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"ESPECIALIDADE\",\"especialidade\":\"Especialidade Inventada\"}"))
                .andExpect(status().isUnprocessableContent());
    }
}
