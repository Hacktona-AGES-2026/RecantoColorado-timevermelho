package com.hackatona.timevermelho.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class NormalizacaoTest {

    @Test
    void textoVazioOuEspacosViraNulo() {
        assertThat(Normalizacao.texto(null)).isNull();
        assertThat(Normalizacao.texto("   ")).isNull();
        assertThat(Normalizacao.texto("  oi  ")).isEqualTo("oi");
    }

    @Test
    void cidFicaSemPontoEMaiusculo() {
        assertThat(Normalizacao.cid("k40.9")).isEqualTo("K409");
        assertThat(Normalizacao.cid("C50.4")).isEqualTo("C504");
        assertThat(Normalizacao.cid("K409")).isEqualTo("K409");
        assertThat(Normalizacao.cid("")).isNull();
    }

    @Test
    void cidFormatadoVoltaAoPontoSoQuandoTemMaisDeTresCaracteres() {
        assertThat(Normalizacao.cidFormatado("K409")).isEqualTo("K40.9");
        assertThat(Normalizacao.cidFormatado("N40")).isEqualTo("N40");
        assertThat(Normalizacao.cidFormatado(null)).isNull();
    }

    @Test
    void prioridadeFicaSemAcentoEMinuscula() {
        assertThat(Normalizacao.prioridade("Média")).isEqualTo("media");
        assertThat(Normalizacao.prioridade("ALTA")).isEqualTo("alta");
        assertThat(Normalizacao.prioridade("")).isNull();
        assertThat(Normalizacao.prioridade(null)).isNull();
    }

    @Test
    void municipioViraTitleCaseComPreposicoesEmMinuscula() {
        assertThat(Normalizacao.municipio("PORTO ALEGRE")).isEqualTo("Porto Alegre");
        assertThat(Normalizacao.municipio("porto alegre")).isEqualTo("Porto Alegre");
        assertThat(Normalizacao.municipio("Santa Maria DO Herval")).isEqualTo("Santa Maria do Herval");
        assertThat(Normalizacao.municipio("  ")).isNull();
    }

    @Test
    void numerosVaziosOuInvalidosViramNulo() {
        assertThat(Normalizacao.inteiro("")).isNull();
        assertThat(Normalizacao.inteiro("abc")).isNull();
        assertThat(Normalizacao.inteiro("7")).isEqualTo(7);
        assertThat(Normalizacao.inteiro("7.0")).isEqualTo(7);
        assertThat(Normalizacao.decimal("")).isNull();
        assertThat(Normalizacao.decimal("x")).isNull();
        assertThat(Normalizacao.decimal("9,8")).isEqualTo(9.8);
        assertThat(Normalizacao.decimal("9.8")).isEqualTo(9.8);
    }

    @Test
    void dataEMinusculoTratamVazio() {
        assertThat(Normalizacao.data("")).isNull();
        assertThat(Normalizacao.data("2026-10-23")).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(Normalizacao.minusculo("SIM")).isEqualTo("sim");
        assertThat(Normalizacao.minusculo(null)).isNull();
    }

    @Test
    void semAcentoRemoveDiacriticos() {
        assertThat(Normalizacao.semAcento("Hérnia são João")).isEqualTo("Hernia sao Joao");
    }
}
