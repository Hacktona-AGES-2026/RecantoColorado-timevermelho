package com.hackatona.timevermelho;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.dominio.TipoAjuste;
import com.hackatona.timevermelho.motor.Caso;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.servico.DecisaoService;
import com.hackatona.timevermelho.servico.ExportService;
import com.hackatona.timevermelho.servico.FilaService;

/**
 * Regra de ouro sem exceção, também na fila da tela: com os critérios do verificador oficial (reescritos aqui de forma
 * independente do motor, sobre todos os eventos, inclusive os novos), nenhum paciente com CID C fica abaixo de um
 * paciente sem urgência clínica. Vale para a fila inicial e depois de centenas de ações aleatórias.
 */
@SpringBootTest
class RegraDeOuroSemExcecaoTest {

    @Autowired FilaService fila;
    @Autowired DecisaoService decisao;
    @Autowired ExportService export;

    @AfterEach
    void voltarAoDataset() {
        decisao.resetar();
    }

    // cópia literal de data/verificar_fila_priorizada.py
    private static final Pattern KW_ALARME = Pattern.compile(
            "encarcer|estrangul|colangite|icter|pancreatite|enterorragia|melena|hematemese|suboclus|perda ponderal|disfagia|sepse|"
                    + "hidronefrose|perda de funcao|transfund|hemoptise|rouquidao|linfonodo.*(?:fixo|palpavel)|sangramento espontaneo|acamad|"
                    + "ulcera de pressao|quedas de repeticao|glaucoma facomorfico|retencao de co2|dano neurologico|psa em elevacao|dessaturacao|"
                    + "angina em repouso|sincope|crescimento rapido|retencao urinaria aguda|massa anexial|ca-125");
    private static final Set<String> CID_URGENTE = Set.of("I200", "K403", "K400", "K420", "K430", "K800", "N132", "I832");

    /** "Sem urgência clínica" do verificador oficial, sobre todos os eventos do paciente. */
    private static boolean semUrgenciaPeloVerificador(Caso c) {
        if (c.cid() != null && c.cid().startsWith("C")) return false;
        int dor = c.dor() == null ? 0 : c.dor();
        String lim = c.limitacao() == null || c.limitacao().isBlank() ? "nenhuma" : c.limitacao();
        if (dor > 3 || !(lim.equals("nenhuma") || lim.equals("leve"))) return false;
        if (CID_URGENTE.contains(c.cid())) return false;
        boolean piora = c.eventos() != null && c.eventos().stream().anyMatch(e -> switch (e.tipo()) {
            case "pronto_socorro", "internacao" -> true;
            case "biopsia" -> "positivo".equals(e.resultado());
            case "exame_imagem" -> "progressao".equals(e.resultado());
            case "consulta_reavaliacao" -> "piora".equals(e.resultado());
            default -> false;
        });
        if (piora) return false;
        String texto = c.justificativa() == null ? "" : c.justificativa().toLowerCase(Locale.ROOT);
        return !KW_ALARME.matcher(texto).find();
    }

    /** Lista as violações: oncológico (CID C) abaixo do primeiro paciente sem urgência. */
    private static List<String> violacoes(List<ResultadoPaciente> ativos) {
        int primeiroCalmo = -1;
        for (int i = 0; i < ativos.size(); i++) {
            if (semUrgenciaPeloVerificador(ativos.get(i).getCaso())) { primeiroCalmo = i; break; }
        }
        List<String> v = new ArrayList<>();
        if (primeiroCalmo < 0) return v;
        for (int i = primeiroCalmo + 1; i < ativos.size(); i++) {
            Caso c = ativos.get(i).getCaso();
            if (c.cid() != null && c.cid().startsWith("C")) {
                v.add(c.solicitacaoId() + " (CID " + c.cid() + ", posição " + (i + 1) + ") abaixo de "
                        + ativos.get(primeiroCalmo).getCaso().solicitacaoId() + " (posição " + (primeiroCalmo + 1) + ")");
            }
        }
        return v;
    }

    @Test
    void filaDaTelaRespeitaARegraComOsCriteriosDoVerificador() {
        assertThat(violacoes(fila.atual().ativos())).isEmpty();
        assertThat(violacoes(fila.oficial().ativos())).isEmpty();
    }

    @Test
    void urgenciaSoNoTextoNaoPassaAFrenteDeOncologicoNaTela() {
        List<ResultadoPaciente> ativos = fila.atual().ativos();
        int ultimoOnco = -1;
        for (int i = 0; i < ativos.size(); i++) if (ativos.get(i).getCaso().oncologicoPeloCid()) ultimoOnco = i;
        long naoReconhecidos = ativos.stream().filter(ResultadoPaciente::isUrgenciaNaoReconhecida).count();
        assertThat(naoReconhecidos).as("o dataset tem urgências que só o FilaVIVA lê").isPositive();
        for (int i = 0; i < ultimoOnco; i++) {
            assertThat(ativos.get(i).isUrgenciaNaoReconhecida()).as(ativos.get(i).getCaso().solicitacaoId()).isFalse();
        }
        // e ele recebe a explicação de por que ficou abaixo
        ResultadoPaciente r = ativos.stream().filter(ResultadoPaciente::isUrgenciaNaoReconhecida)
                .filter(ResultadoPaciente::isMovidoPelaRegraDeOuro).findFirst().orElseThrow();
        assertThat(r.getJustificativa()).contains("regra de ouro").contains("sem dado estruturado");
    }

    @Test
    void reavaliacaoComPioraRegistradaConfirmaAUrgenciaEOPacienteVoltaASubir() {
        ResultadoPaciente r = fila.atual().ativos().stream().filter(ResultadoPaciente::isUrgenciaNaoReconhecida)
                .filter(ResultadoPaciente::isMovidoPelaRegraDeOuro).findFirst().orElseThrow();
        String id = r.getCaso().solicitacaoId();
        int antes = r.getPosicao();
        decisao.registrarEvento(new Dtos.NovoEvento(id, "consulta_reavaliacao", "piora", null,
                "reavaliação confirma a urgência", null));
        ResultadoPaciente depois = fila.atual().get(id);
        assertThat(depois.isUrgenciaNaoReconhecida()).isFalse();
        assertThat(depois.getPosicao()).isLessThan(antes);
        assertThat(violacoes(fila.atual().ativos())).isEmpty();
    }

    @Test
    void nenhumaSequenciaDeAcoesQuebraARegraNaFilaDaTela() {
        Random rnd = new Random(20261024);
        String csvOficial = export.csv(fila.oficial());
        String[][] eventos = {
                {"pronto_socorro", "observacao"}, {"internacao", "alta"}, {"biopsia", "positivo"}, {"biopsia", "negativo"},
                {"exame_imagem", "progressao"}, {"exame_imagem", "estavel"}, {"consulta_reavaliacao", "piora"},
                {"consulta_reavaliacao", "melhora"}, {"contato_telefonico", "nao_atendeu"}, {"falta", "falta"},
        };
        String[] classes = {"A1", "A2", "B", "C", "D"};
        int acoes = 0;
        for (int passo = 0; passo < 300; passo++) {
            List<ResultadoPaciente> ativos = fila.atual().ativos();
            ResultadoPaciente alvo = ativos.get(rnd.nextInt(ativos.size()));
            String id = alvo.getCaso().solicitacaoId();
            try {
                switch (rnd.nextInt(6)) {
                    case 0 -> {
                        String[] ev = eventos[rnd.nextInt(eventos.length)];
                        decisao.registrarEvento(new Dtos.NovoEvento(id, ev[0], ev[1], null, "evento aleatório do teste", null));
                    }
                    case 1 -> decisao.registrarEvento(new Dtos.NovoEvento(id, "hba1c", "resultado",
                            6 + rnd.nextInt(60) / 10.0, null, LocalDate.of(2026, 10, 1 + rnd.nextInt(23))));
                    case 2, 3 -> decisao.ajustar(new Dtos.AjusteRequest(id, rnd.nextBoolean() ? TipoAjuste.ALTERAR_CLASSE
                            : TipoAjuste.SUGERIR_REBAIXAMENTO, classes[rnd.nextInt(classes.length)],
                            "ajuste aleatório do teste de regra de ouro", "teste"));
                    case 4 -> decisao.ajustar(new Dtos.AjusteRequest(id, TipoAjuste.FIXAR_POSICAO, null,
                            "posição aleatória do teste de regra de ouro", "teste", 1 + rnd.nextInt(Math.max(1, alvo.getPosicao()))));
                    default -> decisao.ajustar(new Dtos.AjusteRequest(id, TipoAjuste.CONFIRMAR_CLASSE, null,
                            "confirmação aleatória do teste de regra de ouro", "teste"));
                }
                acoes++;
            } catch (RuntimeException recusada) {
                // ação recusada pelas regras (ex.: rebaixar oncológico, posição abaixo da atual): também conta como proteção
            }
            assertThat(violacoes(fila.atual().ativos())).as("depois do passo " + passo).isEmpty();
        }
        assertThat(acoes).isGreaterThan(150);
        // a fila oficial nunca muda com o que acontece na tela
        assertThat(export.csv(fila.oficial())).isEqualTo(csvOficial);
        assertThat(violacoes(fila.oficial().ativos())).isEmpty();
    }
}
