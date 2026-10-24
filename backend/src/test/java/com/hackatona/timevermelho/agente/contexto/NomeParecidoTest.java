package com.hackatona.timevermelho.agente.contexto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Regras da busca por nome: caixa e acento nunca importam; erro de digitação só no modo aproximado, e pequeno. */
class NomeParecidoTest {

    static final String NOME = "Eliane Lopes Ramos";

    @Test
    void exatoIgnoraCaixaAcentoPontuacaoETitulos() {
        assertThat(LeituraAplicacaoSpring.nomeContem(NOME, "ELIANE LOPES RAMOS", false)).isTrue();
        assertThat(LeituraAplicacaoSpring.nomeContem(NOME, "élíane lópes", false)).isTrue();
        assertThat(LeituraAplicacaoSpring.nomeContem(NOME, "Sra. Eliane, Ramos!", false)).isTrue();
        assertThat(LeituraAplicacaoSpring.nomeContem(NOME, "dona eliane da ramos", false)).isTrue();
        assertThat(LeituraAplicacaoSpring.nomeContem(NOME, "elia lop", false)).isTrue(); // começo das palavras
    }

    @Test
    void exatoNaoAceitaErroDeDigitacao() {
        assertThat(LeituraAplicacaoSpring.nomeContem(NOME, "Eliane Lopez Ramos", false)).isFalse();
    }

    @Test
    void aproximadoAceitaErroPequenoENaoAceitaNomeDiferente() {
        assertThat(LeituraAplicacaoSpring.nomeContem(NOME, "Eliane Lopez Ramos", true)).isTrue();   // 1 letra
        assertThat(LeituraAplicacaoSpring.nomeContem(NOME, "Elaine Lopes Ramoss", true)).isTrue();  // inversão + letra a mais
        assertThat(LeituraAplicacaoSpring.nomeContem(NOME, "Mariana Lopes Ramos", true)).isFalse(); // outro nome
        assertThat(LeituraAplicacaoSpring.nomeContem(NOME, "Eli Lopes", true)).isTrue();            // começo continua valendo
        assertThat(LeituraAplicacaoSpring.nomeContem(NOME, "Ana Lopes", true)).isFalse();           // curta: sem tolerância
    }

    @Test
    void soTituloOuParticulaNaoAchaNinguem() {
        assertThat(LeituraAplicacaoSpring.nomeContem(NOME, "dona da", true)).isFalse();
    }

    @Test
    void distanciaDeEdicao() {
        assertThat(LeituraAplicacaoSpring.distancia("lopes", "lopez")).isEqualTo(1);
        assertThat(LeituraAplicacaoSpring.distancia("eliane", "elaine")).isEqualTo(1); // letras vizinhas invertidas
        assertThat(LeituraAplicacaoSpring.distancia("ramos", "ramoss")).isEqualTo(1);
        assertThat(LeituraAplicacaoSpring.distancia("", "abc")).isEqualTo(3);
    }
}
