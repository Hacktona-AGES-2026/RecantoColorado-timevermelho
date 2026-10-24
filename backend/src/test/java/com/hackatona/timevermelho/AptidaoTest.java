package com.hackatona.timevermelho;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.dominio.RegistroAptidao;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.servico.AlertaService;
import com.hackatona.timevermelho.servico.AptidaoService;
import com.hackatona.timevermelho.servico.DecisaoService;
import com.hackatona.timevermelho.servico.Erros;
import com.hackatona.timevermelho.servico.ExportService;
import com.hackatona.timevermelho.servico.FilaService;

/** Aptidão cirúrgica: parecer pela saúde do paciente, recursos estimados e decisão humana, sem mudar a fila. */
@SpringBootTest
class AptidaoTest {

    @Autowired AptidaoService aptidao;
    @Autowired FilaService fila;
    @Autowired DecisaoService decisao;
    @Autowired AlertaService alertas;
    @Autowired ExportService export;

    @AfterEach
    void voltarAoDataset() {
        decisao.resetar();
    }

    private List<String> ordem() {
        return fila.atual().ativos().stream().map(r -> r.getCaso().solicitacaoId() + ":" + r.getPosicao() + ":" + r.classe()).toList();
    }

    private ResultadoPaciente primeiro(java.util.function.Predicate<ResultadoPaciente> p) {
        return fila.atual().ativos().stream().filter(p).findFirst().orElseThrow();
    }

    private AptidaoService.FatoresRequest fatores(boolean colesterol, boolean pressao, Double imc) {
        return new AptidaoService.FatoresRequest(colesterol, pressao, imc, false, false, null, "Dra. Teste", null);
    }

    @Test
    void diabetesDescontroladoPedeOtimizarAntesSemMudarAFila() {
        List<String> antes = ordem();
        String id = primeiro(r -> "OTIMIZAR_ANTES".equals(r.getAptidao())).getCaso().solicitacaoId();
        var a = aptidao.avaliar(id);
        assertThat(a.veredito()).isEqualTo("OTIMIZAR_ANTES");
        assertThat(a.fatores()).anyMatch(f -> f.bloqueante() && f.descricao().startsWith("Diabetes descontrolado"));
        assertThat(a.acoes()).anyMatch(x -> x.contains("glicemia"));
        assertThat(ordem()).isEqualTo(antes);
    }

    @Test
    void novaHbA1cMudaOParecerPelaUltimaMedida() {
        ResultadoPaciente p = primeiro(r -> "APTO".equals(r.getAptidao()) && "sim".equals(r.getCaso().flagDiabetico()));
        String id = p.getCaso().solicitacaoId();
        decisao.registrarEvento(new Dtos.NovoEvento(id, "hba1c", "resultado", 9.6, "HbA1c de controle", LocalDate.of(2026, 10, 20)));
        assertThat(aptidao.avaliar(id).veredito()).isEqualTo("OTIMIZAR_ANTES");
        decisao.registrarEvento(new Dtos.NovoEvento(id, "hba1c", "resultado", 7.2, "após controle", LocalDate.of(2026, 10, 23)));
        assertThat(aptidao.avaliar(id).veredito()).isNotEqualTo("OTIMIZAR_ANTES");
    }

    @Test
    void fatoresInformadosPeloMedicoEntramNoParecerENosRecursos() {
        ResultadoPaciente p = primeiro(r -> "APTO".equals(r.getAptidao()) && !r.oncologico());
        String id = p.getCaso().solicitacaoId();
        var antes = aptidao.avaliar(id);
        var soColesterol = aptidao.informarFatores(id, fatores(true, false, null));
        assertThat(soColesterol.veredito()).isEqualTo("APTO");
        assertThat(soColesterol.pontos()).isEqualTo(antes.pontos() + 1);
        var pressao = aptidao.informarFatores(id, fatores(true, true, 42.0));
        assertThat(pressao.veredito()).isEqualTo("OTIMIZAR_ANTES");
        assertThat(pressao.fatores()).anyMatch(f -> f.descricao().startsWith("Obesidade grave"));
        assertThat(pressao.recursos().tempoCirurgiaMin()).isGreaterThan(antes.recursos().tempoCirurgiaMin());
        assertThat(pressao.fatoresInformados().pressaoDescontrolada()).isTrue();
        assertThat(fila.atual().get(id).getAptidao()).isEqualTo("OTIMIZAR_ANTES");
    }

    @Test
    void riscoAltoERecursosDaCirurgiaCardiaca() {
        ResultadoPaciente p = primeiro(r -> "PROC022".equals(r.getCaso().procedimentoId()));
        var a = aptidao.avaliar(p.getCaso().solicitacaoId());
        assertThat(a.recursos().uti().name()).isEqualTo("PREVISTA");
        assertThat(a.recursos().permanenciaDias()).isGreaterThanOrEqualTo(7);
        assertThat(a.recursos().consumo().name()).isEqualTo("ALTO");
        long ressalvas = fila.atual().ativos().stream().filter(r -> "APTO_COM_RESSALVAS".equals(r.getAptidao())).count();
        assertThat(ressalvas).isPositive();
    }

    @Test
    void decisaoHumanaFicaRegistradaENaoMudaAFila() {
        List<String> antes = ordem();
        String id = primeiro(r -> "OTIMIZAR_ANTES".equals(r.getAptidao())).getCaso().solicitacaoId();
        assertThatThrownBy(() -> aptidao.decidir(id, new AptidaoService.DecisaoRequest(RegistroAptidao.Decisao.AGUARDAR_OTIMIZACAO,
                "HbA1c alta, controlar antes", null, null, "regulador", null))).isInstanceOf(Erros.RegraNegocio.class);
        var a = aptidao.decidir(id, new AptidaoService.DecisaoRequest(RegistroAptidao.Decisao.AGUARDAR_OTIMIZACAO,
                "HbA1c alta, controlar antes", "encaminhado à endocrinologia; repetir HbA1c", LocalDate.of(2026, 12, 1),
                "regulador", 1L));
        assertThat(a.decisao().decisao()).isEqualTo("AGUARDAR_OTIMIZACAO");
        assertThat(a.decisao().autor()).contains("CRM");
        assertThat(fila.atual().get(id).getDecisaoAptidao()).isEqualTo("AGUARDAR_OTIMIZACAO");
        assertThat(ordem()).isEqualTo(antes);
        // decidido sai da lista de pendências
        assertThat(alertas.alertas().reavaliacao()).filteredOn(g -> g.tipo().equals("APTIDAO_OTIMIZAR"))
                .allSatisfy(g -> assertThat(g.itens()).noneMatch(i -> i.solicitacaoId().equals(id)));
        aptidao.decidir(id, new AptidaoService.DecisaoRequest(RegistroAptidao.Decisao.LIBERADO, "HbA1c controlada, liberado",
                null, null, "regulador", null));
        assertThat(aptidao.avaliar(id).historicoDecisoes()).hasSize(2);
    }

    @Test
    void pendenciasDoReguladorBatemComOsPareceres() {
        long otimizar = fila.atual().ativos().stream().filter(r -> "OTIMIZAR_ANTES".equals(r.getAptidao())).count();
        var grupo = alertas.alertas().reavaliacao().stream().filter(g -> g.tipo().equals("APTIDAO_OTIMIZAR")).findFirst().orElseThrow();
        assertThat(grupo.total()).isEqualTo(otimizar);
    }

    @Test
    void oncologicoQuePrecisaOtimizarTemPrazoCurto() {
        String id = primeiro(ResultadoPaciente::oncologico).getCaso().solicitacaoId();
        var a = aptidao.informarFatores(id, fatores(false, true, null));
        assertThat(a.veredito()).isEqualTo("OTIMIZAR_ANTES");
        assertThat(a.observacaoPrioridade()).contains("oncológico").contains("prazo");
        assertThat(fila.atual().get(id).classe().name()).isIn("A1", "A2");
    }

    @Test
    void validaEReinicioApagaECsvOficialNaoMuda() {
        assertThatThrownBy(() -> aptidao.avaliar("SOL-0000-00000")).isInstanceOf(Erros.NaoEncontrado.class);
        String id = fila.atual().ativos().get(0).getCaso().solicitacaoId();
        assertThatThrownBy(() -> aptidao.informarFatores(id, new AptidaoService.FatoresRequest(true, false, null, false, false,
                null, null, 999_999L))).isInstanceOf(Erros.NaoEncontrado.class);
        String csv = export.csv(fila.oficial());
        aptidao.informarFatores(id, fatores(true, true, null));
        assertThat(export.csv(fila.oficial())).isEqualTo(csv);
        decisao.resetar();
        assertThat(aptidao.avaliar(id).fatoresInformados()).isNull();
    }
}
