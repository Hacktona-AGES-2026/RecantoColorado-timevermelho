package com.hackatona.timevermelho;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.dominio.RespostaCirurgia;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.motor.StatusFila;
import com.hackatona.timevermelho.servico.AlertaService;
import com.hackatona.timevermelho.servico.ConsultaService;
import com.hackatona.timevermelho.servico.DecisaoService;
import com.hackatona.timevermelho.servico.DesejoCirurgiaService;
import com.hackatona.timevermelho.servico.Erros;
import com.hackatona.timevermelho.servico.ExportService;
import com.hackatona.timevermelho.servico.FilaService;

/** Se o paciente quer ser operado: fica registrado com histórico, avisa o regulador e nunca muda a fila sozinho. */
@SpringBootTest
class DesejoCirurgiaTest {

    @Autowired DesejoCirurgiaService desejos;
    @Autowired FilaService fila;
    @Autowired DecisaoService decisao;
    @Autowired AlertaService alertas;
    @Autowired ConsultaService consulta;
    @Autowired ExportService export;

    @AfterEach
    void voltarAoDataset() {
        decisao.resetar();
    }

    private String paciente(int i) {
        return fila.atual().ativos().get(i).getCaso().solicitacaoId();
    }

    private Dtos.DesejoCirurgiaDto registrar(String id, RespostaCirurgia r, String obs) {
        return desejos.registrar(id, new Dtos.DesejoCirurgiaRequest(r, obs, "enfermeira Ana", null));
    }

    private List<String> ordem() {
        return fila.atual().ativos().stream().map(x -> x.getCaso().solicitacaoId() + ":" + x.classe() + ":" + x.getPosicao()).toList();
    }

    @Test
    void semRegistroONaoInformado() {
        assertThat(fila.atual().ativos()).allSatisfy(r -> assertThat(r.getDesejoCirurgia()).isNull());
        assertThat(consulta.paciente(paciente(0)).desejoCirurgia()).isNull();
    }

    @Test
    void registraComQuemEQuandoEGuardaOHistorico() {
        String id = paciente(10);
        registrar(id, RespostaCirurgia.EM_DUVIDA, "quer conversar com a família");
        var ultimo = registrar(id, RespostaCirurgia.QUER_OPERAR, "decidiu operar");
        var d = consulta.paciente(id);
        assertThat(d.desejoCirurgia().resposta()).isEqualTo("QUER_OPERAR");
        assertThat(d.desejoCirurgia().autor()).isEqualTo("enfermeira Ana");
        assertThat(d.desejoCirurgia().criadoEm()).isNotNull();
        assertThat(d.historicoDesejo()).extracting(Dtos.DesejoCirurgiaDto::resposta).containsExactly("QUER_OPERAR", "EM_DUVIDA");
        assertThat(d.item().desejoCirurgia()).isEqualTo("QUER_OPERAR");
        assertThat(ultimo.rotulo()).isEqualTo("Quer ser operado");
    }

    @Test
    void naoQuerOperarNaoMudaANemTiraDaFila() {
        List<String> antes = ordem();
        String id = paciente(3);
        registrar(id, RespostaCirurgia.NAO_QUER_OPERAR, "prefere tratamento clínico");
        assertThat(ordem()).isEqualTo(antes);
        ResultadoPaciente r = fila.atual().get(id);
        assertThat(r.getStatus()).isEqualTo(StatusFila.ATIVA);
        assertThat(r.getDesejoCirurgia()).isEqualTo("NAO_QUER_OPERAR");
    }

    @Test
    void naoQuerEEmDuvidaViramPendenciaDoRegulador() {
        String nao = paciente(5), duvida = paciente(7), quer = paciente(9);
        registrar(nao, RespostaCirurgia.NAO_QUER_OPERAR, null);
        registrar(duvida, RespostaCirurgia.EM_DUVIDA, null);
        registrar(quer, RespostaCirurgia.QUER_OPERAR, null);
        var grupos = alertas.alertas().reavaliacao();
        assertThat(grupos.get(0).tipo()).isEqualTo("PACIENTE_NAO_QUER_OPERAR");
        assertThat(grupos.get(0).itens()).extracting(Dtos.ItemReavaliacao::solicitacaoId).containsExactly(nao);
        assertThat(grupos.get(1).tipo()).isEqualTo("PACIENTE_EM_DUVIDA");
        assertThat(grupos.get(1).itens()).extracting(Dtos.ItemReavaliacao::solicitacaoId).containsExactly(duvida);
        assertThat(grupos).noneMatch(g -> g.itens().stream().anyMatch(i -> i.solicitacaoId().equals(quer)
                && g.tipo().startsWith("PACIENTE_")));
        assertThat(alertas.alertas().recentes()).anyMatch(a -> a.solicitacaoId().equals(nao) && a.mensagem().contains("não muda"));
    }

    @Test
    void saidaContinuaSendoDecisaoDoRegulador() {
        String id = paciente(4);
        registrar(id, RespostaCirurgia.NAO_QUER_OPERAR, "não quer mais operar");
        decisao.ajustar(new Dtos.AjusteRequest(id, com.hackatona.timevermelho.dominio.TipoAjuste.CONFIRMAR_SAIDA, null,
                "paciente confirmou que não quer operar", "regulador"));
        assertThat(fila.atual().get(id).getStatus()).isEqualTo(StatusFila.SAIDA_CONFIRMADA);
        // fora da fila, sai da lista de pendências
        assertThat(alertas.alertas().reavaliacao()).noneMatch(g -> g.tipo().equals("PACIENTE_NAO_QUER_OPERAR"));
    }

    @Test
    void validaPacienteEMedico() {
        assertThatThrownBy(() -> registrar("SOL-0000-00000", RespostaCirurgia.QUER_OPERAR, null)).isInstanceOf(Erros.NaoEncontrado.class);
        assertThatThrownBy(() -> desejos.registrar(paciente(0), new Dtos.DesejoCirurgiaRequest(RespostaCirurgia.QUER_OPERAR, null, null, 999_999L)))
                .isInstanceOf(Erros.NaoEncontrado.class);
    }

    @Test
    void medicoRegistradoAssinaComNomeECrm() {
        var d = desejos.registrar(paciente(0), new Dtos.DesejoCirurgiaRequest(RespostaCirurgia.EM_DUVIDA, null, null, 1L));
        assertThat(d.medicoId()).isEqualTo(1L);
        assertThat(d.autor()).contains("CRM");
    }

    @Test
    void reinicioDaDemoApagaEOCsvOficialNaoMuda() {
        String csv = export.csv(fila.oficial());
        registrar(paciente(2), RespostaCirurgia.NAO_QUER_OPERAR, null);
        assertThat(export.csv(fila.oficial())).isEqualTo(csv);
        decisao.resetar();
        assertThat(fila.atual().ativos()).allSatisfy(r -> assertThat(r.getDesejoCirurgia()).isNull());
    }
}
