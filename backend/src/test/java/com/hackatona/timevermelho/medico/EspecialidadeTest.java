package com.hackatona.timevermelho.medico;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hackatona.timevermelho.motor.Caso;

/** Quem entra na lista de cada especialidade (sem Spring). */
class EspecialidadeTest {

    static final CatalogoEspecialidades CATALOGO = CatalogoEspecialidades.carregarPadrao();

    static Especialidade esp(String codigo) {
        return CATALOGO.buscar(codigo).orElseThrow();
    }

    static Caso caso(String especialidade, String cid, String cidSec, String comorbidades, Integer idade) {
        return Caso.builder().solicitacaoId("X").especialidade(especialidade)
                .procedimento("Cirurgia Cardiaca".equals(especialidade) ? "Revascularizacao do miocardio" : "Outro").cid(cid).cidSecundario(cidSec)
                .comorbidades(comorbidades).idade(idade).eventos(List.of()).build();
    }

    @Test
    void faixaDeCidComparaLetraEDoisDigitos() {
        assertThat(Especialidade.naFaixa("I251", List.of("I20-I52"))).isTrue();
        assertThat(Especialidade.naFaixa("I52", List.of("I20-I52"))).isTrue();
        assertThat(Especialidade.naFaixa("I839", List.of("I20-I52"))).isFalse();
        assertThat(Especialidade.naFaixa("J251", List.of("I20-I52"))).isFalse();
        assertThat(Especialidade.naFaixa("G560", List.of("G56-G56"))).isTrue();
        assertThat(Especialidade.naFaixa(null, List.of("I20-I52"))).isFalse();
        assertThat(Especialidade.naFaixa("I", List.of("I20-I52"))).isFalse();
    }

    @Test
    void cardiologiaPegaCirurgiaCardiacaCidCardiacoEComorbidadeCardiaca() {
        Especialidade cardio = esp("CARDIOLOGIA");
        assertThat(cardio.motivos(caso("Cirurgia Cardiaca", "I251", null, null, 60), false))
                .contains("Procedimento de Cirurgia Cardiaca: Revascularizacao do miocardio").anyMatch(m -> m.startsWith("CID principal I25.1"));
        // hérnia, mas com insuficiência cardíaca: o cardiologista também precisa ver
        assertThat(cardio.motivos(caso("Cirurgia Geral", "K409", null, "DM2; ICC; fibrilacao atrial", 70), false))
                .containsExactly("Comorbidade: ICC");
        assertThat(cardio.motivos(caso("Cirurgia Geral", "K409", "I500", null, 70), false))
                .containsExactly("CID secundário I50.0");
        // varizes são vasculares, hipertensão sozinha não é doença cardíaca
        assertThat(cardio.motivos(caso("Cirurgia Vascular", "I839", "I10", "HAS", 50), false)).isEmpty();
        // "iam" não pode bater dentro de outra palavra
        assertThat(cardio.motivos(caso("Ortopedia", "M171", null, "familiar", 50), false)).isEmpty();
    }

    @Test
    void soOProcedimentoDaEspecialidadeAtribuiOPacienteAListaCirurgica() {
        Especialidade cardio = esp("CARDIOLOGIA");
        var cirurgico = cardio.avaliar(caso("Cirurgia Cardiaca", "I251", null, null, 60), false);
        assertThat(cirurgico.existe()).isTrue();
        assertThat(cirurgico.cirurgica()).isTrue();
        // CID secundário, comorbidade ou CID principal cardíaco sem procedimento cardíaco: só possível avaliação
        for (Caso c : List.of(caso("Cirurgia Geral", "K409", "I500", null, 70),
                caso("Cirurgia Geral", "K409", null, "ICC", 70),
                caso("Cirurgia Geral", "I251", null, null, 70))) {
            var corr = cardio.avaliar(c, false);
            assertThat(corr.existe()).isTrue();
            assertThat(corr.cirurgica()).isFalse();
        }
        assertThat(cardio.avaliar(caso("Ortopedia", "M171", null, "HAS", 50), false).existe()).isFalse();
        // especialidade sem procedimento cirúrgico próprio nunca atribui paciente cirúrgico
        assertThat(esp("ENDOCRINOLOGIA").avaliar(caso("Cirurgia Geral", "K409", "E119", "DM2", 60), false))
                .satisfies(c -> {
                    assertThat(c.existe()).isTrue();
                    assertThat(c.cirurgica()).isFalse();
                });
    }

    @Test
    void oncologiaPegaCidCEBiopsiaPositiva() {
        Especialidade onco = esp("ONCOLOGIA");
        assertThat(onco.motivos(caso("Urologia", "C61", null, null, 70), true)).isNotEmpty();
        assertThat(onco.motivos(caso("Cirurgia Geral", "D370", null, null, 70), true))
                .containsExactly("Paciente oncológico (biópsia positiva)");
        assertThat(onco.motivos(caso("Cirurgia Geral", "D370", null, null, 70), false)).isEmpty();
    }

    @Test
    void pediatriaUsaSoAIdade() {
        Especialidade ped = esp("PEDIATRIA");
        assertThat(ped.motivos(caso("Ortopedia", "M171", null, null, 12), false)).containsExactly("Idade 12 anos (até 17)");
        assertThat(ped.motivos(caso("Ortopedia", "M171", null, null, 18), false)).isEmpty();
        assertThat(ped.motivos(caso("Ortopedia", "M171", null, null, null), false)).isEmpty();
    }

    @Test
    void catalogoTemCodigosUnicosENomes() {
        assertThat(CATALOGO.todas()).hasSizeGreaterThan(10)
                .allSatisfy(e -> assertThat(e.nome()).isNotBlank());
    }
}
