package com.hackatona.timevermelho.motor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class CalculadoraScoreTest {

    static final LocalDate REF = LocalDate.of(2026, 10, 23);
    final CalculadoraScore calc = new CalculadoraScore();

    static Caso.CasoBuilder caso() {
        return Caso.builder().solicitacaoId("S1").pacienteId("P1").nome("Paciente").cid("K409").cidFormatado("K40.9")
                .riscoCirurgico(1).asa(1).dor(0).limitacao("nenhuma").diasEmFila(0)
                .dataEntrada(REF).eventos(List.of());
    }

    static Sinal sinal(TipoSinal t) {
        return Sinal.de(t, t.rotulo(), "evidência", "teste");
    }

    private ComponenteScore componente(CalculadoraScore.Resultado r, String chave) {
        return r.componentes().stream().filter(c -> c.chave().equals(chave)).findFirst().orElseThrow();
    }

    @Test
    void devolveSeisComponentesNaOrdemEsperada() {
        var r = calc.calcular(caso().build(), List.of(), ClasseSwalis.D, Pesos.PADRAO);
        assertThat(r.componentes()).extracting(ComponenteScore::chave)
                .containsExactly("diagnostico", "piora", "dor", "limitacao", "espera", "risco");
    }

    @Test
    void maximosDosComponentesSomam100() {
        var r = calc.calcular(caso().build(), List.of(), ClasseSwalis.D, Pesos.PADRAO);
        assertThat(r.componentes().stream().mapToDouble(ComponenteScore::maximo).sum()).isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.5));
        assertThat(componente(r, "diagnostico").maximo()).isEqualTo(30.0);
        assertThat(componente(r, "piora").maximo()).isEqualTo(25.0);
    }

    @Test
    void totalEhASomaDosComponentes() {
        var c = caso().dor(5).limitacao("moderada").diasEmFila(100).asa(3).riscoCirurgico(3).build();
        var r = calc.calcular(c, List.of(sinal(TipoSinal.PS_RECENTE)), ClasseSwalis.B, Pesos.PADRAO);
        double soma = r.componentes().stream().mapToDouble(ComponenteScore::pontos).sum();
        assertThat(r.total()).isCloseTo(soma, org.assertj.core.data.Offset.offset(0.2));
    }

    @Test
    void pacienteSemNadaTemScoreZero() {
        var r = calc.calcular(caso().build(), List.of(), ClasseSwalis.D, Pesos.PADRAO);
        assertThat(r.total()).isEqualTo(0.0);
    }

    // ---------- diagnóstico ----------

    @Test
    void diagnosticoOncologicoPorCidOuBiopsiaDaPontuacaoMaxima() {
        var porCid = calc.calcular(caso().cid("C504").cidFormatado("C50.4").build(), List.of(sinal(TipoSinal.ONCO_CID)),
                ClasseSwalis.A2, Pesos.PADRAO);
        var porBiopsia = calc.calcular(caso().build(), List.of(sinal(TipoSinal.BIOPSIA_POSITIVA)), ClasseSwalis.A2,
                Pesos.PADRAO);
        assertThat(componente(porCid, "diagnostico").pontos()).isEqualTo(30.0);
        assertThat(componente(porBiopsia, "diagnostico").pontos()).isEqualTo(30.0);
    }

    @Test
    void diagnosticoUrgenteIncertoEBenignoTemPontuacoesDiferentes() {
        double urgente = componente(calc.calcular(caso().build(), List.of(sinal(TipoSinal.CID_URGENTE)), ClasseSwalis.A1,
                Pesos.PADRAO), "diagnostico").pontos();
        double incerto = componente(calc.calcular(caso().build(), List.of(sinal(TipoSinal.CID_INCERTO)), ClasseSwalis.B,
                Pesos.PADRAO), "diagnostico").pontos();
        double benigno = componente(calc.calcular(caso().build(), List.of(), ClasseSwalis.D, Pesos.PADRAO),
                "diagnostico").pontos();
        assertThat(urgente).isGreaterThan(incerto);
        assertThat(incerto).isGreaterThan(benigno);
        assertThat(benigno).isZero();
    }

    // ---------- piora ----------

    @Test
    void pioraSomaOsSinaisEFicaLimitadaAoMaximo() {
        var varios = List.of(sinal(TipoSinal.ALARME_GRAVE), sinal(TipoSinal.PROGRESSAO_IMAGEM), sinal(TipoSinal.INTERNACAO_RECENTE));
        var r = calc.calcular(caso().build(), varios, ClasseSwalis.A1, Pesos.PADRAO);
        assertThat(componente(r, "piora").pontos()).isEqualTo(componente(r, "piora").maximo());

        var um = calc.calcular(caso().build(), List.of(sinal(TipoSinal.PS_RECENTE)), ClasseSwalis.B, Pesos.PADRAO);
        assertThat(componente(um, "piora").pontos()).isGreaterThan(0).isLessThan(componente(um, "piora").maximo());
    }

    @Test
    void semSinaisDePioraAExplicacaoDizIsso() {
        var r = calc.calcular(caso().build(), List.of(), ClasseSwalis.D, Pesos.PADRAO);
        assertThat(componente(r, "piora").explicacao()).isEqualTo("Nenhum sinal de piora registrado");
    }

    // ---------- dor e limitação ----------

    @Test
    void dorEhProporcionalAEscalaEVAENulaNaoPontua() {
        var dez = calc.calcular(caso().dor(10).build(), List.of(), ClasseSwalis.D, Pesos.PADRAO);
        var cinco = calc.calcular(caso().dor(5).build(), List.of(), ClasseSwalis.D, Pesos.PADRAO);
        var nula = calc.calcular(caso().dor(null).build(), List.of(), ClasseSwalis.D, Pesos.PADRAO);
        assertThat(componente(dez, "dor").pontos()).isEqualTo(componente(dez, "dor").maximo());
        assertThat(componente(cinco, "dor").pontos()).isEqualTo(componente(dez, "dor").pontos() / 2);
        assertThat(componente(nula, "dor").pontos()).isZero();
        assertThat(componente(nula, "dor").explicacao()).isEqualTo("Dor não informada");
    }

    @Test
    void limitacaoGraveModeradaLeveENenhuma() {
        double grave = pontosLimitacao("grave");
        double moderada = pontosLimitacao("moderada");
        double leve = pontosLimitacao("leve");
        assertThat(grave).isGreaterThan(moderada);
        assertThat(moderada).isGreaterThan(leve);
        assertThat(leve).isGreaterThan(0);
        assertThat(pontosLimitacao("nenhuma")).isZero();
        assertThat(pontosLimitacao(null)).isZero();
    }

    private double pontosLimitacao(String lim) {
        return componente(calc.calcular(caso().limitacao(lim).build(), List.of(), ClasseSwalis.D, Pesos.PADRAO),
                "limitacao").pontos();
    }

    // ---------- espera ----------

    @Test
    void esperaCrescePeloPrazoDaClasseESaturaEm3Vezes() {
        // classe A1 = 15 dias; 45 dias = 3x o prazo = máximo
        var saturada = calc.calcular(caso().diasEmFila(45).build(), List.of(), ClasseSwalis.A1, Pesos.PADRAO);
        var alemDoLimite = calc.calcular(caso().diasEmFila(500).build(), List.of(), ClasseSwalis.A1, Pesos.PADRAO);
        var metade = calc.calcular(caso().diasEmFila(22).build(), List.of(), ClasseSwalis.A1, Pesos.PADRAO);
        assertThat(componente(saturada, "espera").pontos()).isEqualTo(componente(saturada, "espera").maximo());
        assertThat(componente(alemDoLimite, "espera").pontos()).isEqualTo(componente(saturada, "espera").maximo());
        assertThat(componente(metade, "espera").pontos()).isLessThan(componente(saturada, "espera").pontos());
    }

    @Test
    void mesmosDiasPesamMaisEmClasseComPrazoCurto() {
        var a1 = calc.calcular(caso().diasEmFila(30).build(), List.of(), ClasseSwalis.A1, Pesos.PADRAO);
        var d = calc.calcular(caso().diasEmFila(30).build(), List.of(), ClasseSwalis.D, Pesos.PADRAO);
        assertThat(componente(a1, "espera").pontos()).isGreaterThan(componente(d, "espera").pontos());
    }

    // ---------- risco ----------

    @Test
    void riscoUsaAsaERiscoDoProcedimentoEAssumeValorMedioQuandoFalta() {
        var alto = calc.calcular(caso().asa(4).riscoCirurgico(5).build(), List.of(), ClasseSwalis.D, Pesos.PADRAO);
        var baixo = calc.calcular(caso().asa(1).riscoCirurgico(1).build(), List.of(), ClasseSwalis.D, Pesos.PADRAO);
        var faltando = calc.calcular(caso().asa(null).riscoCirurgico(null).build(), List.of(), ClasseSwalis.D, Pesos.PADRAO);
        var padrao = calc.calcular(caso().asa(2).riscoCirurgico(2).build(), List.of(), ClasseSwalis.D, Pesos.PADRAO);
        assertThat(componente(alto, "risco").pontos()).isEqualTo(componente(alto, "risco").maximo());
        assertThat(componente(baixo, "risco").pontos()).isZero();
        assertThat(componente(faltando, "risco").pontos()).isEqualTo(componente(padrao, "risco").pontos());
        assertThat(componente(faltando, "risco").explicacao()).contains("ASA não informado");
    }

    // ---------- pesos ----------

    @Test
    void pesosAjustaveisMudamOMaximoDeCadaComponente() {
        var soDiagnostico = new Pesos(1, 0, 0, 0, 0, 0);
        var r = calc.calcular(caso().cid("C504").cidFormatado("C50.4").build(), List.of(sinal(TipoSinal.ONCO_CID)),
                ClasseSwalis.A2, soDiagnostico);
        assertThat(componente(r, "diagnostico").maximo()).isEqualTo(100.0);
        assertThat(componente(r, "piora").maximo()).isZero();
        assertThat(r.total()).isEqualTo(100.0);
    }

    @Test
    void dobrarTodosOsPesosNaoMudaOResultado() {
        var c = caso().dor(6).limitacao("grave").diasEmFila(40).asa(3).riscoCirurgico(4).build();
        var sinais = List.of(sinal(TipoSinal.PS_RECENTE));
        var normal = calc.calcular(c, sinais, ClasseSwalis.B, Pesos.PADRAO);
        var dobro = calc.calcular(c, sinais, ClasseSwalis.B, new Pesos(60, 50, 30, 20, 30, 10));
        assertThat(dobro.total()).isEqualTo(normal.total());
    }

    @Test
    void pesosTodosZeroNaoQuebramNemDividemPorZero() {
        var r = calc.calcular(caso().dor(10).build(), List.of(), ClasseSwalis.D, new Pesos(0, 0, 0, 0, 0, 0));
        assertThat(r.total()).isEqualTo(0.0);
        assertThat(r.componentes()).allSatisfy(c -> assertThat(c.pontos()).isZero());
    }
}
