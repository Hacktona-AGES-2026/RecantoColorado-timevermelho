package com.hackatona.timevermelho.agente.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.hackatona.timevermelho.agente.AgenteProperties;
import com.jayway.jsonpath.JsonPath;

/**
 * Com a aplicação inteira no ar (sem nenhuma chave de API): o agente sobe desligado, com o motivo certo,
 * sem derrubar o resto do FilaVIVA, e o limite de corpo e o CORS valem para /api/agente.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AgenteIntegracaoTest {

    @Autowired MockMvc mvc;
    @Autowired AgenteProperties props;

    @Test
    void semChavesNemRegrasOAgenteSobeDesligadoComOMotivo() throws Exception {
        String json = mvc.perform(get("/api/agente/status")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();

        assertThat((Boolean) JsonPath.read(json, "$.pronto")).isFalse();
        // as regras de negócio já estão preenchidas (D1), então o motivo é a falta de chave
        assertThat((String) JsonPath.read(json, "$.motivoDesligado")).contains("nenhum provedor de IA configurado");
        assertThat((java.util.List<?>) JsonPath.read(json, "$.provedores")).isEmpty();
        // nenhum valor ou nome de variável de chave aparece no status
        assertThat(json).doesNotContain("sk-").doesNotContain("IA_PROVEDOR").doesNotContain("Bearer");
    }

    @Test
    void chatDesligadoDevolve503EORestanteDaAplicacaoContinuaFuncionando() throws Exception {
        mvc.perform(post("/api/agente/chat").contentType(MediaType.APPLICATION_JSON).content("{\"mensagem\":\"oi\"}"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/status")).andExpect(status().isOk());
        mvc.perform(get("/api/fila?tamanho=1")).andExpect(status().isOk());
    }

    @Test
    void propriedadesPadraoSaoSeguras() {
        assertThat(props.ativo()).isTrue();
        assertThat(props.exigirRegras()).isTrue();
        assertThat(props.moderacaoAtiva()).isTrue();
        assertThat(props.provedores()).isEmpty(); // nenhuma chave configurada no ambiente de teste
        assertThat(props.limites().maxMensagem()).isEqualTo(1000);
        assertThat(props.limites().maxCorpoBytes()).isEqualTo(16384);
    }

    @Test
    void limiteDeCorpoValeParaAplicacaoInteira() throws Exception {
        String grande = "{\"mensagem\":\"" + "x".repeat(20_000) + "\"}";
        mvc.perform(post("/api/agente/chat").contentType(MediaType.APPLICATION_JSON).content(grande))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void corsDoChatAceitaSoOFrontendConfigurado() throws Exception {
        mvc.perform(options("/api/agente/chat").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        mvc.perform(options("/api/agente/chat").header("Origin", "http://site-malicioso.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void naoHaEndpointParaEditarRegrasNemPromptComAAplicacaoRodando() throws Exception {
        for (String rota : new String[] {"/api/agente/regras", "/api/agente/prompt", "/api/agente/system-prompt"}) {
            mvc.perform(get(rota)).andExpect(status().isNotFound());
            mvc.perform(post(rota).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().is4xxClientError());
        }
    }
}
