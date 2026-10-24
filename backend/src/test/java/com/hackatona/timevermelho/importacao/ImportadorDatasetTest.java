package com.hackatona.timevermelho.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.hackatona.timevermelho.dominio.EventoClinico;
import com.hackatona.timevermelho.dominio.EventoClinicoRepository;
import com.hackatona.timevermelho.dominio.Solicitacao;
import com.hackatona.timevermelho.dominio.SolicitacaoRepository;
import com.hackatona.timevermelho.servico.FilaService;

/**
 * Importa um dataset minúsculo montado à mão (6 solicitações e 5 eventos) com todos os problemas que o
 * dataset real tem: CID sem ponto, grafias diferentes, solicitações duplicadas e eventos duplicados/órfãos.
 * Usa um banco H2 próprio para não misturar com o contexto das outras classes de teste.
 */
@SpringBootTest
class ImportadorDatasetTest {

    static final String CABECALHO_FILA = "solicitacao_id,paciente_id,nome_paciente,sexo,idade,municipio,uf,"
            + "distancia_hospital_km,procedimento_id,procedimento,especialidade,cid_principal,cid_descricao,"
            + "cid_secundario,asa,comorbidades,intensidade_dor_eva,limitacao_funcional,justificativa_clinica,"
            + "observacoes,data_entrada_fila,dias_em_fila,prioridade_regulacao,data_ultima_revisao_prioridade,"
            + "flag_oncologico,flag_diabetico,flag_piora_clinica,hba1c_ultima,data_hba1c_ultima,"
            + "avaliacao_pre_anestesica,exames_pre_op_validos";

    static String sol(String id, String paciente, String municipio, String proc, String cid, String desc, String dor,
                      String limitacao, String obs, String entrada, String prioridade, String flagOnco) {
        Map<String, String> c = new LinkedHashMap<>();
        c.put("solicitacao_id", id);
        c.put("paciente_id", paciente);
        c.put("nome_paciente", "Paciente " + id);
        c.put("sexo", "F");
        c.put("idade", "50");
        c.put("municipio", municipio);
        c.put("uf", "RS");
        c.put("distancia", "10");
        c.put("procedimento_id", proc);
        c.put("procedimento", proc.equals("PROC002") ? "Herniorrafia inguinal" : "Mastectomia");
        c.put("especialidade", proc.equals("PROC002") ? "Cirurgia Geral" : "Mastologia");
        c.put("cid", cid);
        c.put("cid_descricao", desc);
        c.put("cid_secundario", "");
        c.put("asa", dor == null ? "" : "2");
        c.put("comorbidades", "");
        c.put("dor", dor == null ? "" : dor);
        c.put("limitacao", limitacao);
        c.put("justificativa", "Paciente com indicacao cirurgica eletiva estavel.");
        c.put("obs", obs);
        c.put("entrada", entrada);
        c.put("dias", "100");
        c.put("prioridade", prioridade);
        c.put("revisao", "");
        c.put("onco", flagOnco);
        c.put("diabetico", "nao");
        c.put("piora", "nao");
        c.put("hba1c", "");
        c.put("data_hba1c", "");
        c.put("pre_anestesica", "realizada");
        c.put("exames", "sim");
        return String.join(",", c.values());
    }

    static void escrever(Path pasta, String arquivo, String... linhas) throws IOException {
        Files.writeString(pasta.resolve(arquivo), String.join("\n", linhas) + "\n", StandardCharsets.UTF_8);
    }

    static final Path PASTA = criarDataset();

    static Path criarDataset() {
        try {
            Path p = Files.createTempDirectory("filaviva-dataset-teste");
            escrever(p, "procedimentos.csv",
                    "procedimento_id,procedimento,especialidade,porte,risco_cirurgico,duracao_media_min,necessita_uti",
                    "PROC002,Herniorrafia inguinal,Cirurgia Geral,medio,2,60,nao",
                    "PROC011,Mastectomia,Mastologia,grande,3,120,nao");
            escrever(p, "fila_cirurgica.csv", CABECALHO_FILA,
                    // SOL-A1 e SOL-A2: mesmo paciente e procedimento → A2 é duplicada de A1 (mais antiga)
                    sol("SOL-A1", "PAC-1", "PORTO ALEGRE", "PROC002", "k40.9", "Hernia inguinal", "3", "leve", "obs A", "2026-01-10", "Média", "nao"),
                    sol("SOL-A2", "PAC-1", "Porto Alegre", "PROC002", "K40.9", "Hernia inguinal", "3", "leve", "obs B", "2026-03-01", "media", "nao"),
                    sol("SOL-B1", "PAC-2", "são leopoldo", "PROC011", "C50.4", "Neoplasia de mama", null, "leve", "", "2026-02-01", "BAIXA", "sim"),
                    // SOL-D1 (benigna, mais antiga) e SOL-D2 (câncer): o câncer não pode sumir na mescla
                    sol("SOL-D1", "PAC-4", "Canoas", "PROC002", "K40.9", "Hernia inguinal", null, "leve", "", "2026-02-01", "media", "nao"),
                    sol("SOL-D2", "PAC-4", "Canoas", "PROC002", "C18.9", "Neoplasia de colon", "5", "grave", "", "2026-04-01", "media", "nao"),
                    // CID já sem ponto e prioridade vazia
                    sol("SOL-E1", "PAC-5", "Canoas", "PROC002", "K409", "Hernia inguinal", "1", "leve", "", "2026-05-01", "", "nao"));
            escrever(p, "eventos_clinicos.csv",
                    "evento_id,solicitacao_id,paciente_id,data_evento,tipo_evento,resultado,valor_numerico,descricao,origem",
                    "EVT-1,SOL-A1,PAC-1,2026-09-01,CONTATO_TELEFONICO,Atendeu,,ligacao,call center",
                    "EVT-2,SOL-A2,PAC-1,2026-09-01,contato_telefonico,atendeu,,ligacao,call center",
                    "EVT-3,SOL-A2,PAC-1,2026-09-02,falta,exame,,faltou,ambulatorio",
                    "EVT-4,SOL-FANTASMA,PAC-9,2026-09-03,falta,exame,,sem solicitacao,ambulatorio",
                    "EVT-5,SOL-D2,PAC-4,2026-09-04,hba1c,resultado,9.8,hba1c alta,lab");
            return p;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("app.dataset.dir", PASTA::toString);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:importacao;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    }

    @Autowired SolicitacaoRepository solicitacoes;
    @Autowired EventoClinicoRepository eventos;
    @Autowired RelatorioImportacao relatorio;
    @Autowired ImportadorDataset importador;
    @Autowired FilaService fila;

    private Solicitacao sol(String id) {
        return solicitacoes.findById(id).orElseThrow();
    }

    private EventoClinico evt(String id) {
        return eventos.findById(id).orElseThrow();
    }

    // ---------- limpeza ----------

    @Test
    void cidFicaMaiusculoESemPontoComOOriginalGuardado() {
        assertThat(sol("SOL-A1").getCid()).isEqualTo("K409");
        assertThat(sol("SOL-A1").getCidOriginal()).isEqualTo("k40.9");
        assertThat(sol("SOL-E1").getCid()).isEqualTo("K409");
    }

    @Test
    void prioridadeFicaSemAcentoEMinusculaEVaziaViraNula() {
        assertThat(sol("SOL-A1").getPrioridadeManual()).isEqualTo("media");
        assertThat(sol("SOL-A1").getPrioridadeOriginal()).isEqualTo("Média");
        assertThat(sol("SOL-B1").getPrioridadeManual()).isEqualTo("baixa");
        assertThat(sol("SOL-E1").getPrioridadeManual()).isNull();
    }

    @Test
    void municipioViraTitleCase() {
        assertThat(sol("SOL-A1").getMunicipio()).isEqualTo("Porto Alegre");
        assertThat(sol("SOL-A1").getMunicipioOriginal()).isEqualTo("PORTO ALEGRE");
        assertThat(sol("SOL-B1").getMunicipio()).isEqualTo("São Leopoldo");
    }

    @Test
    void numerosVaziosViramNulosENaoZero() {
        assertThat(sol("SOL-B1").getDor()).isNull();
        assertThat(sol("SOL-B1").getAsa()).isNull();
        assertThat(sol("SOL-E1").getDor()).isEqualTo(1);
    }

    // ---------- solicitações duplicadas ----------

    @Test
    void duplicadaFicaMarcadaEApontaParaAMaisAntiga() {
        assertThat(sol("SOL-A1").getSituacao()).isEqualTo(Solicitacao.SituacaoCadastro.ATIVA);
        assertThat(sol("SOL-A2").getSituacao()).isEqualTo(Solicitacao.SituacaoCadastro.DUPLICADA);
        assertThat(sol("SOL-A2").getDuplicadaDe()).isEqualTo("SOL-A1");
        assertThat(sol("SOL-D1").getSituacao()).isEqualTo(Solicitacao.SituacaoCadastro.ATIVA);
        assertThat(sol("SOL-D2").getDuplicadaDe()).isEqualTo("SOL-D1");
    }

    @Test
    void observacoesDaDuplicataSaoJuntadasASuaPrincipal() {
        assertThat(sol("SOL-A1").getObservacoes()).isEqualTo("obs A | obs B");
    }

    @Test
    void canceDaDuplicataNaoSomeNaMescla() {
        assertThat(sol("SOL-D1").getCid()).isEqualTo("C189");
        assertThat(fila.atual().get("SOL-D1").oncologico()).isTrue();
    }

    @Test
    void dadoVazioDaPrincipalEPreenchidoPelaDuplicata() {
        assertThat(sol("SOL-D1").getDor()).isEqualTo(5);
    }

    @Test
    void soAsAtivasEntramNaFila() {
        assertThat(solicitacoes.findBySituacao(Solicitacao.SituacaoCadastro.ATIVA)).hasSize(4);
        assertThat(fila.atual().todos()).extracting(r -> r.getCaso().solicitacaoId())
                .containsExactlyInAnyOrder("SOL-A1", "SOL-B1", "SOL-D1", "SOL-E1");
    }

    // ---------- eventos ----------

    @Test
    void eventoDeDuplicataPassaParaASolicitacaoPrincipal() {
        assertThat(evt("EVT-3").getSolicitacaoId()).isEqualTo("SOL-A1");
        assertThat(evt("EVT-3").getSolicitacaoOriginal()).isEqualTo("SOL-A2");
        assertThat(evt("EVT-3").isDuplicado()).isFalse();
        assertThat(evt("EVT-5").getSolicitacaoId()).isEqualTo("SOL-D1");
    }

    @Test
    void eventoComMesmoConteudoMasOutroIdEhIgnorado() {
        assertThat(evt("EVT-1").isDuplicado()).isFalse();
        assertThat(evt("EVT-2").isDuplicado()).isTrue();
    }

    @Test
    void eventoDeSolicitacaoForaDaFilaEhOrfao() {
        assertThat(evt("EVT-4").isOrfao()).isTrue();
        assertThat(eventos.findByOrfaoFalseAndDuplicadoFalse()).extracting(EventoClinico::getId)
                .containsExactlyInAnyOrder("EVT-1", "EVT-3", "EVT-5");
    }

    @Test
    void tipoEResultadoDoEventoFicamEmMinuscula() {
        assertThat(evt("EVT-1").getTipo()).isEqualTo("contato_telefonico");
        assertThat(evt("EVT-1").getResultado()).isEqualTo("atendeu");
        assertThat(evt("EVT-5").getValor()).isEqualTo(9.8);
    }

    // ---------- relatório ----------

    @Test
    void relatorioContaCadaCorrecaoFeita() {
        Map<String, Long> r = relatorio.itens();
        assertThat(r).containsEntry("Solicitações lidas", 6L)
                .containsEntry("CID sem ponto (ex.: K409) normalizado", 1L)
                .containsEntry("Prioridade manual com grafia fora do padrão corrigida", 2L)
                .containsEntry("Prioridade manual vazia", 1L)
                .containsEntry("Município com grafia corrigida", 2L)
                .containsEntry("Dor (EVA) vazia", 2L)
                .containsEntry("Flag oncológico diverge do CID", 1L)
                .containsEntry("Solicitações duplicadas mescladas (mesmo paciente + procedimento)", 2L)
                .containsEntry("Eventos lidos", 5L)
                .containsEntry("Eventos duplicados ignorados (mesmo conteúdo, id diferente)", 1L)
                .containsEntry("Eventos órfãos ignorados (solicitação fora da fila)", 1L);
    }

    @Test
    void importarDeNovoComOBancoPreenchidoNaoDuplicaNada() {
        long antes = solicitacoes.count();
        long eventosAntes = eventos.count();
        Map<String, Long> relatorioAntes = relatorio.itens();

        importador.importar();

        assertThat(solicitacoes.count()).isEqualTo(antes).isEqualTo(6);
        assertThat(eventos.count()).isEqualTo(eventosAntes).isEqualTo(5);
        assertThat(relatorio.itens()).isEqualTo(relatorioAntes);
    }
}
