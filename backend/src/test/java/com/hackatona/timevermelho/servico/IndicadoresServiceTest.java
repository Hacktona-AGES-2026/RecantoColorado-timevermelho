package com.hackatona.timevermelho.servico;

import static com.hackatona.timevermelho.servico.FilaDeTeste.caso;
import static com.hackatona.timevermelho.servico.FilaDeTeste.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.importacao.RelatorioImportacao;
import com.hackatona.timevermelho.motor.Caso;
import com.hackatona.timevermelho.motor.GuardRegraDeOuro;

/**
 * Fila de 6 pacientes montada à mão: 3 oncológicos que o cadastro deixou com prioridade "baixa" e
 * 3 pacientes sem urgência com prioridade "alta". Na ordem de hoje os calmos vêm na frente.
 */
class IndicadoresServiceTest {

    FilaService fila;
    RelatorioImportacao relatorio = new RelatorioImportacao();
    IndicadoresService servico;
    FilaService.Snapshot snap;

    @BeforeEach
    void montarFila() {
        List<Caso> casos = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            casos.add(caso("ONCO-" + i, "C504").prioridadeManual("baixa").dataEntrada(FilaDeTeste.REF.minusDays(100 + i)).build());
            casos.add(caso("CALMO-" + i, "K409").prioridadeManual("alta").dataEntrada(FilaDeTeste.REF.minusDays(200 + i)).build());
        }
        snap = snapshot(casos);
        fila = mock(FilaService.class);
        when(fila.atual()).thenReturn(snap);
        relatorio.definir("Solicitações lidas", 6);
        servico = new IndicadoresService(fila, relatorio);
    }

    private Dtos.Comparacao comparacao(Dtos.Indicadores ind, String inicio) {
        return ind.comparacoes().stream().filter(c -> c.indicador().startsWith(inicio)).findFirst().orElseThrow();
    }

    @Test
    void totaisBatemComAFila() {
        var ind = servico.indicadores();
        assertThat(ind.totalAtivos()).isEqualTo(6);
        assertThat(ind.totalForaDaFila()).isZero();
        assertThat(ind.porClasse().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(6);
        assertThat(ind.porClasse()).containsEntry("A2", 3L).containsEntry("D", 3L);
    }

    @Test
    void antesDosOncologicosEstaoAbaixoDosCalmosEDepoisNao() {
        var ind = servico.indicadores();
        var regraDeOuro = comparacao(ind, "Oncológicos abaixo de um paciente sem urgência");
        assertThat(regraDeOuro.antes()).isEqualTo("3");
        assertThat(regraDeOuro.depois()).isEqualTo("0");
        assertThat(GuardRegraDeOuro.respeitada(snap.ativos())).isTrue();
    }

    @Test
    void posicaoMedianaDosOncologicosMelhora() {
        var mediana = comparacao(servico.indicadores(), "Posição mediana dos oncológicos");
        assertThat(Integer.parseInt(mediana.depois())).isLessThan(Integer.parseInt(mediana.antes()));
        assertThat(mediana.antes()).isEqualTo("5"); // ordem de hoje: calmos em 1-3, oncológicos em 4-6
        assertThat(mediana.depois()).isEqualTo("2");  // FilaVIVA: oncológicos em 1-3
    }

    @Test
    void oncologicosNoPrimeiroTercoSobemDe0Para67PorCento() {
        var c = comparacao(servico.indicadores(), "Oncológicos no primeiro terço");
        assertThat(c.antes()).isEqualTo("0%");
        assertThat(c.depois()).isEqualTo("67%"); // 2 de 3 nas posições 1 e 2
    }

    @Test
    void urgentesSemPrioridadeAltaNoCadastroSaoContados() {
        var c = comparacao(servico.indicadores(), "Urgentes (A1/A2) sem prioridade");
        assertThat(c.antes()).isEqualTo("3");
        assertThat(c.depois()).isEqualTo("0");
    }

    @Test
    void indicadorSemPacientesNoGrupoMostraTraco() {
        var c = comparacao(servico.indicadores(), "Biópsia positiva com CID benigno");
        assertThat(c.antes()).isEqualTo("—");
        assertThat(c.depois()).isEqualTo("—");
    }

    @Test
    void sempreTrazOsSeteIndicadoresEDezFaixas() {
        var ind = servico.indicadores();
        assertThat(ind.comparacoes()).hasSize(7);
        assertThat(ind.faixas()).hasSize(10);
        assertThat(ind.faixas().stream().mapToInt(Dtos.Faixa::oncoAntes).sum()).isEqualTo(3);
        assertThat(ind.faixas().stream().mapToInt(Dtos.Faixa::oncoDepois).sum()).isEqualTo(3);
    }

    @Test
    void matrizCruzaPrioridadeManualComClasse() {
        var matriz = servico.indicadores().matriz();
        assertThat(matriz).extracting(Dtos.LinhaMatriz::prioridadeManual)
                .containsExactly("alta", "media", "baixa", "sem prioridade");
        var baixa = matriz.stream().filter(l -> l.prioridadeManual().equals("baixa")).findFirst().orElseThrow();
        var alta = matriz.stream().filter(l -> l.prioridadeManual().equals("alta")).findFirst().orElseThrow();
        assertThat(baixa.porClasse()).containsEntry("A2", 3L);
        assertThat(alta.porClasse()).containsEntry("D", 3L);
    }

    @Test
    void qualidadeDoDadoIncluiORelatorioDaImportacao() {
        var qualidade = servico.indicadores().qualidadeDados();
        assertThat(qualidade).anySatisfy(q -> {
            assertThat(q.item()).isEqualTo("Solicitações lidas");
            assertThat(q.quantidade()).isEqualTo(6);
        });
        assertThat(qualidade).extracting(Dtos.QualidadeDado::item).contains("Saídas sugeridas aguardando o regulador");
    }

    @Test
    void filaVaziaNaoQuebra() {
        when(fila.atual()).thenReturn(snapshot(List.of()));
        var ind = servico.indicadores();
        assertThat(ind.totalAtivos()).isZero();
        assertThat(ind.comparacoes()).hasSize(7);
    }
}
