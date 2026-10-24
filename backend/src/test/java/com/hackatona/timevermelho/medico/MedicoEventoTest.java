package com.hackatona.timevermelho.medico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.dominio.AjusteReguladorRepository;
import com.hackatona.timevermelho.dominio.EventoClinicoRepository;
import com.hackatona.timevermelho.dominio.TipoAjuste;
import com.hackatona.timevermelho.medico.MedicoDtos.Grupo;
import com.hackatona.timevermelho.motor.GuardRegraDeOuro;
import com.hackatona.timevermelho.servico.DecisaoService;
import com.hackatona.timevermelho.servico.Erros;
import com.hackatona.timevermelho.servico.ExportService;
import com.hackatona.timevermelho.servico.FilaService;

/** Exame ou evento registrado (pelo médico ou pelo regulador) fica na auditoria com quem registrou. */
@SpringBootTest
class MedicoEventoTest {

    @Autowired MedicoService medicos;
    @Autowired FilaService fila;
    @Autowired DecisaoService decisao;
    @Autowired ExportService export;
    @Autowired AjusteReguladorRepository ajustes;
    @Autowired EventoClinicoRepository eventos;

    @AfterEach
    void voltarAoDataset() {
        decisao.resetar();
    }

    private MedicoDtos.MedicoDto cardio() {
        return medicos.listar().stream().filter(m -> m.especialidade().equals("CARDIOLOGIA")).findFirst().orElseThrow();
    }

    private Set<String> listasDo(MedicoDtos.MedicoDto m) {
        Set<String> ids = new HashSet<>();
        for (Grupo g : Grupo.values()) {
            medicos.filaDoMedico(m.id(), g, null, null, 0, 1000).itens().forEach(i -> ids.add(i.item().solicitacaoId()));
        }
        return ids;
    }

    private static Dtos.NovoEvento hba1c(String id) {
        return new Dtos.NovoEvento(id, "hba1c", "resultado", 9.4, "controle trazido pelo paciente", LocalDate.of(2026, 10, 23));
    }

    @Test
    void eventoDoMedicoFicaNaAuditoriaDoPacienteENasAlteracoesDoMedico() {
        var m = cardio();
        String id = medicos.filaDoMedico(m.id(), Grupo.CIRURGICO, null, null, 0, 1).itens().getFirst().item().solicitacaoId();
        String csv = export.csv(fila.oficial());

        medicos.registrarEvento(m.id(), hba1c(id));

        var a = ajustes.findBySolicitacaoIdOrderByCriadoEmDescIdDesc(id).getFirst();
        assertThat(a.getTipo()).isEqualTo(TipoAjuste.REGISTRAR_EVENTO);
        assertThat(a.getMedicoId()).isEqualTo(m.id());
        assertThat(a.getAutor()).contains(m.nome()).contains(m.crm());
        assertThat(a.getJustificativa()).contains("hba1c").contains("9,4").contains("controle trazido pelo paciente")
                .contains("Impacto:");
        assertThat(medicos.ajustesDoMedico(m.id())).extracting(Dtos.AjusteDto::solicitacaoId).containsExactly(id);
        // o evento guarda quem registrou
        assertThat(eventos.findAll()).filteredOn(e -> id.equals(e.getSolicitacaoId()) && e.isSintetico())
                .extracting(e -> e.getOrigem()).allMatch(o -> o.contains(m.nome()));
        assertThat(GuardRegraDeOuro.respeitada(fila.atual().ativos())).isTrue();
        assertThat(export.csv(fila.oficial())).isEqualTo(csv);
    }

    @Test
    void eventoDoReguladorFicaNaAuditoriaComOAutorInformado() {
        String id = fila.atual().ativos().get(30).getCaso().solicitacaoId();
        decisao.registrarEvento(new Dtos.NovoEvento(id, "pronto_socorro", "observacao", null, "dor no peito", null, "Ana (regulação)"));
        var a = ajustes.findBySolicitacaoIdOrderByCriadoEmDescIdDesc(id).getFirst();
        assertThat(a.getTipo()).isEqualTo(TipoAjuste.REGISTRAR_EVENTO);
        assertThat(a.getMedicoId()).isNull();
        assertThat(a.getAutor()).isEqualTo("Ana (regulação)");

        // sem autor: regulador
        String outro = fila.atual().ativos().get(31).getCaso().solicitacaoId();
        decisao.registrarEvento(hba1c(outro));
        assertThat(ajustes.findBySolicitacaoIdOrderByCriadoEmDescIdDesc(outro).getFirst().getAutor()).isEqualTo("regulador");
    }

    @Test
    void registroNaAuditoriaNaoMudaAFilaAlemDoProprioEvento() {
        String id = fila.atual().ativos().get(40).getCaso().solicitacaoId();
        decisao.registrarEvento(hba1c(id));
        List<String> depoisDoEvento = fila.atual().ativos().stream().map(r -> r.getCaso().solicitacaoId()).toList();
        // recalcular de novo (agora com a entrada de auditoria carregada) dá a mesma fila
        assertThat(fila.recalcular().ativos().stream().map(r -> r.getCaso().solicitacaoId()).toList()).isEqualTo(depoisDoEvento);
    }

    @Test
    void medicoNaoRegistraEventoDePacienteForaDasListasDele() {
        var m = cardio();
        Set<String> dele = listasDo(m);
        String fora = fila.atual().ativos().stream().map(r -> r.getCaso().solicitacaoId()).filter(x -> !dele.contains(x))
                .findFirst().orElseThrow();
        assertThatThrownBy(() -> medicos.registrarEvento(m.id(), hba1c(fora))).isInstanceOf(Erros.RegraNegocio.class)
                .hasMessageContaining("não está nas listas");
        assertThat(ajustes.findBySolicitacaoIdOrderByCriadoEmDescIdDesc(fora)).isEmpty();
    }

    @Test
    void eventoSemDataFicaComADataDeHojeENaoComADataDeReferencia() {
        String id = fila.atual().ativos().get(20).getCaso().solicitacaoId();
        LocalDate hoje = fila.hoje();
        assertThat(hoje).isAfterOrEqualTo(fila.referencia());
        decisao.registrarEvento(new Dtos.NovoEvento(id, "pronto_socorro", "observacao", null, "dor forte", null));
        assertThat(eventos.findAll()).filteredOn(e -> id.equals(e.getSolicitacaoId()) && e.isSintetico())
                .extracting(e -> e.getData()).containsExactly(hoje);
        assertThat(ajustes.findBySolicitacaoIdOrderByCriadoEmDescIdDesc(id).getFirst().getJustificativa())
                .contains(hoje.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    }

    @Test
    void eventoComDataNoFuturoNaoEhAceito() {
        String id = fila.atual().ativos().get(21).getCaso().solicitacaoId();
        assertThatThrownBy(() -> decisao.registrarEvento(new Dtos.NovoEvento(id, "hba1c", "resultado", 7.0, null,
                fila.hoje().plusDays(1)))).isInstanceOf(Erros.RegraNegocio.class).hasMessageContaining("futuro");
        assertThat(ajustes.findBySolicitacaoIdOrderByCriadoEmDescIdDesc(id)).isEmpty();
    }

    @Test
    void registrarEventoNaoEhUmTipoDeAjusteManual() {
        String id = fila.atual().ativos().get(10).getCaso().solicitacaoId();
        assertThatThrownBy(() -> decisao.ajustar(new Dtos.AjusteRequest(id, TipoAjuste.REGISTRAR_EVENTO, null,
                "tentativa direta", "regulador"))).isInstanceOf(Erros.RegraNegocio.class);
    }
}
