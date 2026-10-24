package com.hackatona.timevermelho.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.dominio.AjusteReguladorRepository;
import com.hackatona.timevermelho.dominio.AlertaRepository;
import com.hackatona.timevermelho.dominio.EventoClinicoRepository;
import com.hackatona.timevermelho.dominio.SolicitacaoRepository;
import com.hackatona.timevermelho.motor.ClasseSwalis;
import com.hackatona.timevermelho.motor.ResultadoPaciente;

/** Requisito P0: simular nunca grava nada nem muda a fila oficial. */
@SpringBootTest
class SimulacaoServiceTest {

    @Autowired SimulacaoService simulacao;
    @Autowired FilaService fila;
    @Autowired EventoClinicoRepository eventos;
    @Autowired AjusteReguladorRepository ajustes;
    @Autowired AlertaRepository alertas;
    @Autowired SolicitacaoRepository solicitacoes;

    private record Foto(FilaService.Snapshot snapshot, Map<String, String> posicaoEClasse, long eventos, long ajustes,
                        long alertas, long solicitacoes) {}

    private Foto fotografar() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ResultadoPaciente r : fila.atual().todos()) {
            m.put(r.getCaso().solicitacaoId(), r.getPosicao() + "|" + r.classe() + "|" + r.getStatus() + "|" + r.getScore());
        }
        return new Foto(fila.atual(), m, eventos.count(), ajustes.count(), alertas.count(), solicitacoes.count());
    }

    private void assertNadaMudou(Foto antes) {
        Foto depois = fotografar();
        assertThat(depois.snapshot()).as("a fila oficial não pode ser recalculada").isSameAs(antes.snapshot());
        assertThat(depois.posicaoEClasse()).isEqualTo(antes.posicaoEClasse());
        assertThat(depois.eventos()).isEqualTo(antes.eventos());
        assertThat(depois.ajustes()).isEqualTo(antes.ajustes());
        assertThat(depois.alertas()).isEqualTo(antes.alertas());
        assertThat(depois.solicitacoes()).isEqualTo(antes.solicitacoes());
    }

    private String pacienteClasseD() {
        return fila.atual().ativos().stream().filter(r -> r.classe() == ClasseSwalis.D && !r.oncologico())
                .findFirst().orElseThrow().getCaso().solicitacaoId();
    }

    @Test
    void simularPacienteNaoAlteraFilaNemBanco() {
        Foto antes = fotografar();
        String id = pacienteClasseD();

        var req = new Dtos.SimulacaoRequest(id, 9, "grave",
                List.of(new Dtos.EventoHipotetico("pronto_socorro", "observacao", null, "Dor intensa no PS")),
                "Refere piora importante e episodio de encarceramento.");
        var r = simulacao.simular(req);

        assertThat(r.mensagem()).startsWith("Simulação (nada foi salvo)");
        assertThat(r.antes().solicitacaoId()).isEqualTo(id);
        assertThat(r.depois().solicitacaoId()).isEqualTo(id);
        assertNadaMudou(antes);
    }

    @Test
    void simulacaoMostraONovoSinalEAMudancaDeClasse() {
        String id = pacienteClasseD();
        var r = simulacao.simular(new Dtos.SimulacaoRequest(id, null, null, null,
                "Episodio de encarceramento ha 2 dias."));

        assertThat(r.antes().classe()).isEqualTo("D");
        assertThat(r.depois().classe()).isEqualTo("A1");
        assertThat(r.depois().posicao()).isLessThan(r.antes().posicao());
        assertThat(r.sinaisNovos()).isNotEmpty();
        assertThat(r.componentesDepois()).hasSize(6);
        assertThat(r.mensagem()).contains("Subiu").contains("classe D → A1");
    }

    @Test
    void simulacaoSemMudancaNenhumaMantemAPosicao() {
        String id = pacienteClasseD();
        var r = simulacao.simular(new Dtos.SimulacaoRequest(id, null, null, null, null));
        assertThat(r.depois().posicao()).isEqualTo(r.antes().posicao());
        assertThat(r.depois().classe()).isEqualTo(r.antes().classe());
        assertThat(r.sinaisNovos()).isEmpty();
        assertThat(r.mensagem()).contains("nenhum sinal novo");
    }

    @Test
    void simulacaoDeEventoHipoteticoNaoCriaEventoNoBanco() {
        Foto antes = fotografar();
        String id = pacienteClasseD();
        simulacao.simular(new Dtos.SimulacaoRequest(id, null, null,
                List.of(new Dtos.EventoHipotetico("internacao", "internado", 4.0, null)), null));
        assertNadaMudou(antes);
    }

    @Test
    void simulacaoDePacienteInexistenteDaErroClaro() {
        var req = new Dtos.SimulacaoRequest("SOL-NAO-EXISTE", 5, null, null, null);
        assertThatThrownBy(() -> simulacao.simular(req))
                .isInstanceOf(Erros.NaoEncontrado.class)
                .hasMessageContaining("SOL-NAO-EXISTE");
    }

    @Test
    void simularPesosNaoGravaNadaENemRecalculaAFila() {
        Foto antes = fotografar();
        var r = simulacao.simularPesos(new Dtos.PesosDto(0, 0, 0, 0, 100, 0)); // só tempo de espera

        assertThat(r.regraDeOuroRespeitada()).isTrue();
        assertThat(r.topo()).hasSize(20);
        assertThat(r.pacientesQueMudaram()).isGreaterThan(0);
        assertThat(r.maioresSubidas()).hasSizeLessThanOrEqualTo(10);
        assertThat(r.maioresQuedas()).hasSizeLessThanOrEqualTo(10);
        assertNadaMudou(antes);
    }

    @Test
    void simularOsPesosPadraoNaoMudaNinguem() {
        var r = simulacao.simularPesos(new Dtos.PesosDto(30, 25, 15, 10, 15, 5));
        assertThat(r.pacientesQueMudaram()).isZero();
        assertThat(r.maioresSubidas()).isEmpty();
        assertThat(r.maioresQuedas()).isEmpty();
    }

    @Test
    void pesosTodosZeroSaoRecusados() {
        assertThatThrownBy(() -> simulacao.simularPesos(new Dtos.PesosDto(0, 0, 0, 0, 0, 0)))
                .isInstanceOf(Erros.RegraNegocio.class)
                .hasMessageContaining("Pelo menos um peso");
    }

    @Test
    void movimentosDaSimulacaoDePesosTemDeltaCoerente() {
        var r = simulacao.simularPesos(new Dtos.PesosDto(0, 0, 0, 0, 100, 0));
        r.maioresSubidas().forEach(m -> {
            assertThat(m.delta()).isPositive();
            assertThat(m.delta()).isEqualTo(m.posicaoAntes() - m.posicaoDepois());
        });
        r.maioresQuedas().forEach(m -> assertThat(m.delta()).isNegative());
    }
}
