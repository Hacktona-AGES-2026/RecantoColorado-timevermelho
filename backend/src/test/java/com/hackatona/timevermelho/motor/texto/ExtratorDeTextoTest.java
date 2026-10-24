package com.hackatona.timevermelho.motor.texto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hackatona.timevermelho.motor.Sinal;
import com.hackatona.timevermelho.motor.TipoSinal;

class ExtratorDeTextoTest {

    final ExtratorDeTexto extrator = new ExtratorDeTexto(RegrasClinicas.carregarPadrao());

    private List<TipoSinal> tipos(String justificativa, String observacoes) {
        return extrator.extrair(justificativa, observacoes).stream().map(Sinal::tipo).toList();
    }

    // ---------- um teste por grupo do regras.json ----------

    @Test
    void alarmeGrave() {
        assertThat(tipos("Hernia com episodio de encarceramento ha 2 semanas.", null)).contains(TipoSinal.ALARME_GRAVE);
        assertThat(tipos("Paciente com sepse de foco biliar.", null)).contains(TipoSinal.ALARME_GRAVE);
    }

    @Test
    void suspeitaDeMalignidade() {
        assertThat(tipos("Lesao com crescimento rapido nos ultimos meses.", null))
                .contains(TipoSinal.SUSPEITA_MALIGNIDADE);
        assertThat(tipos("Refere perda de peso de 8 kg.", null)).contains(TipoSinal.SUSPEITA_MALIGNIDADE);
    }

    @Test
    void alarmeFuncional() {
        assertThat(tipos("Paciente acamado ha 3 meses, com ulcera de pressao.", null))
                .contains(TipoSinal.ALARME_FUNCIONAL);
    }

    @Test
    void psNoTexto() {
        assertThat(tipos("Ja teve 3 idas ao PS por crise de dor.", null)).contains(TipoSinal.PS_TEXTO);
        assertThat(tipos("Teve internacao recente por colecistite.", null)).contains(TipoSinal.PS_TEXTO);
    }

    @Test
    void relatoDePioraNaJustificativaENasObservacoes() {
        assertThat(tipos("Paciente refere piora importante dos sintomas.", null)).contains(TipoSinal.RELATA_PIORA);
        assertThat(tipos(null, "Paciente relata piora de sintomas por telefone.")).contains(TipoSinal.RELATA_PIORA);
    }

    @Test
    void melhoraSoVaiParaOsSinaisQuandoVemDasObservacoes() {
        assertThat(tipos(null, "Paciente relata melhora apos fisioterapia.")).contains(TipoSinal.MELHORA_RELATADA);
        assertThat(tipos("Paciente relata melhora apos fisioterapia.", null)).doesNotContain(TipoSinal.MELHORA_RELATADA);
    }

    @Test
    void desistenciaESaidaDeOutroServicoSoValemNasObservacoes() {
        assertThat(tipos(null, "Paciente desistiu da cirurgia.")).contains(TipoSinal.SAIDA_DESISTENCIA);
        assertThat(tipos(null, "Paciente operou pelo convenio.")).contains(TipoSinal.SAIDA_OUTRO_SERVICO);
        assertThat(tipos("Paciente desistiu da cirurgia.", null)).doesNotContain(TipoSinal.SAIDA_DESISTENCIA);
        assertThat(tipos("Operou pelo convenio.", null)).doesNotContain(TipoSinal.SAIDA_OUTRO_SERVICO);
    }

    // ---------- negações ----------

    @Test
    void negacaoNaMesmaFraseIgnoraOTermo() {
        assertThat(tipos("Sem sinais de encarceramento.", null)).isEmpty();
        assertThat(tipos("Paciente nega disfagia.", null)).isEmpty();
        assertThat(tipos("Ausencia de colangite ao exame.", null)).isEmpty();
    }

    @Test
    void negacaoNaoPassaParaOutraFrase() {
        assertThat(tipos("Sem febre. Apresenta colangite.", null)).contains(TipoSinal.ALARME_GRAVE);
        assertThat(tipos("Sem febre, com colangite.", null)).contains(TipoSinal.ALARME_GRAVE);
        assertThat(tipos("Nega tabagismo; encarceramento ha 2 dias.", null)).contains(TipoSinal.ALARME_GRAVE);
    }

    @Test
    void negacaoSoAlcancaAte3PalavrasDeDistancia() {
        assertThat(tipos("Nega dor ou febre ou vomitos ou colangite.", null)).contains(TipoSinal.ALARME_GRAVE);
    }

    // ---------- acentos, evidência e repetição ----------

    @Test
    void reconheceTermoComAcentoEMaiuscula() {
        assertThat(tipos("ICTERÍCIA obstrutiva em investigação.", null)).contains(TipoSinal.ALARME_GRAVE);
    }

    @Test
    void guardaOTrechoOriginalComoEvidenciaEAFonte() {
        var sinais = extrator.extrair("Paciente com Icterícia há 5 dias.", null);
        assertThat(sinais).hasSize(1);
        assertThat(sinais.getFirst().evidencia()).contains("Icterícia");
        assertThat(sinais.getFirst().fonte()).isEqualTo("justificativa_clinica");

        var deObs = extrator.extrair(null, "Paciente desistiu da cirurgia.");
        assertThat(deObs.getFirst().fonte()).isEqualTo("observacoes");
    }

    @Test
    void mesmoAchadoRepetidoViraUmSinalSo() {
        var sinais = extrator.extrair("Colangite recorrente. Nova colangite ontem.", null);
        assertThat(sinais.stream().filter(s -> s.tipo() == TipoSinal.ALARME_GRAVE)).hasSize(1);
    }

    @Test
    void textoNuloOuVazioNaoGeraSinal() {
        assertThat(extrator.extrair(null, null)).isEmpty();
        assertThat(extrator.extrair("  ", "")).isEmpty();
    }

    @Test
    void normalizarMantemOTamanhoParaOsTrechosBaterem() {
        String original = "Icterícia São João — hérnia";
        assertThat(ExtratorDeTexto.normalizar(original)).hasSize(original.length())
                .isEqualTo("ictericia sao joao — hernia");
    }
}
