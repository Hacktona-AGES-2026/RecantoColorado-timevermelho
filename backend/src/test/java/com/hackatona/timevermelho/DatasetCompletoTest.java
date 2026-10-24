package com.hackatona.timevermelho;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.dominio.TipoAjuste;
import com.hackatona.timevermelho.motor.GuardRegraDeOuro;
import com.hackatona.timevermelho.servico.DecisaoService;
import com.hackatona.timevermelho.servico.Erros;
import com.hackatona.timevermelho.servico.ExportService;
import com.hackatona.timevermelho.servico.FilaService;

/** Sobe a aplicação com o dataset inteiro (banco H2 em memória) e confere as garantias principais. */
@SpringBootTest
class DatasetCompletoTest {

    @Autowired FilaService fila;
    @Autowired DecisaoService decisao;
    @Autowired ExportService export;

    @Test
    void filaInteiraRespeitaRegraDeOuro() {
        var snap = fila.atual();
        assertThat(snap.casos()).hasSize(919); // 941 menos 22 duplicadas
        assertThat(snap.ativos()).hasSizeGreaterThan(850);
        assertThat(GuardRegraDeOuro.respeitada(snap.ativos())).isTrue();
    }

    @Test
    void biopsiaPositivaComCidBenignoSobeParaA2() {
        var r = fila.atual().get("SOL-2026-00351");
        assertThat(r.classe().name()).isEqualTo("A2");
        assertThat(r.getPosicao()).isLessThan(r.getPosicaoManual());
    }

    @Test
    void exportTemCabecalhoEUmaLinhaPorAtivo() {
        String csv = export.csv();
        assertThat(csv).startsWith("solicitacao_id,posicao,justificativa\n");
        assertThat(csv.lines().count()).isEqualTo(fila.atual().ativos().size() + 1L);
    }

    @Test
    void naoDeixaRebaixarOncologico() {
        String onco = fila.atual().ativos().stream().filter(r -> r.getCaso().oncologicoPeloCid()).findFirst()
                .orElseThrow().getCaso().solicitacaoId();
        assertThatThrownBy(() -> decisao.ajustar(new Dtos.AjusteRequest(onco, TipoAjuste.ALTERAR_CLASSE, "D",
                "teste de rebaixamento indevido", "teste")))
                .isInstanceOf(Erros.RegraNegocio.class)
                .hasMessageContaining("Regra de ouro");
    }
}
