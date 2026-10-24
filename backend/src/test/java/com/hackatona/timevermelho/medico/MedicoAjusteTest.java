package com.hackatona.timevermelho.medico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hackatona.timevermelho.dominio.AjusteReguladorRepository;
import com.hackatona.timevermelho.dominio.TipoAjuste;
import com.hackatona.timevermelho.medico.MedicoDtos.Grupo;
import com.hackatona.timevermelho.motor.ClasseSwalis;
import com.hackatona.timevermelho.motor.GuardRegraDeOuro;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.servico.DecisaoService;
import com.hackatona.timevermelho.servico.Erros;
import com.hackatona.timevermelho.servico.ExportService;
import com.hackatona.timevermelho.servico.FilaService;

/** O médico muda a prioridade dos pacientes das listas dele, com as mesmas regras e auditoria do regulador. */
@SpringBootTest
class MedicoAjusteTest {

    @Autowired MedicoService medicos;
    @Autowired FilaService fila;
    @Autowired DecisaoService decisao;
    @Autowired ExportService export;
    @Autowired AjusteReguladorRepository ajustes;

    @AfterEach
    void voltarAoDataset() {
        decisao.resetar();
    }

    private MedicoDtos.MedicoDto medico(String especialidade) {
        return medicos.listar().stream().filter(m -> m.especialidade().equals(especialidade)).findFirst()
                .orElseGet(() -> medicos.cadastrar(new MedicoDtos.NovoMedico("Teste " + especialidade,
                        "CRM-T " + especialidade, especialidade)));
    }

    /** Primeiro paciente cirúrgico do médico que satisfaz o filtro. */
    private ResultadoPaciente paciente(MedicoDtos.MedicoDto m, java.util.function.Predicate<ResultadoPaciente> filtro) {
        return medicos.filaDoMedico(m.id(), Grupo.CIRURGICO, null, null, 0, 500).itens().stream()
                .map(i -> fila.atual().get(i.item().solicitacaoId()))
                .filter(r -> !r.getClassificacao().ajustada()).filter(filtro).findFirst().orElseThrow();
    }

    private MedicoDtos.AjusteMedico pedido(String id, TipoAjuste tipo, String classe, boolean fora) {
        return new MedicoDtos.AjusteMedico(id, tipo, classe, "paciente relatou piora no ambulatório", fora);
    }

    @Test
    void medicoSobeAPrioridadeEFicaNaAuditoriaComNomeECrm() {
        var cardio = medico("CARDIOLOGIA");
        String id = paciente(cardio, r -> r.classe() == ClasseSwalis.B).getCaso().solicitacaoId();
        var imp = medicos.ajustar(cardio.id(), pedido(id, TipoAjuste.ALTERAR_CLASSE, "A1", true));

        assertThat(fila.atual().get(id).classe()).isEqualTo(ClasseSwalis.A1);
        assertThat(imp.classeDepois()).isEqualTo("A1");
        var a = ajustes.findBySolicitacaoIdOrderByCriadoEmDescIdDesc(id).getFirst();
        assertThat(a.getMedicoId()).isEqualTo(cardio.id());
        assertThat(a.isInformacaoForaDoSistema()).isTrue();
        assertThat(a.getAutor()).contains(cardio.nome()).contains(cardio.crm()).contains("Cardiologia");
        assertThat(medicos.ajustesDoMedico(cardio.id())).extracting(x -> x.solicitacaoId()).containsExactly(id);
        assertThat(GuardRegraDeOuro.respeitada(fila.atual().ativos())).isTrue();
    }

    @Test
    void mudancaDoMedicoNaoReordenaOsOutrosPacientes() {
        var cardio = medico("CARDIOLOGIA");
        String id = paciente(cardio, r -> r.classe() == ClasseSwalis.B).getCaso().solicitacaoId();
        List<String> antes = fila.atual().ativos().stream().map(r -> r.getCaso().solicitacaoId())
                .filter(x -> !x.equals(id)).toList();
        medicos.ajustar(cardio.id(), pedido(id, TipoAjuste.ALTERAR_CLASSE, "A2", false));
        List<String> depois = fila.atual().ativos().stream().map(r -> r.getCaso().solicitacaoId())
                .filter(x -> !x.equals(id)).toList();
        // só o paciente alterado muda de lugar; a ordem relativa de todos os outros fica igual
        assertThat(depois).isEqualTo(antes);
    }

    @Test
    void promocaoSemEvidenciaNaoPassaAFrenteDeQuemTemCancer() {
        var orto = medico("ORTOPEDIA");
        ResultadoPaciente p = paciente(orto, r -> r.classe() == ClasseSwalis.D && !r.oncologico());
        String id = p.getCaso().solicitacaoId();
        medicos.ajustar(orto.id(), pedido(id, TipoAjuste.ALTERAR_CLASSE, "A1", true));
        var snap = fila.atual();
        assertThat(snap.get(id).classe()).isEqualTo(ClasseSwalis.A1);
        int ultimoCidC = snap.ativos().stream().filter(r -> r.getCaso().oncologicoPeloCid())
                .mapToInt(ResultadoPaciente::getPosicao).max().orElseThrow();
        assertThat(snap.get(id).getPosicao()).isGreaterThan(ultimoCidC);
        assertThat(GuardRegraDeOuro.respeitada(snap.ativos())).isTrue();
    }

    @Test
    void medicoBaixarAClasseViraSugestaoParaORegulador() {
        var cardio = medico("CARDIOLOGIA");
        String id = paciente(cardio, r -> r.classe() == ClasseSwalis.B).getCaso().solicitacaoId();
        var imp = medicos.ajustar(cardio.id(), pedido(id, TipoAjuste.ALTERAR_CLASSE, "D", false));
        assertThat(fila.atual().get(id).classe()).isEqualTo(ClasseSwalis.B);
        assertThat(fila.atual().get(id).getEstadoRebaixamento()).isEqualTo("PENDENTE");
        assertThat(imp.mensagem()).contains("sugerido");
        var a = ajustes.findBySolicitacaoIdOrderByCriadoEmDescIdDesc(id).getFirst();
        assertThat(a.getTipo()).isEqualTo(TipoAjuste.SUGERIR_REBAIXAMENTO);
        assertThat(a.getMedicoId()).isEqualTo(cardio.id());
        // o médico não confirma o próprio rebaixamento: isso é do regulador
        assertThatThrownBy(() -> medicos.ajustar(cardio.id(), pedido(id, TipoAjuste.CONFIRMAR_REBAIXAMENTO, null, false)))
                .isInstanceOf(Erros.RegraNegocio.class);
    }

    @Test
    void medicoNuncaReduzAClasseDeOncologico() {
        var onco = medico("ONCOLOGIA");
        ResultadoPaciente p = paciente(onco, r -> r.oncologico() && r.classe() == ClasseSwalis.A1);
        assertThatThrownBy(() -> medicos.ajustar(onco.id(), pedido(p.getCaso().solicitacaoId(), TipoAjuste.ALTERAR_CLASSE, "A2", true)))
                .isInstanceOf(Erros.RegraNegocio.class).hasMessageContaining("nem de A1 para A2");
        assertThat(fila.atual().get(p.getCaso().solicitacaoId()).classe()).isEqualTo(ClasseSwalis.A1);
    }

    @Test
    void medicoSoMexeNosPacientesDasListasDele() {
        var oftalmo = medico("OFTALMOLOGIA");
        var cardio = medico("CARDIOLOGIA");
        String doCardio = paciente(cardio, r -> r.classe() == ClasseSwalis.B && !"Oftalmologia".equals(r.getCaso().especialidade()))
                .getCaso().solicitacaoId();
        boolean naListaDoOftalmo = medicos.filaDoMedico(oftalmo.id(), Grupo.POSSIVEL_AVALIACAO, null, null, 0, 500).itens().stream()
                .anyMatch(i -> i.item().solicitacaoId().equals(doCardio));
        assertThat(naListaDoOftalmo).isFalse();
        assertThatThrownBy(() -> medicos.ajustar(oftalmo.id(), pedido(doCardio, TipoAjuste.ALTERAR_CLASSE, "A1", false)))
                .isInstanceOf(Erros.RegraNegocio.class).hasMessageContaining("não está nas listas");
    }

    @Test
    void saidaEConfirmacoesContinuamComORegulador() {
        var cardio = medico("CARDIOLOGIA");
        String id = paciente(cardio, r -> true).getCaso().solicitacaoId();
        for (TipoAjuste t : List.of(TipoAjuste.CONFIRMAR_SAIDA, TipoAjuste.RECUSAR_SAIDA, TipoAjuste.REJEITAR_REBAIXAMENTO,
                TipoAjuste.REBAIXAMENTO_BLOQUEADO)) {
            assertThatThrownBy(() -> medicos.ajustar(cardio.id(), pedido(id, t, null, false)))
                    .as(t.name()).isInstanceOf(Erros.RegraNegocio.class);
        }
        assertThatThrownBy(() -> medicos.ajustar(999_999L, pedido(id, TipoAjuste.CONFIRMAR_CLASSE, null, false)))
                .isInstanceOf(Erros.NaoEncontrado.class);
    }

    @Test
    void auditoriaSeparaMedicoDeReguladorEFicaSeOMedicoForRemovido() {
        var novo = medicos.cadastrar(new MedicoDtos.NovoMedico("Dra. Auditoria", "CRM-RS 55555", "CARDIOLOGIA"));
        String id = paciente(novo, r -> r.classe() == ClasseSwalis.B).getCaso().solicitacaoId();
        medicos.ajustar(novo.id(), pedido(id, TipoAjuste.CONFIRMAR_CLASSE, null, false));
        decisao.ajustar(new com.hackatona.timevermelho.api.dto.Dtos.AjusteRequest(id, TipoAjuste.CONFIRMAR_CLASSE, null,
                "regulador conferiu o laudo", "regulador"));
        assertThat(medicos.ajustesDoMedico(novo.id())).hasSize(1);
        var hist = ajustes.findBySolicitacaoIdOrderByCriadoEmDescIdDesc(id);
        assertThat(hist).hasSize(2);
        assertThat(hist).filteredOn(a -> a.getMedicoId() == null).hasSize(1);
        medicos.remover(novo.id());
        assertThat(ajustes.findBySolicitacaoIdOrderByCriadoEmDescIdDesc(id))
                .anyMatch(a -> novo.id().equals(a.getMedicoId()) && a.getAutor().contains("Dra. Auditoria"));
    }

    @Test
    void csvOficialNaoMudaComAlteracoesDeMedicos() {
        String antes = export.csv(fila.oficial());
        var cardio = medico("CARDIOLOGIA");
        String id = paciente(cardio, r -> r.classe() == ClasseSwalis.B).getCaso().solicitacaoId();
        medicos.ajustar(cardio.id(), pedido(id, TipoAjuste.ALTERAR_CLASSE, "A1", true));
        assertThat(export.csv(fila.oficial())).isEqualTo(antes);
    }
}
