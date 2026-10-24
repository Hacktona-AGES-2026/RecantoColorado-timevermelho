package com.hackatona.timevermelho;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.motor.ClasseSwalis;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.motor.TipoSinal;
import com.hackatona.timevermelho.servico.FilaService;
import com.hackatona.timevermelho.servico.SimulacaoService;

/** Simulador "E se?": muda o que deve mudar e explica quando nada muda. Nada é gravado. */
@SpringBootTest
class SimulacaoTest {

    @Autowired SimulacaoService simulacao;
    @Autowired FilaService fila;

    private ResultadoPaciente paciente(ClasseSwalis classe, boolean oncologico) {
        return fila.atual().ativos().stream()
                .filter(r -> r.classe() == classe && r.oncologico() == oncologico && !r.getClassificacao().ajustada())
                .filter(r -> r.getSinais().stream().noneMatch(s -> s.tipo() == TipoSinal.PS_RECENTE))
                .findFirst().orElseThrow();
    }

    private Dtos.Simulacao simular(String id, Integer dor, String evento, String resultado) {
        List<Dtos.EventoHipotetico> evs = evento == null ? null
                : List.of(new Dtos.EventoHipotetico(evento, resultado, null, null));
        return simulacao.simular(new Dtos.SimulacaoRequest(id, dor, null, evs, null));
    }

    @Test
    void eventoDePioraMudaClasseEPosicaoSemGravarNada() {
        String id = paciente(ClasseSwalis.D, false).getCaso().solicitacaoId();
        String versao = fila.atual().info().versao();
        var r = simular(id, null, "pronto_socorro", "observacao");
        assertThat(r.depois().classe()).isEqualTo("B");
        assertThat(r.depois().posicao()).isLessThan(r.antes().posicao());
        assertThat(r.observacoes()).isEmpty();
        assertThat(fila.atual().info().versao()).isEqualTo(versao);
        assertThat(fila.atual().get(id).classe()).isEqualTo(ClasseSwalis.D);
    }

    @Test
    void eventoQueNaoMudaUrgenciaEhExplicado() {
        String id = paciente(ClasseSwalis.D, false).getCaso().solicitacaoId();
        var r = simular(id, null, "consulta_reavaliacao", "melhora");
        assertThat(r.depois().classe()).isEqualTo(r.antes().classe());
        assertThat(r.observacoes()).anyMatch(o -> o.contains("não muda a urgência"));
    }

    @Test
    void mesmoValorDeHojeEhExplicado() {
        ResultadoPaciente p = fila.atual().ativos().stream().filter(x -> x.getCaso().dor() != null).findFirst().orElseThrow();
        var r = simular(p.getCaso().solicitacaoId(), p.getCaso().dor(), null, null);
        assertThat(r.observacoes()).anyMatch(o -> o.contains("igual à de hoje"));
    }

    @Test
    void sinalQueOPacienteJaTemEhExplicado() {
        String id = paciente(ClasseSwalis.A2, true).getCaso().solicitacaoId();
        var r = simular(id, null, "biopsia", "positivo");
        assertThat(r.depois().classe()).isEqualTo("A2");
        assertThat(r.observacoes()).isNotEmpty();
    }

    @Test
    void quedaDeClasseNaSimulacaoAvisaQueSeriaSoSugestao() {
        ResultadoPaciente p = fila.atual().ativos().stream()
                .filter(x -> !x.oncologico() && x.classe() == ClasseSwalis.B && x.getCaso().dor() != null
                        && x.getCaso().dor() >= 7 && x.getClassificacao().determinantes().stream()
                        .allMatch(s -> s.tipo() == TipoSinal.DOR_INTENSA))
                .findFirst().orElseThrow();
        var r = simular(p.getCaso().solicitacaoId(), 0, null, null);
        assertThat(ClasseSwalis.valueOf(r.antes().classe()).maisUrgenteQue(ClasseSwalis.valueOf(r.depois().classe()))).isTrue();
        assertThat(r.observacoes()).anyMatch(o -> o.contains("rebaixamento sugerido"));
    }
}
