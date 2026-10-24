package com.hackatona.timevermelho.medico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.dominio.AjusteReguladorRepository;
import com.hackatona.timevermelho.dominio.TipoAjuste;
import com.hackatona.timevermelho.medico.MedicoDtos.Grupo;
import com.hackatona.timevermelho.motor.GuardRegraDeOuro;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.motor.TipoSinal;
import com.hackatona.timevermelho.servico.DecisaoService;
import com.hackatona.timevermelho.servico.Erros;
import com.hackatona.timevermelho.servico.ExportService;
import com.hackatona.timevermelho.servico.FilaService;

/** O médico (ou o regulador) escolhe a posição de um paciente na fila, sem quebrar a ordem dos outros nem a regra de ouro. */
@SpringBootTest
class MedicoPosicaoTest {

    @Autowired MedicoService medicos;
    @Autowired FilaService fila;
    @Autowired DecisaoService decisao;
    @Autowired ExportService export;
    @Autowired AjusteReguladorRepository ajustes;

    private static final java.util.Set<TipoSinal> EVIDENCIA = java.util.EnumSet.of(TipoSinal.PS_RECENTE,
            TipoSinal.INTERNACAO_RECENTE, TipoSinal.PROGRESSAO_IMAGEM, TipoSinal.REAVALIACAO_PIORA, TipoSinal.CID_URGENTE);

    @AfterEach
    void voltarAoDataset() {
        decisao.resetar();
    }

    private MedicoDtos.MedicoDto medico(String especialidade) {
        return medicos.listar().stream().filter(m -> m.especialidade().equals(especialidade)).findFirst().orElseThrow();
    }

    /** Último paciente (pior posição) das listas do médico que satisfaz o filtro. */
    private ResultadoPaciente ultimo(MedicoDtos.MedicoDto m, java.util.function.Predicate<ResultadoPaciente> filtro) {
        List<ResultadoPaciente> l = new java.util.ArrayList<>();
        for (Grupo g : Grupo.values()) {
            medicos.filaDoMedico(m.id(), g, null, null, 0, 500).itens()
                    .forEach(i -> l.add(fila.atual().get(i.item().solicitacaoId())));
        }
        return l.stream().filter(r -> r.getStatus() == com.hackatona.timevermelho.motor.StatusFila.ATIVA).filter(filtro)
                .max(java.util.Comparator.comparing(ResultadoPaciente::getPosicao)).orElseThrow();
    }

    private static boolean temEvidencia(ResultadoPaciente r) {
        return r.getSinais().stream().anyMatch(s -> EVIDENCIA.contains(s.tipo()));
    }

    private Dtos.Impacto fixar(MedicoDtos.MedicoDto m, String id, Integer posicao) {
        return medicos.ajustar(m.id(), new MedicoDtos.AjusteMedico(id, TipoAjuste.FIXAR_POSICAO, null,
                "soube no ambulatório de piora importante", true, posicao));
    }

    private List<String> ordemSem(String id) {
        return fila.atual().ativos().stream().map(r -> r.getCaso().solicitacaoId()).filter(x -> !x.equals(id)).toList();
    }

    @Test
    void medicoLevaPacienteComUrgenciaRegistradaDoFimParaOPrimeiroLugar() {
        var cardio = medico("CARDIOLOGIA");
        ResultadoPaciente p = ultimo(cardio, r -> !r.oncologico() && temEvidencia(r));
        String id = p.getCaso().solicitacaoId();
        List<String> outrosAntes = ordemSem(id);

        var imp = fixar(cardio, id, 1);

        assertThat(fila.atual().get(id).getPosicao()).isEqualTo(1);
        assertThat(imp.posicaoDepois()).isEqualTo(1);
        assertThat(imp.mensagem()).contains("colocou o paciente na posição 1");
        // os outros só abrem espaço: a ordem entre eles não muda
        assertThat(ordemSem(id)).isEqualTo(outrosAntes);
        assertThat(GuardRegraDeOuro.respeitada(fila.atual().ativos())).isTrue();
        var a = ajustes.findBySolicitacaoIdOrderByCriadoEmDescIdDesc(id).getFirst();
        assertThat(a.getTipo()).isEqualTo(TipoAjuste.FIXAR_POSICAO);
        assertThat(a.getPosicaoFixada()).isEqualTo(1);
        assertThat(a.getMedicoId()).isEqualTo(cardio.id());
        assertThat(a.isInformacaoForaDoSistema()).isTrue();
        assertThat(fila.atual().get(id).getJustificativa()).contains("posição 1 escolhida manualmente");
    }

    @Test
    void semUrgenciaRegistradaNaoPassaAFrenteDeQuemTemCancer() {
        var orto = medico("ORTOPEDIA");
        ResultadoPaciente p = ultimo(orto, r -> !r.oncologico() && !temEvidencia(r));
        String id = p.getCaso().solicitacaoId();
        var imp = fixar(orto, id, 1);
        var snap = fila.atual();
        int ultimoProtegido = snap.ativos().stream().filter(ResultadoPaciente::isProtegido)
                .mapToInt(ResultadoPaciente::getPosicao).max().orElseThrow();
        assertThat(snap.get(id).getPosicao()).isEqualTo(ultimoProtegido + 1);
        assertThat(snap.get(id).isMovidoPelaRegraDeOuro()).isTrue();
        assertThat(imp.mensagem()).contains("regra de ouro");
        assertThat(GuardRegraDeOuro.respeitada(snap.ativos())).isTrue();
    }

    @Test
    void oncologicoVaiParaAPosicaoEscolhida() {
        var onco = medico("ONCOLOGIA");
        ResultadoPaciente p = ultimo(onco, ResultadoPaciente::oncologico);
        fixar(onco, p.getCaso().solicitacaoId(), 1);
        assertThat(fila.atual().get(p.getCaso().solicitacaoId()).getPosicao()).isEqualTo(1);
        assertThat(GuardRegraDeOuro.respeitada(fila.atual().ativos())).isTrue();
    }

    @Test
    void soDaParaSubirEDentroDaFila() {
        var cardio = medico("CARDIOLOGIA");
        ResultadoPaciente p = ultimo(cardio, r -> !r.oncologico());
        String id = p.getCaso().solicitacaoId();
        int atual = p.getPosicao();
        assertThatThrownBy(() -> fixar(cardio, id, atual)).isInstanceOf(Erros.RegraNegocio.class).hasMessageContaining("acima");
        assertThatThrownBy(() -> fixar(cardio, id, atual + 10)).isInstanceOf(Erros.RegraNegocio.class)
                .hasMessageContaining("rebaixamento");
        assertThatThrownBy(() -> fixar(cardio, id, 0)).isInstanceOf(Erros.RegraNegocio.class);
        assertThatThrownBy(() -> fixar(cardio, id, null)).isInstanceOf(Erros.RegraNegocio.class);
        assertThat(fila.atual().get(id).getPosicao()).isEqualTo(atual);
    }

    @Test
    void liberarVoltaParaAPosicaoCalculada() {
        var cardio = medico("CARDIOLOGIA");
        ResultadoPaciente p = ultimo(cardio, r -> !r.oncologico() && temEvidencia(r));
        String id = p.getCaso().solicitacaoId();
        int natural = p.getPosicao();
        assertThatThrownBy(() -> medicos.ajustar(cardio.id(), new MedicoDtos.AjusteMedico(id, TipoAjuste.LIBERAR_POSICAO,
                null, "liberando sem ter fixado antes", false))).isInstanceOf(Erros.RegraNegocio.class);
        fixar(cardio, id, 5);
        assertThat(fila.atual().get(id).getPosicao()).isEqualTo(5);
        medicos.ajustar(cardio.id(), new MedicoDtos.AjusteMedico(id, TipoAjuste.LIBERAR_POSICAO, null,
                "situação esclarecida com o paciente", false));
        assertThat(fila.atual().get(id).getPosicao()).isEqualTo(natural);
        assertThat(fila.atual().get(id).getPosicaoFixada()).isNull();
    }

    @Test
    void seAPosicaoCalculadaFicarMelhorElaVale() {
        var onco = medico("ONCOLOGIA");
        ResultadoPaciente p = ultimo(onco, r -> r.oncologico() && r.classe() == com.hackatona.timevermelho.motor.ClasseSwalis.A2);
        String id = p.getCaso().solicitacaoId();
        int pino = p.getPosicao() - 1;
        fixar(onco, id, pino);
        // progressão em imagem: vira A1 e a posição calculada passa a ser bem melhor que a escolhida
        decisao.registrarEvento(new Dtos.NovoEvento(id, "exame_imagem", "progressao", null, "progressão", null));
        assertThat(fila.atual().get(id).getPosicao()).isLessThan(pino);
    }

    @Test
    void reguladorTambemPodeEscolherAPosicao() {
        ResultadoPaciente p = fila.atual().ativos().stream().filter(ResultadoPaciente::oncologico)
                .max(java.util.Comparator.comparing(ResultadoPaciente::getPosicao)).orElseThrow();
        String id = p.getCaso().solicitacaoId();
        decisao.ajustar(new Dtos.AjusteRequest(id, TipoAjuste.FIXAR_POSICAO, null, "paciente com sangramento ativo",
                "regulador", 2));
        assertThat(fila.atual().get(id).getPosicao()).isEqualTo(2);
        assertThat(ajustes.findBySolicitacaoIdOrderByCriadoEmDescIdDesc(id).getFirst().getMedicoId()).isNull();
    }

    @Test
    void csvOficialNaoMudaERegraDeOuroValeComVariasPosicoesEscolhidas() {
        String csv = export.csv(fila.oficial());
        var cardio = medico("CARDIOLOGIA");
        var orto = medico("ORTOPEDIA");
        fixar(cardio, ultimo(cardio, r -> !r.oncologico() && temEvidencia(r)).getCaso().solicitacaoId(), 1);
        fixar(orto, ultimo(orto, r -> !r.oncologico() && !temEvidencia(r)).getCaso().solicitacaoId(), 1);
        fixar(orto, ultimo(orto, r -> !r.oncologico() && temEvidencia(r)).getCaso().solicitacaoId(), 1);
        var ativos = fila.atual().ativos();
        assertThat(GuardRegraDeOuro.respeitada(ativos)).isTrue();
        assertThat(ativos).hasSize(919);
        assertThat(ativos.stream().map(ResultadoPaciente::getPosicao).toList())
                .isEqualTo(java.util.stream.IntStream.rangeClosed(1, 919).boxed().toList());
        assertThat(export.csv(fila.oficial())).isEqualTo(csv);
    }
}
