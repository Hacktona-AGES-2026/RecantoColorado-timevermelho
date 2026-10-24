package com.hackatona.timevermelho;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.dominio.AjusteReguladorRepository;
import com.hackatona.timevermelho.dominio.EventoClinicoRepository;
import com.hackatona.timevermelho.dominio.ProcedimentoRepository;
import com.hackatona.timevermelho.dominio.Solicitacao;
import com.hackatona.timevermelho.dominio.SolicitacaoRepository;
import com.hackatona.timevermelho.dominio.TipoAjuste;
import com.hackatona.timevermelho.motor.ClasseSwalis;
import com.hackatona.timevermelho.motor.GuardRegraDeOuro;
import com.hackatona.timevermelho.motor.MotorPriorizacao;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.motor.TipoReavaliacao;
import com.hackatona.timevermelho.motor.TipoSinal;
import com.hackatona.timevermelho.servico.AlertaService;
import com.hackatona.timevermelho.servico.DecisaoService;
import com.hackatona.timevermelho.servico.Erros;
import com.hackatona.timevermelho.servico.ExportService;
import com.hackatona.timevermelho.servico.FilaService;

/**
 * Regras de rebaixamento (FAQ do desafio: "Pode sugerir, com justificativa, para o regulador confirmar. Nunca um
 * oncológico."), sobre o dataset inteiro. Cada teste volta ao estado do dataset.
 */
@SpringBootTest
class RebaixamentoTest {

    @Autowired FilaService fila;
    @Autowired DecisaoService decisao;
    @Autowired AlertaService alertas;
    @Autowired ExportService export;
    @Autowired AjusteReguladorRepository ajustes;
    @Autowired EventoClinicoRepository eventos;
    @Autowired SolicitacaoRepository solicitacoes;
    @Autowired ProcedimentoRepository procedimentos;
    @Autowired MotorPriorizacao motor;
    @Value("${app.dataset.dir}") String pastaDataset;

    @AfterEach
    void voltarAoDataset() {
        decisao.resetar();
    }

    // ---------- pacientes do dataset ----------

    private ResultadoPaciente primeiro(java.util.function.Predicate<ResultadoPaciente> p) {
        return fila.atual().ativos().stream().filter(r -> !r.getClassificacao().ajustada())
                .filter(r -> r.getStatus() == com.hackatona.timevermelho.motor.StatusFila.ATIVA).filter(p)
                .findFirst().orElseThrow();
    }

    private ResultadoPaciente naoOncologico(ClasseSwalis classe) {
        return primeiro(r -> !r.oncologico() && r.classe() == classe && r.getSinais().stream()
                .noneMatch(s -> s.tipo() == TipoSinal.PS_RECENTE || s.tipo() == TipoSinal.INTERNACAO_RECENTE));
    }

    private ResultadoPaciente oncologico(ClasseSwalis classe) {
        return primeiro(r -> r.oncologico() && r.classe() == classe);
    }

    private ResultadoPaciente agora(String id) {
        return fila.atual().get(id);
    }

    private Dtos.Impacto ajustar(String id, TipoAjuste tipo, String classe, String justificativa) {
        return decisao.ajustar(new Dtos.AjusteRequest(id, tipo, classe, justificativa, "regulador-teste"));
    }

    private void evento(String id, String tipo, String resultado) {
        decisao.registrarEvento(new Dtos.NovoEvento(id, tipo, resultado, 3.0, "evento de teste", null));
    }

    /** Simula reprocessamento com correção de dado: o evento novo do paciente é apagado do banco e a fila recalcula. */
    private void apagarEventosNovosERecalcular(String id) {
        eventos.findAll().stream().filter(e -> e.isSintetico() && id.equals(e.getSolicitacaoId())).forEach(eventos::delete);
        fila.recalcular();
    }

    private long registros(String id, TipoAjuste tipo) {
        return ajustes.findBySolicitacaoIdOrderByCriadoEmDescIdDesc(id).stream().filter(a -> a.getTipo() == tipo).count();
    }

    // ---------- 1 a 3: sugerir, confirmar, rejeitar ----------

    @Test
    void teste1_rebaixamentoManualViraSugestaoENaoEhAplicado() {
        ResultadoPaciente p = naoOncologico(ClasseSwalis.B);
        String id = p.getCaso().solicitacaoId();
        Dtos.Impacto imp = ajustar(id, TipoAjuste.ALTERAR_CLASSE, "D", "dor controlada na última consulta");

        assertThat(agora(id).classe()).isEqualTo(ClasseSwalis.B);
        assertThat(agora(id).getEstadoRebaixamento()).isEqualTo("PENDENTE");
        assertThat(imp.mensagem()).contains("sugerido").contains("continua valendo");
        assertThat(registros(id, TipoAjuste.SUGERIR_REBAIXAMENTO)).isEqualTo(1);
        assertThat(registros(id, TipoAjuste.ALTERAR_CLASSE)).isZero();

        Dtos.Rebaixamento r = alertas.rebaixamentos().stream().filter(x -> x.solicitacaoId().equals(id)).findFirst().orElseThrow();
        assertThat(r.estado()).isEqualTo("PENDENTE");
        assertThat(r.classeVigente()).isEqualTo("B");
        assertThat(r.classeProposta()).isEqualTo("D");
        assertThat(r.fatores()).isEqualTo("dor controlada na última consulta");
        assertThat(r.evidencias()).startsWith("Classe calculada B");
        assertThat(r.sugeridoPor()).isEqualTo("regulador-teste");
        assertThat(GuardRegraDeOuro.respeitada(fila.atual().ativos())).isTrue();
    }

    @Test
    void teste1b_recalculoAutomaticoQueBaixariaAClasseViraSugestao() {
        ResultadoPaciente p = naoOncologico(ClasseSwalis.D);
        String id = p.getCaso().solicitacaoId();
        evento(id, "pronto_socorro", "observacao");
        assertThat(agora(id).classe()).isEqualTo(ClasseSwalis.B);

        apagarEventosNovosERecalcular(id);

        assertThat(agora(id).classe()).as("a queda não é aplicada sem o regulador").isEqualTo(ClasseSwalis.B);
        assertThat(agora(id).getClassificacao().classeCalculada()).isEqualTo(ClasseSwalis.D);
        assertThat(agora(id).getEstadoRebaixamento()).isEqualTo("PENDENTE");
        Dtos.Rebaixamento r = alertas.rebaixamentoDe(id);
        assertThat(r.automatico()).isTrue();
        assertThat(r.fatores()).contains("Pronto-socorro");
        assertThat(GuardRegraDeOuro.respeitada(fila.atual().ativos())).isTrue();

        // recálculos seguintes não criam sugestões repetidas
        fila.recalcular();
        assertThat(registros(id, TipoAjuste.SUGERIR_REBAIXAMENTO)).isEqualTo(1);
    }

    @Test
    void teste2_reguladorConfirmaORebaixamentoComJustificativa() {
        String id = naoOncologico(ClasseSwalis.B).getCaso().solicitacaoId();
        ajustar(id, TipoAjuste.ALTERAR_CLASSE, "D", "dor controlada na última consulta");
        ajustar(id, TipoAjuste.CONFIRMAR_REBAIXAMENTO, null, "revisado com o cirurgião, sem dor");

        assertThat(agora(id).classe()).isEqualTo(ClasseSwalis.D);
        assertThat(agora(id).getEstadoRebaixamento()).isEqualTo("CONFIRMADO");
        Dtos.Rebaixamento r = alertas.rebaixamentoDe(id);
        assertThat(r.estado()).isEqualTo("CONFIRMADO");
        assertThat(r.decisao()).isEqualTo("revisado com o cirurgião, sem dor");
        assertThat(r.decididoPor()).isEqualTo("regulador-teste");
        assertThat(r.decididoEm()).isNotNull();
        // sem nova sugestão pendente: confirmar de novo é recusado
        assertThatThrownBy(() -> ajustar(id, TipoAjuste.CONFIRMAR_REBAIXAMENTO, null, "confirmando outra vez"))
                .isInstanceOf(Erros.RegraNegocio.class);
        assertThat(GuardRegraDeOuro.respeitada(fila.atual().ativos())).isTrue();
    }

    @Test
    void teste2b_confirmarRebaixamentoAutomaticoVoltaASeguirAClasseCalculada() {
        String id = naoOncologico(ClasseSwalis.D).getCaso().solicitacaoId();
        evento(id, "pronto_socorro", "observacao");
        apagarEventosNovosERecalcular(id);
        ajustar(id, TipoAjuste.CONFIRMAR_REBAIXAMENTO, null, "atendimento lançado por engano");
        assertThat(agora(id).classe()).isEqualTo(ClasseSwalis.D);
        assertThat(agora(id).getClassificacao().ajustada()).isFalse();
    }

    @Test
    void teste3_reguladorRejeitaEAPrioridadeVigentePermanece() {
        String id = naoOncologico(ClasseSwalis.B).getCaso().solicitacaoId();
        ajustar(id, TipoAjuste.ALTERAR_CLASSE, "D", "dor controlada na última consulta");
        ajustar(id, TipoAjuste.REJEITAR_REBAIXAMENTO, null, "dor continua limitando o trabalho");

        assertThat(agora(id).classe()).isEqualTo(ClasseSwalis.B);
        assertThat(agora(id).getEstadoRebaixamento()).isEqualTo("REJEITADO");
        // a sugestão e o histórico continuam guardados
        assertThat(registros(id, TipoAjuste.SUGERIR_REBAIXAMENTO)).isEqualTo(1);
        assertThat(registros(id, TipoAjuste.REJEITAR_REBAIXAMENTO)).isEqualTo(1);
        Dtos.Rebaixamento r = alertas.rebaixamentoDe(id);
        assertThat(r.estado()).isEqualTo("REJEITADO");
        assertThat(r.classeProposta()).isEqualTo("D");
        // a classe ainda pode subir com evidência nova
        evento(id, "biopsia", "positivo");
        assertThat(agora(id).classe()).isEqualTo(ClasseSwalis.A2);
    }

    // ---------- 4: queda de posição não é rebaixamento ----------

    @Test
    void teste4_quedaDePosicaoRelativaNaoEhRebaixamentoClinico() {
        ResultadoPaciente alvo = naoOncologico(ClasseSwalis.B);
        String id = alvo.getCaso().solicitacaoId();
        int posicaoAntes = alvo.getPosicao();
        // alguém que está atrás vira oncológico e passa à frente
        ResultadoPaciente atras = fila.atual().ativos().stream()
                .filter(r -> r.getPosicao() > posicaoAntes && !r.oncologico() && r.classe() == ClasseSwalis.D)
                .findFirst().orElseThrow();
        evento(atras.getCaso().solicitacaoId(), "biopsia", "positivo");

        ResultadoPaciente depois = agora(id);
        assertThat(depois.getPosicao()).isGreaterThan(posicaoAntes);
        assertThat(depois.classe()).isEqualTo(ClasseSwalis.B);
        assertThat(depois.getEstadoRebaixamento()).isNull();
        assertThat(ajustes.findBySolicitacaoIdOrderByCriadoEmDescIdDesc(id)).isEmpty();
        assertThat(alertas.rebaixamentos()).isEmpty();
    }

    // ---------- 5 a 7: oncológicos ----------

    @Test
    void teste5_oncologicoEmA1NaoPassaParaA2PorDecisaoManual() {
        String id = oncologico(ClasseSwalis.A1).getCaso().solicitacaoId();
        for (TipoAjuste tipo : List.of(TipoAjuste.ALTERAR_CLASSE, TipoAjuste.SUGERIR_REBAIXAMENTO)) {
            assertThatThrownBy(() -> ajustar(id, tipo, "A2", "laudo recente sem progressão"))
                    .isInstanceOf(Erros.RegraNegocio.class)
                    .hasMessageContaining("Regra de ouro")
                    .hasMessageContaining("nem de A1 para A2");
        }
        assertThat(agora(id).classe()).isEqualTo(ClasseSwalis.A1);
        assertThat(ajustes.findBySolicitacaoIdOrderByCriadoEmDescIdDesc(id)).isEmpty();
    }

    @Test
    void teste5b_confirmarClasseNaoDesfazPromocaoDeOncologico() {
        // antes, "Confirmar classe" voltava para a classe calculada: um oncológico promovido a A1 caía para A2
        String id = oncologico(ClasseSwalis.A2).getCaso().solicitacaoId();
        ajustar(id, TipoAjuste.ALTERAR_CLASSE, "A1", "cirurgião relata sangramento ativo");
        assertThat(agora(id).classe()).isEqualTo(ClasseSwalis.A1);
        ajustar(id, TipoAjuste.CONFIRMAR_CLASSE, null, "conferido com o laudo e o cirurgião");
        assertThat(agora(id).classe()).isEqualTo(ClasseSwalis.A1);
        assertThatThrownBy(() -> ajustar(id, TipoAjuste.ALTERAR_CLASSE, "A2", "voltar para a classe calculada"))
                .isInstanceOf(Erros.RegraNegocio.class);
        assertThat(agora(id).classe()).isEqualTo(ClasseSwalis.A1);
    }

    @Test
    void teste6_oncologicoEmA1NaoPassaParaA2PorEventoOuRecalculo() {
        String id = oncologico(ClasseSwalis.A2).getCaso().solicitacaoId();
        evento(id, "exame_imagem", "progressao");
        assertThat(agora(id).classe()).isEqualTo(ClasseSwalis.A1);

        // eventos que não pioram não tiram o A1
        evento(id, "consulta_reavaliacao", "melhora");
        evento(id, "exame_imagem", "estavel");
        evento(id, "biopsia", "negativo");
        assertThat(agora(id).classe()).isEqualTo(ClasseSwalis.A1);

        // reprocessamento que tira a progressão: o recálculo daria A2, mas o rebaixamento é bloqueado
        apagarEventosNovosERecalcular(id);
        ResultadoPaciente r = agora(id);
        assertThat(r.getClassificacao().classeCalculada()).isEqualTo(ClasseSwalis.A2);
        assertThat(r.classe()).isEqualTo(ClasseSwalis.A1);
        assertThat(r.getEstadoRebaixamento()).isEqualTo("BLOQUEADO");
        assertThat(registros(id, TipoAjuste.REBAIXAMENTO_BLOQUEADO)).isEqualTo(1);
        assertThat(alertas.rebaixamentoDe(id).estado()).isEqualTo("BLOQUEADO");
        // não há como confirmar: bloqueio não é sugestão
        assertThatThrownBy(() -> ajustar(id, TipoAjuste.CONFIRMAR_REBAIXAMENTO, null, "tentando confirmar"))
                .isInstanceOf(Erros.RegraNegocio.class);
        assertThatThrownBy(() -> ajustar(id, TipoAjuste.REBAIXAMENTO_BLOQUEADO, null, "registro manual indevido"))
                .isInstanceOf(Erros.RegraNegocio.class);
        // novos recálculos mantêm A1 sem duplicar o bloqueio
        fila.recalcular();
        assertThat(agora(id).classe()).isEqualTo(ClasseSwalis.A1);
        assertThat(registros(id, TipoAjuste.REBAIXAMENTO_BLOQUEADO)).isEqualTo(1);
        assertThat(GuardRegraDeOuro.respeitada(fila.atual().ativos())).isTrue();
    }

    @Test
    void teste6b_pacienteQueViraOncologicoComSugestaoPendenteNaoPodeSerRebaixado() {
        String id = naoOncologico(ClasseSwalis.B).getCaso().solicitacaoId();
        ajustar(id, TipoAjuste.ALTERAR_CLASSE, "D", "dor controlada na última consulta");
        evento(id, "biopsia", "positivo");
        assertThatThrownBy(() -> ajustar(id, TipoAjuste.CONFIRMAR_REBAIXAMENTO, null, "confirmando a sugestão antiga"))
                .isInstanceOf(Erros.RegraNegocio.class).hasMessageContaining("oncológico");
        assertThat(agora(id).classe()).isEqualTo(ClasseSwalis.A2);
        ajustar(id, TipoAjuste.REJEITAR_REBAIXAMENTO, null, "agora é oncológico, sugestão encerrada");
        assertThat(agora(id).classe()).isEqualTo(ClasseSwalis.A2);
    }

    @Test
    void teste7_oncologicoEmA2NaoPodeIrParaBCD() {
        String id = oncologico(ClasseSwalis.A2).getCaso().solicitacaoId();
        for (String c : List.of("B", "C", "D")) {
            assertThatThrownBy(() -> ajustar(id, TipoAjuste.ALTERAR_CLASSE, c, "teste de rebaixamento indevido"))
                    .isInstanceOf(Erros.RegraNegocio.class).hasMessageContaining("Regra de ouro");
        }
        assertThat(agora(id).classe()).isEqualTo(ClasseSwalis.A2);
        assertThat(alertas.rebaixamentos()).isEmpty();
    }

    // ---------- 8: urgência não oncológica continua podendo passar ----------

    @Test
    void teste8_naoOncologicoUrgentePodeFicarAFrenteDeOncologico() {
        var ativos = fila.atual().ativos();
        int ultimoA2Onco = ativos.stream().filter(r -> r.oncologico() && r.classe() == ClasseSwalis.A2)
                .mapToInt(ResultadoPaciente::getPosicao).max().orElseThrow();
        long urgentesNaFrente = ativos.stream()
                .filter(r -> !r.oncologico() && r.classe() == ClasseSwalis.A1 && r.getPosicao() < ultimoA2Onco).count();
        assertThat(urgentesNaFrente).isPositive();

        // e um paciente que ganha urgência estruturada nova também passa, sem quebrar a regra de ouro
        String id = naoOncologico(ClasseSwalis.D).getCaso().solicitacaoId();
        int antes = agora(id).getPosicao();
        evento(id, "pronto_socorro", "observacao");
        assertThat(agora(id).getPosicao()).isLessThan(antes);
        assertThat(GuardRegraDeOuro.respeitada(fila.atual().ativos())).isTrue();
    }

    // ---------- 9: divergência com a prioridade manual ----------

    @Test
    void teste9_divergenciaDePrioridadeEhSinalizadaSemCriarRebaixamentoFalso() {
        long esperados = 0;
        for (ResultadoPaciente r : fila.atual().ativos()) {
            String manual = r.getCaso().prioridadeManual();
            boolean urgente = r.classe() == ClasseSwalis.A1 || r.classe() == ClasseSwalis.A2;
            boolean semDisfuncao = r.classe() == ClasseSwalis.C || r.classe() == ClasseSwalis.D;
            boolean deveria = (urgente && !"alta".equals(manual)) || (semDisfuncao && "alta".equals(manual));
            boolean tem = r.getReavaliacoes().stream().anyMatch(x -> x.tipo() == TipoReavaliacao.PRIORIDADE_DIVERGENTE);
            assertThat(tem).as(r.getCaso().solicitacaoId()).isEqualTo(deveria);
            if (deveria) esperados++;
            assertThat(r.getEstadoRebaixamento()).isNull();
        }
        assertThat(esperados).isPositive();
        assertThat(TipoReavaliacao.PRIORIDADE_DIVERGENTE.titulo()).isEqualTo("Divergência de prioridade — revisão necessária");
        // "alta" com classe B não é divergência (B = dor ou disfunção importantes)
        assertThat(fila.atual().ativos().stream().filter(r -> "alta".equals(r.getCaso().prioridadeManual())
                && r.classe() == ClasseSwalis.B).flatMap(r -> r.getReavaliacoes().stream())
                .noneMatch(x -> x.tipo() == TipoReavaliacao.PRIORIDADE_DIVERGENTE)).isTrue();
        assertThat(ajustes.count()).isZero();
        assertThat(alertas.rebaixamentos()).isEmpty();
    }

    // ---------- 10 e 11: CSV oficial ----------

    @Test
    void teste10_csvOficialTemOs919EnaoMudaComRebaixamentos() {
        String antes = export.csv(fila.oficial());
        assertThat(antes.lines().count()).isEqualTo(919 + 1L);
        String b = naoOncologico(ClasseSwalis.B).getCaso().solicitacaoId();
        ajustar(b, TipoAjuste.ALTERAR_CLASSE, "D", "dor controlada na última consulta");
        ajustar(b, TipoAjuste.CONFIRMAR_REBAIXAMENTO, null, "revisado com o cirurgião");
        String onco = oncologico(ClasseSwalis.A2).getCaso().solicitacaoId();
        evento(onco, "exame_imagem", "progressao");
        apagarEventosNovosERecalcular(onco);
        assertThat(export.csv(fila.oficial())).isEqualTo(antes);
        assertThat(GuardRegraDeOuro.respeitada(fila.oficial().ativos())).isTrue();
    }

    @Test
    void teste11_consultaDuranteASubidaNaoFixaFilaOficialVazia() {
        // um FilaService novo, como na subida: a primeira consulta chega antes de o dataset estar no banco
        List<Solicitacao> ativas = solicitacoes.findBySituacao(Solicitacao.SituacaoCadastro.ATIVA);
        SolicitacaoRepository sol = mock(SolicitacaoRepository.class);
        when(sol.findBySituacao(Solicitacao.SituacaoCadastro.ATIVA)).thenReturn(List.of()).thenReturn(ativas);
        EventoClinicoRepository ev = mock(EventoClinicoRepository.class);
        when(ev.findByOrfaoFalseAndDuplicadoFalse()).thenReturn(List.of()).thenReturn(eventos.findByOrfaoFalseAndDuplicadoFalse());
        ProcedimentoRepository pr = mock(ProcedimentoRepository.class);
        when(pr.findAll()).thenReturn(procedimentos.findAll());
        AjusteReguladorRepository aj = mock(AjusteReguladorRepository.class);
        when(aj.findAllByOrderByCriadoEmAscIdAsc()).thenReturn(List.of());

        FilaService subindo = new FilaService(sol, ev, pr, aj, motor, fila.referencia(), pastaDataset);
        assertThat(subindo.atual().ativos()).as("consulta durante a subida").isEmpty();
        // a fila oficial não foi fixada vazia: com o dataset importado, sai completa
        FilaService.Snapshot oficial = subindo.oficial();
        assertThat(oficial.ativos()).hasSize(919);
        assertThat(export.csv(oficial)).isEqualTo(export.csv(fila.oficial()));
    }

    @Test
    void teste11b_oficialSemDatasetRecusaEmVezDeDevolverFilaVazia() {
        SolicitacaoRepository sol = mock(SolicitacaoRepository.class);
        when(sol.findBySituacao(Solicitacao.SituacaoCadastro.ATIVA)).thenReturn(List.of());
        EventoClinicoRepository ev = mock(EventoClinicoRepository.class);
        ProcedimentoRepository pr = mock(ProcedimentoRepository.class);
        AjusteReguladorRepository aj = mock(AjusteReguladorRepository.class);
        FilaService vazio = new FilaService(sol, ev, pr, aj, motor, fila.referencia(), pastaDataset);
        assertThatThrownBy(vazio::oficial).isInstanceOf(Erros.Conflito.class);
    }

    // ---------- reinício da demo não é rebaixamento ----------

    @Test
    void reiniciarADemoVoltaAoDatasetSemCriarSugestoes() {
        String id = naoOncologico(ClasseSwalis.D).getCaso().solicitacaoId();
        evento(id, "pronto_socorro", "observacao");
        decisao.resetar();
        assertThat(agora(id).classe()).isEqualTo(ClasseSwalis.D);
        assertThat(ajustes.count()).isZero();
        assertThat(fila.recalcular(Set.of()).ativos()).hasSize(919);
    }
}
