package com.hackatona.timevermelho.agente.contexto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hackatona.timevermelho.servico.FilaService;

import tools.jackson.databind.json.JsonMapper;

/** Com o dataset de verdade: a consulta devolve exatamente os pacientes da fila, e o texto para a IA não tem nomes. */
@SpringBootTest
class LeituraAplicacaoConsultaTest {

    @Autowired LeituraAplicacao leitura;
    @Autowired FilaService fila;

    @Test
    void histerectomiaTrazOsMesmosPacientesDaFilaAtivaNaOrdemDaFila() {
        var esperados = fila.atual().ativos().stream().filter(r -> "Histerectomia".equals(r.getCaso().procedimento()))
                .map(r -> r.getCaso().solicitacaoId()).toList();

        var r = leitura.consultar(ConsultaFila.doProcedimento("Histerectomia"));

        assertThat(esperados).isNotEmpty();
        assertThat(r.total()).isEqualTo(esperados.size());
        assertThat(r.linhas()).extracting(LeituraAplicacao.LinhaPaciente::solicitacaoId).containsExactlyElementsOf(esperados);
        assertThat(r.linhas()).allSatisfy(l -> {
            assertThat(l.procedimento()).isEqualTo("Histerectomia");
            assertThat(l.posicao()).isNotNull();
        });
        assertThat(r.resumo()).contains("Pacientes encontrados: " + esperados.size());
        // o texto que vai para a IA não tem nome, identificador nem CID de ninguém
        for (var l : r.linhas()) {
            assertThat(r.resumo()).doesNotContain(l.nome()).doesNotContain(l.solicitacaoId());
        }
    }

    @Test
    void contagemNaoDevolveLinhasEFiltrosSeSomam() {
        var json = JsonMapper.builder().build().readTree("{\"especialidades\":[\"Ginecologia\"],\"classes\":[\"A1\",\"A2\"]}");
        var c = ConsultaFila.deJson(json, false, leitura.catalogo());

        var r = leitura.consultar(c);

        long esperado = fila.atual().ativos().stream().filter(p -> "Ginecologia".equals(p.getCaso().especialidade()))
                .filter(p -> List.of("A1", "A2").contains(p.classe().name())).count();
        assertThat(r.total()).isEqualTo((int) esperado);
        assertThat(r.linhas()).isEmpty();
    }

    @Test
    void oCatalogoUnificaMunicipiosEscritosDeJeitosDiferentes() {
        var municipios = leitura.catalogo().municipios();

        assertThat(municipios).contains("Porto Alegre").doesNotContain("PORTO ALEGRE", "porto alegre");
        assertThat(leitura.catalogo().procedimentos()).contains("Histerectomia");
    }

    // ---------- paciente específico (o bug: o agente não conseguia buscar um paciente) ----------

    ConsultaFila pedido(String filtros) {
        return ConsultaFila.deJson(JsonMapper.builder().build().readTree(filtros), true, leitura.catalogo());
    }

    @Test
    void acharPacientePeloNomeTrazALinhaDeleEADetalheSemIdentificacaoParaAIA() {
        var esperado = fila.atual().get("SOL-2026-00489");

        var r = leitura.consultar(pedido("{\"nomes\":[\"Eliane Lopes Ramos\"]}"));

        assertThat(r.total()).isEqualTo(1);
        var linha = r.linhas().getFirst();
        assertThat(linha.solicitacaoId()).isEqualTo("SOL-2026-00489");
        assertThat(linha.nome()).isEqualTo("Eliane Lopes Ramos");
        assertThat(linha.classe()).isEqualTo(esperado.classe().name());
        assertThat(linha.posicao()).isEqualTo(esperado.getPosicao());
        assertThat(r.resumo()).contains("Paciente 1 da tabela: classe " + esperado.classe().name())
                .contains(esperado.getPosicao() + "ª posição na fila geral").contains("Histerectomia")
                .doesNotContainIgnoringCase("eliane").doesNotContain("SOL-2026-00489").doesNotContain("C54");
    }

    @Test
    void acharPeloNumeroDeSolicitacaoEAvisarQuandoONumeroNaoExiste() {
        var r = leitura.consultar(pedido("{\"solicitacoes\":[\"sol 2026 489\", \"SOL-2099-99999\"]}"));

        assertThat(r.linhas()).extracting(LeituraAplicacao.LinhaPaciente::solicitacaoId).containsExactly("SOL-2026-00489");
        assertThat(r.resumo()).contains("Números de solicitação informados que NÃO existem no dataset: 1")
                .doesNotContain("SOL-2099-99999");
    }

    @Test
    void parteDoNomeAchaTodosOsQueCombinamENomeInexistenteNaoAchaNinguem() {
        var r = leitura.consultar(pedido("{\"nomes\":[\"eliane\"]}"));
        assertThat(r.linhas()).isNotEmpty().allSatisfy(l -> assertThat(l.nome()).containsIgnoringCase("eliane"));

        var ninguem = leitura.consultar(pedido("{\"nomes\":[\"Fulano Inexistente da Silva\"]}"));
        assertThat(ninguem.total()).isZero();
        assertThat(ninguem.resumo()).contains("Pacientes encontrados: 0");
    }

    // ---------- nome não exatamente igual (caixa, título, erro de digitação) ----------

    @ParameterizedTest
    @ValueSource(strings = {"ELIANE LOPES RAMOS", "eliane lopes ramos", "eLiAnE lOpEs RaMoS", "Eliane Lópes Ramos",
            "dona ELIANE LOPES RAMOS", "Sra. Eliane Lopes Ramos"})
    void caixaAcentoETituloNaoAtrapalhamONomeExato(String nome) {
        var r = leitura.consultar(pedido("{\"nomes\":[\"" + nome + "\"]}"));

        assertThat(r.linhas()).extracting(LeituraAplicacao.LinhaPaciente::solicitacaoId).containsExactly("SOL-2026-00489");
        assertThat(r.resumo()).doesNotContain("MAIS PARECIDOS");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ELIANE LOPEZ RAMOS", "Eliane Lopes Ramoss", "elaine lopes ramos", "ELIANE LOPES RAMUS"})
    void erroDeDigitacaoPequenoAchaONomeMaisParecidoEAvisaAIA(String nome) {
        var r = leitura.consultar(pedido("{\"nomes\":[\"" + nome + "\"]}"));

        assertThat(r.linhas()).extracting(LeituraAplicacao.LinhaPaciente::solicitacaoId).contains("SOL-2026-00489");
        assertThat(r.linhas()).hasSizeLessThanOrEqualTo(3);
        assertThat(r.resumo()).contains("Nenhum nome exatamente igual").contains("MAIS PARECIDOS")
                .doesNotContainIgnoringCase("eliane");
    }

    @Test
    void comNomeExatoNaoMisturaOsParecidos() {
        // "eliane ramos" bate exato com duas pessoas: os "parecidos" não entram junto
        var r = leitura.consultar(pedido("{\"nomes\":[\"eliane ramos\"]}"));

        assertThat(r.linhas()).isNotEmpty().allSatisfy(l -> assertThat(l.nome().toLowerCase()).contains("eliane").contains("ramos"));
        assertThat(r.resumo()).doesNotContain("MAIS PARECIDOS");
    }

    // ---------- ID do paciente e duplicadas (pelo CSV original, só leitura) ----------

    @Test
    void acharPeloIdDoPacienteTrazTodasAsSolicitacoesDeleSemIdentificacaoParaAIA() {
        // PAC-00492 tem duas solicitações, de procedimentos diferentes
        var r = leitura.consultar(pedido("{\"pacientes\":[\"pac 492\"]}"));

        assertThat(r.linhas()).extracting(LeituraAplicacao.LinhaPaciente::solicitacaoId)
                .containsExactlyInAnyOrder("SOL-2025-00326", "SOL-2026-00448");
        assertThat(r.resumo()).contains("Pessoas distintas entre os encontrados (pelo ID do paciente): 1")
                .contains("No CSV original, essas pessoas têm 2 solicitação(ões), das quais 0 duplicada(s)")
                .doesNotContain("PAC-00492").doesNotContain("SOL-2025-00326").doesNotContain("SOL-2026-00448");
        for (var l : r.linhas()) assertThat(r.resumo()).doesNotContain(l.nome());
    }

    @Test
    void numeroDeUmaDuplicadaAchaAPrincipalEmVezDeDizerQueNaoExiste() {
        // SOL-2024-00044 é duplicada de SOL-2024-00003 (PAC-00087, mesmo procedimento): só a principal está na fila
        assertThat(fila.atual().get("SOL-2024-00044")).isNull();

        var r = leitura.consultar(pedido("{\"solicitacoes\":[\"SOL-2024-00044\"]}"));

        assertThat(r.linhas()).extracting(LeituraAplicacao.LinhaPaciente::solicitacaoId).containsExactly("SOL-2024-00003");
        assertThat(r.resumo()).doesNotContain("NÃO existem no dataset")
                .contains("são DUPLICADAS no CSV original").contains("já mescladas na solicitação principal")
                .contains("solicitações duplicadas mescladas nesta: 1").doesNotContain("SOL-2024-00003");
    }

    @Test
    void pacienteComDuplicadaApareceUmaVezSoEOResumoContaADuplicada() {
        var r = leitura.consultar(pedido("{\"pacientes\":[\"PAC-00087\"]}"));

        assertThat(r.linhas()).extracting(LeituraAplicacao.LinhaPaciente::solicitacaoId).containsExactly("SOL-2024-00003");
        assertThat(r.resumo()).contains("Pessoas distintas entre os encontrados (pelo ID do paciente): 1")
                .contains("No CSV original, essas pessoas têm 2 solicitação(ões), das quais 1 duplicada(s)");
    }

    @Test
    void idDePacienteInexistenteAvisaSemInventar() {
        var r = leitura.consultar(pedido("{\"pacientes\":[\"PAC-99999\"]}"));

        assertThat(r.total()).isZero();
        assertThat(r.resumo()).contains("IDs de paciente informados que NÃO existem no dataset: 1").doesNotContain("PAC-99999");
    }
}
