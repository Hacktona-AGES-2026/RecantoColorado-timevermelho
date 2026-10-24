package com.hackatona.timevermelho;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hackatona.timevermelho.api.dto.Conversor;
import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.dominio.SolicitacaoRepository;
import com.hackatona.timevermelho.motor.Evento;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.servico.DecisaoService;
import com.hackatona.timevermelho.servico.ExportService;
import com.hackatona.timevermelho.servico.FilaService;

/** Diabetes, HbA1c e comorbidades na fila batem com o cadastro e os exames, para os 919 pacientes. */
@SpringBootTest
class SaudePacienteTest {

    @Autowired FilaService fila;
    @Autowired DecisaoService decisao;
    @Autowired SolicitacaoRepository solicitacoes;
    @Autowired ExportService export;

    @AfterEach
    void voltarAoDataset() {
        decisao.resetar();
    }

    @Test
    void camposDeSaudeBatemComOCadastroEOsExames() {
        int diabeticos = 0, comHba1c = 0;
        for (ResultadoPaciente r : fila.atual().ativos()) {
            var c = r.getCaso();
            var s = solicitacoes.findById(c.solicitacaoId()).orElseThrow();
            Dtos.Saude saude = Conversor.item(r).saude();
            String com = c.comorbidades() == null ? "" : c.comorbidades().toLowerCase(Locale.ROOT);
            boolean dm = com.matches(".*\\bdm2\\b.*") || "sim".equals(c.flagDiabetico())
                    || (c.cidSecundario() != null && c.cidSecundario().matches("E1[0-4].*"));
            assertThat(saude.diabetes()).as(c.solicitacaoId()).isEqualTo(dm);
            if (dm) diabeticos++;

            Evento ultimo = c.eventos().stream().filter(e -> "hba1c".equals(e.tipo()) && e.valor() != null)
                    .max(Comparator.comparing(Evento::data)).orElse(null);
            Double esperado = ultimo != null ? ultimo.valor() : s.getHba1cUltima();
            assertThat(saude.hba1c()).as(c.solicitacaoId()).isEqualTo(esperado);
            if (esperado != null) {
                comHba1c++;
                assertThat(saude.hba1cAlta()).isEqualTo(esperado > 8.5);
                assertThat(saude.dataHba1c()).isEqualTo(ultimo != null ? ultimo.data() : s.getDataHba1cUltima());
            }
            long itens = c.comorbidades() == null ? 0 : java.util.Arrays.stream(c.comorbidades().split(";"))
                    .map(String::trim).filter(x -> !x.isEmpty() && !x.equalsIgnoreCase("nenhuma")).count();
            boolean dmSoForaDaLista = dm && !com.matches(".*\\bdm2\\b.*");
            assertThat(saude.comorbidades()).as(c.solicitacaoId()).hasSize((int) itens + (dmSoForaDaLista ? 1 : 0));
        }
        assertThat(diabeticos).isGreaterThan(250);
        assertThat(comHba1c).isGreaterThan(150);
    }

    /** Ninguém é chamado de diabético (fila, aptidão, BRAN, alertas) sem diabetes registrado no cadastro do CSV. */
    @Test
    void semDiabetesNoCadastroNinguemEChamadoDeDiabetico() {
        int hba1cAltaSemDiabetes = 0;
        for (ResultadoPaciente r : fila.atual().ativos()) {
            var c = r.getCaso();
            if (c.diabetesNoCadastro()) continue;
            var s = solicitacoes.findById(c.solicitacaoId()).orElseThrow();
            assertThat(Conversor.item(r).saude().diabetes()).as(c.solicitacaoId()).isFalse();
            assertThat(Conversor.item(r).saude().comorbidades()).as(c.solicitacaoId())
                    .noneMatch(x -> x.toLowerCase(Locale.ROOT).contains("diabet"));
            assertThat(com.hackatona.timevermelho.servico.AvaliadorAptidao.avaliar(r, null).fatores()).as(c.solicitacaoId())
                    .noneMatch(f -> chamaDeDiabetico(f.descricao()));
            assertThat(com.hackatona.timevermelho.servico.BranService.fatoresDeRisco(r, s)).as(c.solicitacaoId())
                    .noneMatch(SaudePacienteTest::chamaDeDiabetico);
            assertThat(r.getReavaliacoes()).as(c.solicitacaoId())
                    .noneMatch(x -> chamaDeDiabetico(x.mensagem()));
            if (Conversor.item(r).saude().hba1cAlta()) hba1cAltaSemDiabetes++;
        }
        // o dataset tem pacientes com HbA1c alta e nenhum registro de diabetes: eles mostram só o exame
        assertThat(hba1cAltaSemDiabetes).isPositive();
    }

    /** Fala em diabetes como diagnóstico (dizer "sem diabetes registrado" é permitido). */
    private static boolean chamaDeDiabetico(String texto) {
        return texto.toLowerCase(Locale.ROOT).replace("sem diabetes registrado", "").contains("diabet");
    }

    @Test
    void divergenciaDaFlagFicaSinalizada() {
        long divergentes = fila.atual().ativos().stream().map(r -> Conversor.item(r).saude())
                .filter(Dtos.Saude::divergenciaDiabetes).count();
        assertThat(divergentes).isPositive();
    }

    @Test
    void novaHbA1cApareceNaFilaEOsOutrosMantemAOrdem() {
        ResultadoPaciente p = fila.atual().ativos().get(50);
        String alvo = p.getCaso().solicitacaoId();
        List<String> antes = fila.atual().ativos().stream().map(r -> r.getCaso().solicitacaoId()).filter(x -> !x.equals(alvo)).toList();
        String csv = export.csv(fila.oficial());
        decisao.registrarEvento(new Dtos.NovoEvento(p.getCaso().solicitacaoId(), "hba1c", "resultado", 9.1, "controle",
                LocalDate.of(2026, 10, 23)));
        var saude = Conversor.item(fila.atual().get(p.getCaso().solicitacaoId())).saude();
        assertThat(saude.hba1c()).isEqualTo(9.1);
        assertThat(saude.origemHba1c()).isEqualTo("exame");
        assertThat(saude.hba1cAlta()).isTrue();
        assertThat(export.csv(fila.oficial())).isEqualTo(csv);
        // a HbA1c alta já era sinal de piora no score (motor existente): só o próprio paciente pode mudar de lugar
        assertThat(fila.atual().ativos().stream().map(r -> r.getCaso().solicitacaoId()).filter(x -> !x.equals(alvo)).toList())
                .isEqualTo(antes);
    }
}
