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
}
