package com.hackatona.timevermelho.agente.contexto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
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
}
