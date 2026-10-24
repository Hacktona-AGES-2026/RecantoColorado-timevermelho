package com.hackatona.timevermelho.agente.contexto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/** Os filtros vindos da IA só valem depois de validados contra o catálogo da fila. */
class ConsultaFilaTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final LeituraAplicacao.Catalogo CATALOGO = new LeituraAplicacao.Catalogo(
            List.of("Colecistectomia videolaparoscopica", "Histerectomia", "Herniorrafia inguinal",
                    "Herniorrafia umbilical / incisional"),
            List.of("Cirurgia Geral", "Ginecologia"), List.of("Porto Alegre", "São Leopoldo"));

    ConsultaFila ler(String filtros, boolean listar) {
        return ConsultaFila.deJson(JSON.readTree(filtros), listar, CATALOGO);
    }

    @Test
    void nomesViramONomeExatoDoCatalogoSemAcentoNemCaixa() {
        var c = ler("{\"procedimentos\":[\"HISTERECTOMIA\"],\"municipios\":[\"sao leopoldo\"]}", true);

        assertThat(c.procedimentos()).containsExactly("Histerectomia");
        assertThat(c.municipios()).containsExactly("São Leopoldo");
        assertThat(c.naoEncontrados()).isEmpty();
        assertThat(c.listar()).isTrue();
    }

    @Test
    void termoParcialPegaTodosOsQueCombinam() {
        var c = ler("{\"procedimentos\":[\"herniorrafia\"]}", false);

        assertThat(c.procedimentos()).containsExactly("Herniorrafia inguinal", "Herniorrafia umbilical / incisional");
    }

    @Test
    void oQueNaoExisteNaFilaViraAvisoEmVezDeFiltroInventado() {
        var c = ler("{\"procedimentos\":[\"Transplante de coração\"]}", true);

        assertThat(c.procedimentos()).isEmpty();
        assertThat(c.naoEncontrados()).containsExactly("Transplante de coração");
    }

    @Test
    void valoresForaDoPermitidoSaoDescartados() {
        var c = ler("{\"classes\":[\"A1\",\"Z9\",\"b\"],\"cidPrefixo\":\"'; DROP TABLE\",\"sexo\":\"X\","
                + "\"idadeMin\":-5,\"idadeMax\":300,\"status\":\"APAGAR\",\"ordem\":\"NOME\",\"limite\":999999}", true);

        assertThat(c.classes()).containsExactly("A1", "B");
        assertThat(c.cidPrefixo()).isNull();
        assertThat(c.sexo()).isNull();
        assertThat(c.idadeMin()).isNull();
        assertThat(c.idadeMax()).isNull();
        assertThat(c.status()).isEqualTo(ConsultaFila.Status.ATIVA);
        assertThat(c.ordem()).isEqualTo(ConsultaFila.Ordem.POSICAO);
        assertThat(c.limite()).isEqualTo(ConsultaFila.LIMITE_MAXIMO);
    }

    @Test
    void cidValidoEBooleanosPassam() {
        var c = ler("{\"cidPrefixo\":\"c50\",\"oncologico\":true,\"acimaDoPrazo\":false,\"status\":\"saida_sugerida\"}", false);

        assertThat(c.cidPrefixo()).isEqualTo("C50");
        assertThat(c.oncologico()).isTrue();
        assertThat(c.acimaDoPrazo()).isFalse();
        assertThat(c.status()).isEqualTo(ConsultaFila.Status.SAIDA_SUGERIDA);
        assertThat(c.descricao()).contains("CID começando com C50").contains("só oncológicos")
                .contains("dentro do prazo da classe").contains("saída sugerida");
    }

    @Test
    void planejadorSoConsultaQuandoAIAPedeEComJsonValido() {
        assertThat(PlanejadorConsulta.interpretar("{\"consultar\":false}", CATALOGO)).isEmpty();
        assertThat(PlanejadorConsulta.interpretar("lixo", CATALOGO)).isEmpty();
        assertThat(PlanejadorConsulta.interpretar("{\"consultar\":true,\"listar\":true,\"filtros\":{\"especialidades\":[\"ginecologia\"]}}",
                CATALOGO)).get().satisfies(c -> {
                    assertThat(c.especialidades()).containsExactly("Ginecologia");
                    assertThat(c.listar()).isTrue();
                });
    }
}
