package com.hackatona.timevermelho.servico;

import static com.hackatona.timevermelho.servico.FilaDeTeste.REF;
import static com.hackatona.timevermelho.servico.FilaDeTeste.caso;
import static com.hackatona.timevermelho.servico.FilaDeTeste.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.dominio.Alerta;
import com.hackatona.timevermelho.dominio.AlertaRepository;
import com.hackatona.timevermelho.motor.Caso;
import com.hackatona.timevermelho.motor.ClasseSwalis;
import com.hackatona.timevermelho.motor.DecisaoRegulador;
import com.hackatona.timevermelho.motor.Evento;
import com.hackatona.timevermelho.motor.StatusFila;
import com.hackatona.timevermelho.motor.TipoReavaliacao;

class AlertaServiceTest {

    FilaService fila = mock(FilaService.class);
    AlertaRepository repo = mock(AlertaRepository.class);
    AlertaService servico;

    @BeforeEach
    void preparar() {
        when(repo.findTop50ByOrderByCriadoEmDescIdDesc()).thenReturn(List.of());
        servico = new AlertaService(fila, repo);
    }

    private Caso desistente(String id, String cid) {
        return caso(id, cid).eventos(List.of(new Evento("E-" + id, id, REF.minusDays(5), "contato_telefonico",
                "desistiu", null, "Pct desistiu", "call center", false))).build();
    }

    private Dtos.Alertas alertas(FilaService.Snapshot s) {
        when(fila.atual()).thenReturn(s);
        return servico.alertas();
    }

    @Test
    void filaSemProblemasNaoTemAlertasNemReavaliacoes() {
        var a = alertas(snapshot(List.of(caso("S1", "K409").build())));
        assertThat(a.recentes()).isEmpty();
        assertThat(a.reavaliacao()).isEmpty();
        assertThat(a.saidas()).isEmpty();
    }

    @Test
    void alertasRecentesViramDtoComClasseEPosicao() {
        var al = new Alerta();
        al.setSolicitacaoId("S1");
        al.setNome("Maria");
        al.setTipo(Alerta.Tipo.MUDANCA_CLASSE);
        al.setMensagem("Classe mudou");
        al.setClasseAntes(ClasseSwalis.D);
        al.setClasseDepois(ClasseSwalis.A1);
        al.setPosicaoAntes(20);
        al.setPosicaoDepois(1);
        al.setCriadoEm(LocalDateTime.of(2026, 10, 23, 10, 0));
        when(repo.findTop50ByOrderByCriadoEmDescIdDesc()).thenReturn(List.of(al));

        var recentes = alertas(snapshot(List.of(caso("S1", "K409").build()))).recentes();
        assertThat(recentes).hasSize(1);
        var d = recentes.getFirst();
        assertThat(d.tipo()).isEqualTo("MUDANCA_CLASSE");
        assertThat(d.classeAntes()).isEqualTo("D");
        assertThat(d.classeDepois()).isEqualTo("A1");
        assertThat(d.posicaoAntes()).isEqualTo(20);
        assertThat(d.posicaoDepois()).isEqualTo(1);
    }

    @Test
    void reavaliacoesVemAgrupadasPorTipoComTotal() {
        var s = snapshot(List.of(
                caso("A", "K409").dor(null).build(),
                caso("B", "K409").limitacao(null).build(),
                caso("C", "C504").prioridadeManual("baixa").build()));
        var a = alertas(s);
        var incompleto = a.reavaliacao().stream().filter(g -> g.tipo().equals("DADO_INCOMPLETO")).findFirst().orElseThrow();
        assertThat(incompleto.total()).isEqualTo(2);
        assertThat(incompleto.titulo()).isEqualTo(TipoReavaliacao.DADO_INCOMPLETO.titulo());
        assertThat(a.reavaliacao()).extracting(Dtos.GrupoReavaliacao::tipo).contains("PRIORIDADE_DIVERGENTE");
    }

    @Test
    void cadaGrupoMostraNoMaximo50ItensMasInformaOTotalReal() {
        List<Caso> casos = new ArrayList<>();
        for (int i = 0; i < 60; i++) casos.add(caso("S" + i, "K409").dor(null).build());
        var g = alertas(snapshot(casos)).reavaliacao().stream().filter(x -> x.tipo().equals("DADO_INCOMPLETO")).findFirst().orElseThrow();
        assertThat(g.total()).isEqualTo(60);
        assertThat(g.itens()).hasSize(50);
    }

    @Test
    void saidaSugeridaDeNaoOncologicoAparecePendente() {
        var a = alertas(snapshot(List.of(desistente("S1", "K409"))));
        assertThat(a.saidas()).hasSize(1);
        var saida = a.saidas().getFirst();
        assertThat(saida.oncologico()).isFalse();
        assertThat(saida.status()).isEqualTo(StatusFila.SAIDA_SUGERIDA.name());
        assertThat(saida.cid()).isEqualTo("K409");
    }

    @Test
    void saidaSugeridaNuncaRemoveOncologicoDaFila() {
        var a = alertas(snapshot(List.of(desistente("S1", "C504"))));
        assertThat(a.saidas()).hasSize(1);
        var saida = a.saidas().getFirst();
        assertThat(saida.oncologico()).isTrue();
        assertThat(saida.status()).isEqualTo(StatusFila.ATIVA.name()); // continua na fila até o regulador decidir
    }

    @Test
    void saidaConfirmadaSomeDasListasDePendencia() {
        var decisoes = Map.of("S1", new DecisaoRegulador(null, null, true, "confirmado pelo regulador"));
        var a = alertas(snapshot(List.of(desistente("S1", "K409")), decisoes));
        assertThat(a.saidas()).isEmpty();
        assertThat(a.reavaliacao()).isEmpty();
    }
}
