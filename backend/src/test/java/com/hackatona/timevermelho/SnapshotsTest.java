package com.hackatona.timevermelho;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.dominio.TipoAjuste;
import com.hackatona.timevermelho.motor.ClasseSwalis;
import com.hackatona.timevermelho.motor.GuardRegraDeOuro;
import com.hackatona.timevermelho.motor.Reavaliacao;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.motor.TipoReavaliacao;
import com.hackatona.timevermelho.servico.ConsultaService;
import com.hackatona.timevermelho.servico.DecisaoService;
import com.hackatona.timevermelho.servico.ExportService;
import com.hackatona.timevermelho.servico.FilaService;
import com.hackatona.timevermelho.servico.SimulacaoService;

/**
 * Fila operacional × fila de referência da entrega, sobre o dataset inteiro (H2 em memória).
 * Testes C, D, E, G, H e I da revisão da regra de ouro. Cada teste volta a demo ao estado do dataset.
 */
@SpringBootTest
class SnapshotsTest {

    static final String CATARATA = "SOL-2026-00738";   // sem urgência no dataset
    static final String PROMOVIDO = "SOL-2026-00950";  // sem urgência no dataset
    static final List<String> TRONCO = List.of("SOL-2026-00960", "SOL-2026-00765"); // "CATE: TRONCO 80%"

    @Autowired FilaService fila;
    @Autowired DecisaoService decisao;
    @Autowired ExportService export;
    @Autowired ConsultaService consulta;
    @Autowired SimulacaoService simulacao;

    /** Eventos novos ficam com a data de hoje (não a data de referência do dataset). */
    private String hoje() {
        return fila.hoje().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    @AfterEach
    void voltarAoDataset() {
        decisao.resetar();
    }

    private static int ultimaPosicaoCidC(List<ResultadoPaciente> ativos) {
        return ativos.stream().filter(r -> r.getCaso().oncologicoPeloCid()).mapToInt(ResultadoPaciente::getPosicao).max().orElseThrow();
    }

    private String oficialCsv() {
        return export.csv(fila.oficial());
    }

    @Test
    void testeC_biopsiaNovaEhReconhecidaEProtegidaNaFilaOperacional() {
        String oficialAntes = oficialCsv();
        var antes = fila.atual().get(CATARATA);
        assertThat(antes.classe()).isEqualTo(ClasseSwalis.D);

        decisao.registrarEvento(new Dtos.NovoEvento(CATARATA, "biopsia", "positivo", null, "AP: carcinoma invasivo", null));
        var snap = fila.atual();
        var r = snap.get(CATARATA);
        assertThat(r.classe()).isEqualTo(ClasseSwalis.A2);
        assertThat(r.oncologico()).isTrue();
        assertThat(r.isProtegido()).isTrue();
        assertThat(r.isMovidoPelaRegraDeOuro()).isFalse();   // sem limitação artificial
        assertThat(r.getPosicao()).isLessThan(antes.getPosicao());
        assertThat(r.getJustificativa()).contains("Biópsia positiva em " + hoje());
        assertThat(GuardRegraDeOuro.respeitada(snap.ativos())).isTrue();
        // a fila de referência não muda com evento registrado depois
        assertThat(oficialCsv()).isEqualTo(oficialAntes);
        assertThat(fila.oficial().get(CATARATA).classe()).isEqualTo(ClasseSwalis.D);
    }

    @Test
    void testeD_promocaoComProgressaoNovaPassaSemBloqueioDoVerificador() {
        decisao.registrarEvento(new Dtos.NovoEvento(PROMOVIDO, "exame_imagem", "progressao", null, "TC com piora", null));
        decisao.ajustar(new Dtos.AjusteRequest(PROMOVIDO, TipoAjuste.ALTERAR_CLASSE, "A1",
                "progressão confirmada pelo cirurgião", "teste"));
        var snap = fila.atual();
        var r = snap.get(PROMOVIDO);
        assertThat(r.classe()).isEqualTo(ClasseSwalis.A1);
        assertThat(r.getClassificacao().classeCalculada()).isEqualTo(ClasseSwalis.B);
        assertThat(r.isSemUrgencia()).isFalse();
        assertThat(r.isMovidoPelaRegraDeOuro()).isFalse();
        assertThat(r.getPosicao()).isLessThan(ultimaPosicaoCidC(snap.ativos())); // passou à frente de cânceres A2
        assertThat(GuardRegraDeOuro.respeitada(snap.ativos())).isTrue();
    }

    @Test
    void testeE_troncoCoronarioFicaAbaixoDosOncologicosNasDuasFilasAteSerConfirmado() {
        // regra de ouro sem exceção: a urgência lida só no texto ("tronco 80%") não passa à frente de câncer nem na tela
        var operacional = fila.atual();
        var oficial = fila.oficial();
        int ultimoCOficial = ultimaPosicaoCidC(oficial.ativos());
        int ultimoCOperacional = ultimaPosicaoCidC(operacional.ativos());
        for (String id : TRONCO) {
            var op = operacional.get(id);
            assertThat(op.classe()).as(id).isEqualTo(ClasseSwalis.A1);
            assertThat(op.isUrgenciaNaoReconhecida()).as(id).isTrue();
            assertThat(op.isMovidoPelaRegraDeOuro()).as(id).isTrue();
            assertThat(op.getPosicao()).as(id).isGreaterThan(ultimoCOperacional);
            assertThat(op.isDivergenteDoVerificador()).as(id).isTrue();
            assertThat(op.getReavaliacoes()).extracting(Reavaliacao::tipo).contains(TipoReavaliacao.DIVERGENCIA_VERIFICADOR);
            var of = oficial.get(id);
            assertThat(of.isMovidoPelaRegraDeOuro()).as(id).isTrue();
            assertThat(of.getPosicao()).as(id).isGreaterThan(ultimoCOficial);
        }
    }

    @Test
    void testeF_promocaoSemEvidenciaNaoContornaARegraNoDatasetReal() {
        decisao.ajustar(new Dtos.AjusteRequest(CATARATA, TipoAjuste.ALTERAR_CLASSE, "A1", "sem achado novo", "teste"));
        var snap = fila.atual();
        var r = snap.get(CATARATA);
        assertThat(r.classe()).isEqualTo(ClasseSwalis.A1);
        assertThat(r.isMovidoPelaRegraDeOuro()).isTrue();
        assertThat(r.getPosicao()).isGreaterThan(ultimaPosicaoCidC(snap.ativos()));
        assertThat(GuardRegraDeOuro.respeitada(snap.ativos())).isTrue();
    }

    @Test
    void eventoComSepseNoTextoReclassificaPeloEstruturadoEPedeRevisaoUrgente() {
        String oficialAntes = oficialCsv();
        var imp = decisao.registrarEvento(new Dtos.NovoEvento(CATARATA, "internacao", "alta", 15.0,
                "Internado em UTI por sepse, hernia encarcerada com risco de estrangulamento", null));
        var snap = fila.atual();
        var r = snap.get(CATARATA);
        assertThat(r.classe()).isEqualTo(ClasseSwalis.B);             // a internação (estruturada) sobe para B
        assertThat(r.getReavaliacoes()).extracting(Reavaliacao::tipo).contains(TipoReavaliacao.REVISAO_URGENTE);
        assertThat(r.getPosicao()).isGreaterThan(ultimaPosicaoCidC(snap.ativos())); // o texto não passa os cânceres
        assertThat(imp.mensagem()).contains("revisão urgente");
        assertThat(GuardRegraDeOuro.respeitada(snap.ativos())).isTrue();
        assertThat(oficialCsv()).isEqualTo(oficialAntes);
        // o regulador revisa e promove: o atendimento registrado (internação) é a evidência, e ele passa
        decisao.ajustar(new Dtos.AjusteRequest(CATARATA, TipoAjuste.ALTERAR_CLASSE, "A1",
                "revisão clínica: sepse confirmada na internação", "regulador"));
        var depois = fila.atual();
        assertThat(depois.get(CATARATA).getPosicao()).isLessThan(ultimaPosicaoCidC(depois.ativos()));
        assertThat(depois.get(CATARATA).getJustificativa()).contains("evidência da promoção: Internação em " + hoje());
        assertThat(GuardRegraDeOuro.respeitada(depois.ativos())).isTrue();
    }

    /** Os 12 pacientes cuja urgência (A1) está só no texto do médico: caíam para D se o texto fosse ignorado. */
    static final List<String> URGENCIA_SO_NO_TEXTO = List.of("SOL-2025-00482", "SOL-2026-00025", "SOL-2026-00195",
            "SOL-2025-00520", "SOL-2026-00061", "SOL-2026-00110", "SOL-2026-00361", "SOL-2026-00202", "SOL-2024-00149",
            "SOL-2023-00079", "SOL-2026-00960", "SOL-2026-00765");

    @Test
    void osDozeComUrgenciaSoNoTextoNaoSaoRebaixadosEFicamEmRevisaoUrgente() {
        for (var snap : List.of(fila.atual(), fila.oficial())) {
            for (String id : URGENCIA_SO_NO_TEXTO) {
                var r = snap.get(id);
                String onde = snap.info().tipo() + " " + id;
                assertThat(r.classe()).as(onde).isEqualTo(ClasseSwalis.A1);           // não rebaixado
                assertThat(r.getClasseEstruturada()).as(onde).isEqualTo(ClasseSwalis.D);
                assertThat(r.isClassePorTexto()).as(onde).isTrue();
                assertThat(r.getJustificativa()).as(onde).contains("classe provisória");
                assertThat(r.getReavaliacoes()).as(onde).filteredOn(x -> x.tipo() == TipoReavaliacao.REVISAO_URGENTE)
                        .singleElement().satisfies(x -> assertThat(x.mensagem()).contains("justificativa_clinica")
                                .contains("não é confirmação médica"));
            }
        }
        // na fila operacional, só os 2 de tronco ficam acima de cânceres sem o verificador reconhecer (já documentado);
        // na de referência, os 2 ficam abaixo de todos os cânceres, como o verificador exige
        int ultimoC = ultimaPosicaoCidC(fila.oficial().ativos());
        for (String id : TRONCO) assertThat(fila.oficial().get(id).getPosicao()).isGreaterThan(ultimoC);
    }

    @Test
    void regressaoDoPacienteDaDemo() {
        String demo = "SOL-2026-00351";
        var r = fila.atual().get(demo);
        assertThat(r.classe()).isEqualTo(ClasseSwalis.A2);
        assertThat(r.isClassePorTexto()).isFalse(); // A2 vem da biópsia (estruturada), não do texto
        assertThat(r.getPosicao()).isLessThan(r.getPosicaoManual());
        assertThat(r.getClassificacao().determinantes()).anySatisfy(s -> assertThat(s.evidencia()).contains("NEOPLASIA MALIGNA"));
        var imp = decisao.registrarEvento(new Dtos.NovoEvento(demo, "exame_imagem", "progressao", null, "TC: aumento da lesão", null));
        assertThat(imp.classeAntes()).isEqualTo("A2");
        assertThat(imp.classeDepois()).isEqualTo("A1");
        assertThat(imp.posicaoDepois()).isLessThan(imp.posicaoAntes());
        assertThat(GuardRegraDeOuro.respeitada(fila.atual().ativos())).isTrue();
    }

    @Test
    void relatoGravePorTelefonePedeRevisaoUrgenteENaoSobeSozinho() {
        var imp = decisao.registrarEvento(new Dtos.NovoEvento(CATARATA, "contato_telefonico", "atendeu", null,
                "Familiar informa internacao por sepse em outro hospital", null));
        var snap = fila.atual();
        var r = snap.get(CATARATA);
        // não vira A1: continua D (só ganha um pouco de score dentro da classe) e abaixo de todos os cânceres
        assertThat(r.classe()).isEqualTo(ClasseSwalis.D);
        assertThat(r.getPosicao()).isGreaterThan(ultimaPosicaoCidC(snap.ativos()));
        assertThat(r.getReavaliacoes()).extracting(Reavaliacao::tipo).contains(TipoReavaliacao.REVISAO_URGENTE);
        assertThat(imp.mensagem()).contains("revisão urgente");
    }

    @Test
    void promocaoDeDorModeradaSemEvidenciaNaoUltrapassaNoDatasetReal() {
        String moderado = "SOL-2025-00173"; // dor 6, classe C, sem eventos
        assertThat(fila.atual().get(moderado).classe()).isEqualTo(ClasseSwalis.C);
        var imp = decisao.ajustar(new Dtos.AjusteRequest(moderado, TipoAjuste.ALTERAR_CLASSE, "A1", "pedido do cirurgião", "teste"));
        var snap = fila.atual();
        var r = snap.get(moderado);
        assertThat(r.classe()).isEqualTo(ClasseSwalis.A1);
        assertThat(r.isPromocaoSemEvidencia()).isTrue();
        assertThat(r.getPosicao()).isGreaterThan(ultimaPosicaoCidC(snap.ativos()));
        assertThat(imp.mensagem()).contains("sem evidência de agravamento registrada");
        // registrada a evidência, a mesma promoção passa
        decisao.registrarEvento(new Dtos.NovoEvento(moderado, "consulta_reavaliacao", "piora", null, "Piora clínica", null));
        var depois = fila.atual();
        assertThat(depois.get(moderado).isPromocaoSemEvidencia()).isFalse();
        assertThat(depois.get(moderado).getPosicao()).isLessThan(ultimaPosicaoCidC(depois.ativos()));
        assertThat(GuardRegraDeOuro.respeitada(depois.ativos())).isTrue();
    }

    @Test
    void nenhumSemUrgenciaAcimaDeCidCNasDuasFilasDepoisDeMuitasAcoes() {
        String oficialAntes = oficialCsv();
        var ids = fila.atual().ativos().stream().map(r -> r.getCaso().solicitacaoId()).toList();
        var rnd = new java.util.Random(7);
        String[][] eventos = {{"pronto_socorro", "observacao", "PS"}, {"internacao", "alta", "Internado por sepse"},
                {"exame_imagem", "progressao", "Progressao"}, {"contato_telefonico", "atendeu", "refere hernia encarcerada"},
                {"consulta_reavaliacao", "estavel", "Sem sinais de encarceramento"}};
        for (int i = 0; i < 40; i++) {
            String id = ids.get(rnd.nextInt(ids.size()));
            if (rnd.nextBoolean()) {
                String[] e = eventos[rnd.nextInt(eventos.length)];
                decisao.registrarEvento(new Dtos.NovoEvento(id, e[0], e[1], null, e[2], null));
            } else if (!fila.atual().get(id).oncologico()) {
                decisao.ajustar(new Dtos.AjusteRequest(id, TipoAjuste.ALTERAR_CLASSE, "A1", "teste de muitas ações", "t"));
            }
        }
        for (var snap : List.of(fila.atual(), fila.oficial())) {
            var ativos = snap.ativos();
            int ultimo = ultimaPosicaoCidC(ativos);
            assertThat(ativos.stream().filter(r -> r.getPosicao() < ultimo).filter(GuardRegraDeOuro::naoPodeFicarAcimaDeProtegido))
                    .as(snap.info().tipo()).isEmpty();
            assertThat(GuardRegraDeOuro.respeitada(ativos)).as(snap.info().tipo()).isTrue();
        }
        assertThat(oficialCsv()).isEqualTo(oficialAntes);
    }

    @Test
    void testeG_simulacaoNaoMudaNenhumaDasFilas() {
        var operacionalAntes = fila.atual();
        String oficialAntes = oficialCsv();
        var sim = simulacao.simular(new Dtos.SimulacaoRequest(CATARATA, 9, "grave",
                List.of(new Dtos.EventoHipotetico("biopsia", "positivo", null, "hipotético")), "Episodio de encarceramento."));
        assertThat(sim.depois().classe()).isNotEqualTo(sim.antes().classe());
        assertThat(fila.atual()).isSameAs(operacionalAntes);
        assertThat(fila.atual().info().versao()).isEqualTo(operacionalAntes.info().versao());
        assertThat(fila.atual().get(CATARATA).classe()).isEqualTo(ClasseSwalis.D);
        assertThat(oficialCsv()).isEqualTo(oficialAntes);
    }

    @Test
    void testeH_apiECsvOperacionalSaoOMesmoSnapshot() {
        decisao.registrarEvento(new Dtos.NovoEvento(CATARATA, "biopsia", "positivo", null, "AP positivo", null));
        var snap = fila.atual();
        String csv = export.csv(snap);
        List<String[]> linhas = new ArrayList<>();
        try (var parser = org.apache.commons.csv.CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get()
                .parse(new java.io.StringReader(csv))) {
            parser.forEach(rec -> linhas.add(new String[]{rec.get(0), rec.get(1), rec.get(2)}));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        List<Dtos.ItemFila> api = new ArrayList<>();
        for (int p = 0; p < 2; p++) {
            var pagina = consulta.fila(null, null, null, "ATIVA", null, p, 500);
            assertThat(pagina.snapshot().versao()).isEqualTo(snap.info().versao());
            assertThat(pagina.snapshot().tipo()).isEqualTo(FilaService.OPERACIONAL);
            api.addAll(pagina.itens());
        }
        assertThat(linhas).hasSameSizeAs(api);
        for (int i = 0; i < api.size(); i++) {
            assertThat(linhas.get(i)[0]).isEqualTo(api.get(i).solicitacaoId());
            assertThat(Integer.parseInt(linhas.get(i)[1])).isEqualTo(api.get(i).posicao());
            assertThat(linhas.get(i)[2]).isEqualTo(api.get(i).justificativa());
        }
        assertThat(ExportService.nomeArquivo(snap)).startsWith("fila_operacional_" + snap.info().versao());
    }

    @Test
    void testeI_filaDeReferenciaEhReproduzivelEIgualAoEntregavel() throws IOException {
        var oficial = fila.oficial();
        assertThat(oficial.info().tipo()).isEqualTo(FilaService.OFICIAL);
        assertThat(oficial.info().versao()).startsWith("ref-");
        assertThat(oficial.info().eventosRegistrados()).isZero();
        assertThat(GuardRegraDeOuro.respeitada(oficial.ativos())).isTrue();
        assertThat(ExportService.nomeArquivo(oficial)).isEqualTo("fila_priorizada.csv");
        // igual, byte a byte (fora quebra de linha), ao fila_priorizada.csv do repositório
        String entregavel = Files.readString(Path.of("../fila_priorizada.csv")).replace("\r\n", "\n");
        assertThat(oficialCsv()).isEqualTo(entregavel);
        // e continua igual depois de eventos e decisões na fila operacional
        decisao.registrarEvento(new Dtos.NovoEvento(PROMOVIDO, "pronto_socorro", "observacao", null, "PS", null));
        decisao.ajustar(new Dtos.AjusteRequest(PROMOVIDO, TipoAjuste.ALTERAR_CLASSE, "A1", "teste de independência", "t"));
        assertThat(oficialCsv()).isEqualTo(entregavel);
    }
}
