package com.hackatona.timevermelho.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.hackatona.timevermelho.dominio.Solicitacao;
import com.hackatona.timevermelho.dominio.SolicitacaoRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * API de ponta a ponta com o dataset inteiro (H2 em memória). Cada teste que muda estado é desfeito
 * por {@code /api/demo/reset}, para não vazar para as outras classes que compartilham o mesmo contexto.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FilaControllerTest {

    @Autowired MockMvc mvc;
    @Autowired SolicitacaoRepository solicitacoes;

    @AfterEach
    void desfazerMudancas() throws Exception {
        mvc.perform(post("/api/demo/reset")).andExpect(status().isOk());
    }

    // ---------- helpers ----------

    private ResultActions enviar(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String corpo(String url) throws Exception {
        return mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String corpo(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    private <T> T ler(String json, String path) {
        return JsonPath.read(json, path);
    }

    private String primeiro(String url) throws Exception {
        return ler(corpo(url), "$.itens[0].solicitacaoId");
    }

    private String pacienteNaoOncologicoClasseD() throws Exception {
        return primeiro("/api/fila?classe=D&oncologico=false");
    }

    private String evento(String id, String tipo) {
        return """
                {"solicitacaoId":"%s","tipo":"%s","resultado":"observacao","descricao":"Evento de teste"}"""
                .formatted(id, tipo);
    }

    private String ajuste(String id, String tipo, String classeNova, String justificativa) {
        String classe = classeNova == null ? "null" : "\"" + classeNova + "\"";
        return """
                {"solicitacaoId":"%s","tipo":"%s","classeNova":%s,"justificativa":"%s","autor":"teste"}"""
                .formatted(id, tipo, classe, justificativa);
    }

    private String idDeSaidaSugeridaNaoOncologica() throws Exception {
        List<String> ids = ler(corpo("/api/alertas"), "$.saidas[?(@.oncologico==false)].solicitacaoId");
        assertThat(ids).as("o dataset precisa ter uma saída sugerida de não oncológico").isNotEmpty();
        return ids.getFirst();
    }

    // ---------- status ----------

    @Test
    void statusMostraAppEDataDeReferencia() throws Exception {
        String r = corpo("/api/status");
        assertThat((String) ler(r, "$.app")).isEqualTo("filaviva");
        assertThat((String) ler(r, "$.dataReferencia")).isEqualTo("2026-10-23");
        assertThat((Boolean) ler(r, "$.datasetEncontrado")).isTrue();
    }

    // ---------- GET /api/fila ----------

    @Test
    void filaTrazPrimeiraPaginaComResumoPorClasse() throws Exception {
        String r = corpo("/api/fila");
        assertThat((List<?>) ler(r, "$.itens")).hasSize(50);
        assertThat((Integer) ler(r, "$.pagina")).isZero();
        assertThat((Integer) ler(r, "$.tamanho")).isEqualTo(50);
        assertThat(((Number) ler(r, "$.total")).intValue()).isGreaterThan(850);
        assertThat((java.util.Map<String, ?>) ler(r, "$.porClasse")).containsKeys("A1", "A2", "B", "C", "D");
        assertThat((List<?>) ler(r, "$.especialidades")).isNotEmpty();
        assertThat((Integer) ler(r, "$.itens[0].posicao")).isEqualTo(1);
        assertThat((String) ler(r, "$.itens[0].justificativa")).isNotBlank();
    }

    @Test
    void filaVemOrdenadaPorPosicao() throws Exception {
        List<Integer> posicoes = ler(corpo("/api/fila?tamanho=100"), "$.itens[*].posicao");
        assertThat(posicoes).isSortedAccordingTo(Integer::compare);
        assertThat(posicoes.getFirst()).isEqualTo(1);
        assertThat(posicoes.getLast()).isEqualTo(100);
    }

    @Test
    void filtroDeClasseSoDevolveEssaClasse() throws Exception {
        List<String> classes = ler(corpo("/api/fila?classe=A1&tamanho=500"), "$.itens[*].classe");
        assertThat(classes).isNotEmpty().allMatch("A1"::equals);
    }

    @Test
    void filtroOncologicoSoDevolveOncologicos() throws Exception {
        List<Boolean> onco = ler(corpo("/api/fila?oncologico=true&tamanho=500"), "$.itens[*].oncologico");
        assertThat(onco).isNotEmpty().allMatch(Boolean::booleanValue);
    }

    @Test
    void filtroDeEspecialidade() throws Exception {
        String esp = ler(corpo("/api/fila"), "$.itens[0].especialidade");
        List<String> doFiltro = ler(corpo("/api/fila?tamanho=500&especialidade=" + java.net.URLEncoder.encode(esp, "UTF-8")),
                "$.itens[*].especialidade");
        assertThat(doFiltro).isNotEmpty().allMatch(esp::equals);
    }

    @Test
    void buscaPorNomeIgnoraAcentoECaixa() throws Exception {
        String nome = ler(corpo("/api/fila"), "$.itens[0].nome");
        String termo = nome.split(" ")[0].toUpperCase();
        String r = corpo("/api/fila?busca=" + java.net.URLEncoder.encode(termo, "UTF-8"));
        assertThat(((Number) ler(r, "$.total")).intValue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void buscaPorCidAceitaComEPontoESemPonto() throws Exception {
        String cid = ler(corpo("/api/fila"), "$.itens[0].cid"); // ex.: C50.4 ou K40.9
        int comPonto = ((Number) ler(corpo("/api/fila?busca=" + cid), "$.total")).intValue();
        int semPonto = ((Number) ler(corpo("/api/fila?busca=" + cid.replace(".", "")), "$.total")).intValue();
        assertThat(comPonto).isGreaterThanOrEqualTo(1).isEqualTo(semPonto);
    }

    @Test
    void statusTodosIncluiQuemEstaForaDaFilaAtiva() throws Exception {
        int ativos = ((Number) ler(corpo("/api/fila"), "$.total")).intValue();
        int todos = ((Number) ler(corpo("/api/fila?status=TODOS"), "$.total")).intValue();
        assertThat(todos).isGreaterThanOrEqualTo(ativos);
    }

    @Test
    void filtroDeStatusSaidaSugeridaSoDevolveEssesPacientes() throws Exception {
        List<String> status = ler(corpo("/api/fila?status=SAIDA_SUGERIDA&tamanho=500"), "$.itens[*].status");
        assertThat(status).isNotEmpty().allMatch("SAIDA_SUGERIDA"::equals);
    }

    @Test
    void paginacaoPulaOsItensCertosELimitaOTamanhoEm500() throws Exception {
        String pag = corpo("/api/fila?pagina=1&tamanho=10");
        assertThat((Integer) ler(pag, "$.itens[0].posicao")).isEqualTo(11);
        assertThat((List<?>) ler(pag, "$.itens")).hasSize(10);

        assertThat((Integer) ler(corpo("/api/fila?tamanho=9999"), "$.tamanho")).isEqualTo(500);
        assertThat((Integer) ler(corpo("/api/fila?tamanho=0"), "$.tamanho")).isEqualTo(1);
        assertThat((Integer) ler(corpo("/api/fila?pagina=-5"), "$.pagina")).isZero();
    }

    // ---------- GET /api/pacientes/{id} ----------

    @Test
    void pacienteTrazClassificacaoComponentesESinais() throws Exception {
        String id = primeiro("/api/fila");
        String r = corpo("/api/pacientes/" + id);
        assertThat((String) ler(r, "$.item.solicitacaoId")).isEqualTo(id);
        assertThat((List<?>) ler(r, "$.componentes")).hasSize(6);
        assertThat((String) ler(r, "$.classificacao.regra")).isNotBlank();
        assertThat((List<?>) ler(r, "$.classificacao.determinantes")).isNotNull();
        assertThat((String) ler(r, "$.dados.procedimento")).isNotBlank();
        assertThat((List<?>) ler(r, "$.ajustes")).isEmpty();
    }

    @Test
    void pacienteInexistenteDevolve404ComMensagem() throws Exception {
        String r = corpo(mvc.perform(get("/api/pacientes/SOL-NAO-EXISTE")).andExpect(status().isNotFound()));
        assertThat((String) ler(r, "$.mensagem")).contains("SOL-NAO-EXISTE").contains("não encontrada");
    }

    @Test
    void solicitacaoDuplicadaAbreAPrincipal() throws Exception {
        Solicitacao dup = solicitacoes.findBySituacao(Solicitacao.SituacaoCadastro.DUPLICADA).getFirst();
        String r = corpo("/api/pacientes/" + dup.getId());
        assertThat((String) ler(r, "$.item.solicitacaoId")).isEqualTo(dup.getDuplicadaDe());
        assertThat((List<String>) ler(r, "$.duplicatas")).contains(dup.getId());
    }

    // ---------- POST /api/eventos ----------

    @Test
    void eventoNovoRecalculaAFilaERegistraAlertaEHistorico() throws Exception {
        String id = pacienteNaoOncologicoClasseD();
        String r = corpo(enviar("/api/eventos", evento(id, "pronto_socorro")).andExpect(status().isOk()));
        assertThat((String) ler(r, "$.solicitacaoId")).isEqualTo(id);
        assertThat((String) ler(r, "$.statusDepois")).isEqualTo("ATIVA");
        assertThat((String) ler(r, "$.mensagem")).isNotBlank();

        List<String> tipos = ler(corpo("/api/alertas"), "$.recentes[*].tipo");
        assertThat(tipos).contains("EVENTO_NOVO");

        List<Boolean> sinteticos = ler(corpo("/api/pacientes/" + id), "$.eventos[*].sintetico");
        assertThat(sinteticos).contains(true);
    }

    @Test
    void eventoGraveMudaAClasseEGeraAlertaDeMudanca() throws Exception {
        String id = pacienteNaoOncologicoClasseD();
        String r = corpo(enviar("/api/eventos", """
                {"solicitacaoId":"%s","tipo":"pronto_socorro","resultado":"observacao","descricao":"Hernia encarcerada, dor intensa"}"""
                .formatted(id)).andExpect(status().isOk()));
        String depois = ler(r, "$.classeDepois");
        if (!depois.equals(ler(r, "$.classeAntes"))) {
            List<String> tipos = ler(corpo("/api/alertas"), "$.recentes[*].tipo");
            assertThat(tipos).contains("MUDANCA_CLASSE");
        }
        assertThat((Integer) ler(r, "$.posicaoDepois")).isLessThanOrEqualTo((Integer) ler(r, "$.posicaoAntes"));
    }

    @Test
    void tipoDeEventoDesconhecidoEh422ENaoGravaNada() throws Exception {
        String id = pacienteNaoOncologicoClasseD();
        String r = corpo(enviar("/api/eventos", evento(id, "xpto")).andExpect(status().isUnprocessableContent()));
        assertThat((String) ler(r, "$.mensagem")).isEqualTo("Tipo de evento desconhecido: xpto");
        assertThat((List<?>) ler(corpo("/api/alertas"), "$.recentes")).isEmpty();
    }

    @Test
    void eventoSemTipoEh400ComONomeDoCampo() throws Exception {
        String r = corpo(enviar("/api/eventos", "{\"solicitacaoId\":\"X\"}").andExpect(status().isBadRequest()));
        assertThat((String) ler(r, "$.mensagem")).contains("tipo");
    }

    @Test
    void corpoQueNaoEJsonEh400() throws Exception {
        String r = corpo(enviar("/api/eventos", "{isso nao e json").andExpect(status().isBadRequest()));
        assertThat((String) ler(r, "$.mensagem")).isEqualTo("Corpo da requisição inválido");
    }

    @Test
    void eventoDePacienteInexistenteEh404() throws Exception {
        enviar("/api/eventos", evento("SOL-NAO-EXISTE", "biopsia")).andExpect(status().isNotFound());
    }

    // ---------- POST /api/ajustes ----------

    @Test
    void justificativaCurtaEhRecusadaComMensagemClara() throws Exception {
        String id = pacienteNaoOncologicoClasseD();
        String r = corpo(enviar("/api/ajustes", ajuste(id, "CONFIRMAR_CLASSE", null, "curta")).andExpect(status().isBadRequest()));
        assertThat((String) ler(r, "$.mensagem")).contains("justificativa").contains("10 caracteres");
    }

    @Test
    void justificativaEmBrancoEhRecusada() throws Exception {
        String id = pacienteNaoOncologicoClasseD();
        enviar("/api/ajustes", ajuste(id, "CONFIRMAR_CLASSE", null, "")).andExpect(status().isBadRequest());
    }

    @Test
    void naoDeixaRebaixarOncologicoAbaixoDeA2() throws Exception {
        String id = primeiro("/api/fila?oncologico=true");
        String r = corpo(enviar("/api/ajustes", ajuste(id, "ALTERAR_CLASSE", "D", "Tentativa de rebaixar o paciente"))
                .andExpect(status().isUnprocessableContent()));
        assertThat((String) ler(r, "$.mensagem")).contains("Regra de ouro");
        assertThat((List<?>) ler(corpo("/api/pacientes/" + id), "$.ajustes")).isEmpty();
    }

    @Test
    void oncologicoPodeSerPromovidoParaA1() throws Exception {
        String id = primeiro("/api/fila?oncologico=true&classe=A2");
        String r = corpo(enviar("/api/ajustes", ajuste(id, "ALTERAR_CLASSE", "A1", "Piora clinica observada em consulta"))
                .andExpect(status().isOk()));
        assertThat((String) ler(r, "$.classeDepois")).isEqualTo("A1");
    }

    @Test
    void confirmarClasseGravaNoHistoricoComAutor() throws Exception {
        String id = pacienteNaoOncologicoClasseD();
        enviar("/api/ajustes", ajuste(id, "CONFIRMAR_CLASSE", null, "Classe confirmada apos revisao")).andExpect(status().isOk());
        String p = corpo("/api/pacientes/" + id);
        assertThat((List<?>) ler(p, "$.ajustes")).hasSize(1);
        assertThat((String) ler(p, "$.ajustes[0].tipo")).isEqualTo("CONFIRMAR_CLASSE");
        assertThat((String) ler(p, "$.ajustes[0].autor")).isEqualTo("teste");
        assertThat((String) ler(p, "$.ajustes[0].justificativa")).isEqualTo("Classe confirmada apos revisao");
        assertThat((List<String>) ler(corpo("/api/alertas"), "$.recentes[*].tipo")).contains("AJUSTE_REGULADOR");
    }

    @Test
    void alterarClasseMarcaComoAjustadaEMantemAClasseCalculada() throws Exception {
        String id = pacienteNaoOncologicoClasseD();
        String r = corpo(enviar("/api/ajustes", ajuste(id, "ALTERAR_CLASSE", "B", "Dor limita o trabalho do paciente"))
                .andExpect(status().isOk()));
        assertThat((String) ler(r, "$.classeAntes")).isEqualTo("D");
        assertThat((String) ler(r, "$.classeDepois")).isEqualTo("B");

        String p = corpo("/api/pacientes/" + id);
        assertThat((String) ler(p, "$.item.classe")).isEqualTo("B");
        assertThat((String) ler(p, "$.item.classeCalculada")).isEqualTo("D");
        assertThat((Boolean) ler(p, "$.item.ajustada")).isTrue();
    }

    @Test
    void alterarClasseSemClasseNovaOuComClasseInvalidaEh422() throws Exception {
        String id = pacienteNaoOncologicoClasseD();
        String sem = corpo(enviar("/api/ajustes", ajuste(id, "ALTERAR_CLASSE", null, "Justificativa suficiente"))
                .andExpect(status().isUnprocessableContent()));
        assertThat((String) ler(sem, "$.mensagem")).isEqualTo("Informe a classe nova");

        String invalida = corpo(enviar("/api/ajustes", ajuste(id, "ALTERAR_CLASSE", "Z", "Justificativa suficiente"))
                .andExpect(status().isUnprocessableContent()));
        assertThat((String) ler(invalida, "$.mensagem")).isEqualTo("Classe inválida: Z");
    }

    @Test
    void tipoDeAjusteQueNaoExisteEh400() throws Exception {
        String id = pacienteNaoOncologicoClasseD();
        enviar("/api/ajustes", ajuste(id, "VOAR", null, "Justificativa suficiente")).andExpect(status().isBadRequest());
    }

    @Test
    void recusarSaidaSemSugestaoEh422() throws Exception {
        String id = pacienteNaoOncologicoClasseD();
        String r = corpo(enviar("/api/ajustes", ajuste(id, "RECUSAR_SAIDA", null, "Paciente quer continuar na fila"))
                .andExpect(status().isUnprocessableContent()));
        assertThat((String) ler(r, "$.mensagem")).isEqualTo("Não há sugestão de saída para recusar");
    }

    @Test
    void confirmarSaidaTiraOPacienteDaFilaAtiva() throws Exception {
        String id = idDeSaidaSugeridaNaoOncologica();
        String r = corpo(enviar("/api/ajustes", ajuste(id, "CONFIRMAR_SAIDA", null, "Paciente desistiu, confirmado por telefone"))
                .andExpect(status().isOk()));
        assertThat((String) ler(r, "$.statusDepois")).isEqualTo("SAIDA_CONFIRMADA");
        assertThat(((Number) ler(corpo("/api/fila?status=ATIVA&busca=" + id), "$.total")).intValue()).isZero();
        assertThat(((Number) ler(corpo("/api/fila?status=SAIDA_CONFIRMADA&busca=" + id), "$.total")).intValue()).isEqualTo(1);
    }

    @Test
    void recusarSaidaMantemOPacienteNaFila() throws Exception {
        String id = idDeSaidaSugeridaNaoOncologica();
        String r = corpo(enviar("/api/ajustes", ajuste(id, "RECUSAR_SAIDA", null, "Paciente confirmou que quer operar"))
                .andExpect(status().isOk()));
        assertThat((String) ler(r, "$.statusDepois")).isEqualTo("ATIVA");
        assertThat(((Number) ler(corpo("/api/fila?status=ATIVA&busca=" + id), "$.total")).intValue()).isEqualTo(1);
    }

    // ---------- POST /api/simulacao ----------

    @Test
    void simulacaoDevolveAntesEDepoisSemMudarAFilaOficial() throws Exception {
        String filaAntes = corpo("/api/fila?tamanho=200");
        String id = pacienteNaoOncologicoClasseD();
        String r = corpo(enviar("/api/simulacao", """
                {"solicitacaoId":"%s","dor":9,"limitacao":"grave",
                 "eventos":[{"tipo":"internacao","resultado":"internado","valorNumerico":5}],
                 "textoAdicional":"Episodio de encarceramento"}""".formatted(id)).andExpect(status().isOk()));
        assertThat((String) ler(r, "$.mensagem")).startsWith("Simulação (nada foi salvo)");
        assertThat((String) ler(r, "$.antes.solicitacaoId")).isEqualTo(id);
        assertThat((String) ler(r, "$.depois.classe")).isEqualTo("A1");

        assertThat(corpo("/api/fila?tamanho=200")).isEqualTo(filaAntes);
        assertThat((List<?>) ler(corpo("/api/alertas"), "$.recentes")).isEmpty();
        assertThat((List<?>) ler(corpo("/api/pacientes/" + id), "$.ajustes")).isEmpty();
    }

    @Test
    void simulacaoComDorForaDaEscalaEh400() throws Exception {
        String id = pacienteNaoOncologicoClasseD();
        String r = corpo(enviar("/api/simulacao", "{\"solicitacaoId\":\"%s\",\"dor\":11}".formatted(id))
                .andExpect(status().isBadRequest()));
        assertThat((String) ler(r, "$.mensagem")).contains("dor");
    }

    @Test
    void simulacaoDePacienteInexistenteEh404() throws Exception {
        enviar("/api/simulacao", "{\"solicitacaoId\":\"SOL-NAO-EXISTE\"}").andExpect(status().isNotFound());
    }

    @Test
    void simulacaoDePesosValidaEDevolveMovimentos() throws Exception {
        String r = corpo(enviar("/api/simulacao/pesos",
                "{\"diagnostico\":10,\"piora\":10,\"dor\":10,\"limitacao\":10,\"espera\":60,\"risco\":0}")
                .andExpect(status().isOk()));
        assertThat((Boolean) ler(r, "$.regraDeOuroRespeitada")).isTrue();
        assertThat((List<?>) ler(r, "$.topo")).hasSize(20);
    }

    @Test
    void pesosZeradosEh422ENegativosSao400() throws Exception {
        String zero = corpo(enviar("/api/simulacao/pesos",
                "{\"diagnostico\":0,\"piora\":0,\"dor\":0,\"limitacao\":0,\"espera\":0,\"risco\":0}")
                .andExpect(status().isUnprocessableContent()));
        assertThat((String) ler(zero, "$.mensagem")).contains("Pelo menos um peso");

        enviar("/api/simulacao/pesos",
                "{\"diagnostico\":-1,\"piora\":1,\"dor\":1,\"limitacao\":1,\"espera\":1,\"risco\":1}")
                .andExpect(status().isBadRequest());
    }

    // ---------- alertas, indicadores e export ----------

    @Test
    void alertasTemAsTresListas() throws Exception {
        String r = corpo("/api/alertas");
        assertThat((List<?>) ler(r, "$.recentes")).isNotNull();
        assertThat((List<?>) ler(r, "$.reavaliacao")).isNotEmpty();
        assertThat((List<?>) ler(r, "$.saidas")).isNotEmpty();
    }

    @Test
    void indicadoresComparamAntesEDepois() throws Exception {
        String r = corpo("/api/indicadores");
        assertThat(((Number) ler(r, "$.totalAtivos")).intValue()).isGreaterThan(850);
        assertThat((List<?>) ler(r, "$.comparacoes")).hasSize(7);
        assertThat((List<?>) ler(r, "$.faixas")).hasSize(10);
        List<String> depois = ler(r, "$.comparacoes[?(@.indicador=='Oncológicos abaixo de um paciente sem urgência')].depois");
        assertThat(depois).containsExactly("0");
    }

    @Test
    void exportDevolveCsvParaDownload() throws Exception {
        var r = mvc.perform(get("/api/fila/export.csv")).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"fila_priorizada.csv\""))
                .andReturn().getResponse();
        assertThat(r.getContentType()).startsWith("text/csv");
        assertThat(r.getContentAsString()).startsWith("solicitacao_id,posicao,justificativa\n");
    }

    // ---------- demo ----------

    @Test
    void cenarioDaDemoRegistraEventosEResetVoltaAoOriginal() throws Exception {
        String filaOriginal = corpo("/api/fila?tamanho=500");

        String impactos = corpo(enviar("/api/demo/cenario", "").andExpect(status().isOk()));
        List<?> lista = ler(impactos, "$");
        assertThat(lista).isNotEmpty().hasSizeLessThanOrEqualTo(4);
        assertThat((List<?>) ler(corpo("/api/alertas"), "$.recentes")).isNotEmpty();
        assertThat(corpo("/api/fila?tamanho=500")).isNotEqualTo(filaOriginal);

        String reset = corpo(enviar("/api/demo/reset", "").andExpect(status().isOk()));
        assertThat((Boolean) ler(reset, "$.ok")).isTrue();
        assertThat((List<?>) ler(corpo("/api/alertas"), "$.recentes")).isEmpty();
        assertThat(corpo("/api/fila?tamanho=500")).isEqualTo(filaOriginal);
    }

    @Test
    void resetDesfazAjustesDoRegulador() throws Exception {
        String id = pacienteNaoOncologicoClasseD();
        enviar("/api/ajustes", ajuste(id, "ALTERAR_CLASSE", "B", "Dor limita o trabalho do paciente")).andExpect(status().isOk());
        assertThat((Boolean) ler(corpo("/api/pacientes/" + id), "$.item.ajustada")).isTrue();

        enviar("/api/demo/reset", "").andExpect(status().isOk());
        String p = corpo("/api/pacientes/" + id);
        assertThat((Boolean) ler(p, "$.item.ajustada")).isFalse();
        assertThat((List<?>) ler(p, "$.ajustes")).isEmpty();
    }

    // ---------- CORS ----------

    @Test
    void corsAceitaOOrigemConfigurada() throws Exception {
        mvc.perform(options("/api/fila").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void corsRecusaOutraOrigem() throws Exception {
        mvc.perform(options("/api/fila").header("Origin", "http://site-malicioso.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/fila").header("Origin", "http://site-malicioso.example"))
                .andExpect(status().isForbidden());
    }
}
