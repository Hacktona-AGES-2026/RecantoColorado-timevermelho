package com.hackatona.timevermelho;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.dominio.FormaComunicacao;
import com.hackatona.timevermelho.dominio.ProcedimentoRepository;
import com.hackatona.timevermelho.dominio.RespostaCirurgia;
import com.hackatona.timevermelho.dominio.SolicitacaoRepository;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.servico.AlertaService;
import com.hackatona.timevermelho.servico.BranService;
import com.hackatona.timevermelho.servico.DecisaoService;
import com.hackatona.timevermelho.servico.DesejoCirurgiaService;
import com.hackatona.timevermelho.servico.Erros;
import com.hackatona.timevermelho.servico.ExportService;
import com.hackatona.timevermelho.servico.FilaService;

/** BRAN: rascunho por procedimento e paciente, registro com histórico, ligação com o desejo e nada muda na fila. */
@SpringBootTest
class BranTest {

    @Autowired BranService bran;
    @Autowired DesejoCirurgiaService desejos;
    @Autowired AlertaService alertas;
    @Autowired FilaService fila;
    @Autowired DecisaoService decisao;
    @Autowired ExportService export;
    @Autowired ProcedimentoRepository procedimentos;
    @Autowired SolicitacaoRepository solicitacoes;

    @AfterEach
    void voltarAoDataset() {
        decisao.resetar();
    }

    private Dtos.BranRequest pedido(Dtos.BranRascunho r, boolean compreendeu, Long medicoId) {
        return new Dtos.BranRequest(r.beneficios(), r.riscos(), r.alternativas(), r.nada(), FormaComunicacao.PRESENCIAL,
                "perguntou sobre o tempo de recuperação", compreendeu, "Dra. Teste", medicoId);
    }

    @Test
    void todoProcedimentoDoDatasetTemRascunhoComOsQuatroItens() {
        var porProcedimento = new java.util.HashMap<String, ResultadoPaciente>();
        fila.atual().ativos().forEach(r -> porProcedimento.putIfAbsent(r.getCaso().procedimentoId(), r));
        assertThat(porProcedimento.keySet()).containsExactlyInAnyOrderElementsOf(
                procedimentos.findAll().stream().map(p -> p.getId()).toList());
        for (ResultadoPaciente r : porProcedimento.values()) {
            var rasc = bran.rascunho(r.getCaso().solicitacaoId());
            assertThat(List.of(rasc.beneficios(), rasc.riscos(), rasc.alternativas(), rasc.nada()))
                    .as(r.getCaso().procedimentoId()).allSatisfy(t -> assertThat(t).hasSizeGreaterThan(30)
                            .doesNotStartWith("Descreva"));
            assertThat(rasc.riscos()).contains("numa escala de 1 a 5");
            assertThat(rasc.nada()).contains("classe " + r.classe()).contains(r.classe().prazoDias() + " dias");
            assertThat(rasc.aviso()).contains("Revise");
        }
    }

    @Test
    void rascunhoTrazOsFatoresDeRiscoDoProprioPaciente() {
        // diabético com HbA1c alta no cadastro: o rascunho pede controlar antes
        var s = solicitacoes.findAll().stream()
                .filter(x -> x.getHba1cUltima() != null && x.getHba1cUltima() > 8.5 && "sim".equals(x.getFlagDiabetico())
                        && fila.atual().get(x.getId()) != null)
                .findFirst().orElseThrow();
        var rasc = bran.rascunho(s.getId());
        assertThat(rasc.fatoresDeRisco()).anyMatch(f -> f.contains("diabetes descontrolado"));
        assertThat(rasc.riscos()).contains("No seu caso, aumentam o risco");

        // procedimento que precisa de UTI
        var uti = fila.atual().ativos().stream().filter(r -> "PROC022".equals(r.getCaso().procedimentoId())).findFirst().orElseThrow();
        assertThat(bran.rascunho(uti.getCaso().solicitacaoId()).riscos()).contains("costuma precisar de UTI");

        // oncológico: o "nada" lembra a prioridade
        var onco = fila.atual().ativos().stream().filter(ResultadoPaciente::oncologico).findFirst().orElseThrow();
        assertThat(bran.rascunho(onco.getCaso().solicitacaoId()).nada()).contains("câncer");
    }

    @Test
    void registraComHistoricoENaoMudaAFila() {
        List<String> antes = fila.atual().ativos().stream().map(r -> r.getCaso().solicitacaoId() + r.getPosicao()).toList();
        String id = fila.atual().ativos().get(20).getCaso().solicitacaoId();
        var rasc = bran.rascunho(id);
        bran.registrar(id, pedido(rasc, false, null));
        var ultimo = bran.registrar(id, pedido(rasc, true, 1L));
        assertThat(bran.historico(id)).hasSize(2);
        assertThat(bran.historico(id).getFirst().id()).isEqualTo(ultimo.id());
        assertThat(ultimo.compreendeu()).isTrue();
        assertThat(ultimo.autor()).contains("CRM");
        assertThat(ultimo.forma()).isEqualTo("PRESENCIAL");
        assertThat(fila.atual().ativos().stream().map(r -> r.getCaso().solicitacaoId() + r.getPosicao()).toList()).isEqualTo(antes);
        assertThat(alertas.alertas().recentes()).anyMatch(a -> a.solicitacaoId().equals(id) && a.mensagem().startsWith("BRAN comunicado"));
    }

    @Test
    void pendenciaDoDesejoMostraSeOBranJaFoiComunicado() {
        String id = fila.atual().ativos().get(30).getCaso().solicitacaoId();
        desejos.registrar(id, new Dtos.DesejoCirurgiaRequest(RespostaCirurgia.EM_DUVIDA, null, "enfermagem", null));
        String antes = mensagemDuvida(id);
        assertThat(antes).contains("BRAN ainda não comunicado");
        bran.registrar(id, pedido(bran.rascunho(id), true, null));
        assertThat(mensagemDuvida(id)).contains("BRAN comunicado em").contains("Dra. Teste");
    }

    private String mensagemDuvida(String id) {
        return alertas.alertas().reavaliacao().stream().filter(g -> g.tipo().equals("PACIENTE_EM_DUVIDA"))
                .flatMap(g -> g.itens().stream()).filter(i -> i.solicitacaoId().equals(id)).findFirst().orElseThrow().mensagem();
    }

    @Test
    void validaPacienteEMedicoEReinicioApaga() {
        assertThatThrownBy(() -> bran.rascunho("SOL-0000-00000")).isInstanceOf(Erros.NaoEncontrado.class);
        String id = fila.atual().ativos().get(0).getCaso().solicitacaoId();
        var rasc = bran.rascunho(id);
        assertThatThrownBy(() -> bran.registrar(id, pedido(rasc, true, 999_999L))).isInstanceOf(Erros.NaoEncontrado.class);
        String csv = export.csv(fila.oficial());
        bran.registrar(id, pedido(rasc, true, null));
        assertThat(export.csv(fila.oficial())).isEqualTo(csv);
        decisao.resetar();
        assertThat(bran.historico(id)).isEmpty();
    }
}
