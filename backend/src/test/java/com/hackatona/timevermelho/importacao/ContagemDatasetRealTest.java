package com.hackatona.timevermelho.importacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hackatona.timevermelho.dominio.Solicitacao;
import com.hackatona.timevermelho.dominio.SolicitacaoRepository;
import com.hackatona.timevermelho.servico.FilaService;

/** Confere as contagens do dataset real (data/*.csv) contra o que o importador relata. */
@SpringBootTest
class ContagemDatasetRealTest {

    @Autowired RelatorioImportacao relatorio;
    @Autowired SolicitacaoRepository solicitacoes;
    @Autowired FilaService fila;

    @Test
    void relatorioBateComOsNumerosDoDataset() {
        Map<String, Long> r = relatorio.itens();
        assertThat(r).containsEntry("Solicitações lidas", 941L)
                .containsEntry("Eventos lidos", 1505L)
                .containsEntry("Solicitações duplicadas mescladas (mesmo paciente + procedimento)", 22L)
                .containsEntry("Eventos duplicados ignorados (mesmo conteúdo, id diferente)", 7L)
                .containsEntry("Eventos órfãos ignorados (solicitação fora da fila)", 9L);
    }

    @Test
    void soAsSolicitacoesNaoDuplicadasEntramNaFila() {
        long ativas = solicitacoes.findBySituacao(Solicitacao.SituacaoCadastro.ATIVA).size();
        long duplicadas = solicitacoes.findBySituacao(Solicitacao.SituacaoCadastro.DUPLICADA).size();
        assertThat(ativas + duplicadas).isEqualTo(941);
        assertThat(duplicadas).isEqualTo(22);
        assertThat(fila.atual().todos()).hasSize((int) ativas).hasSize(919);
    }
}
