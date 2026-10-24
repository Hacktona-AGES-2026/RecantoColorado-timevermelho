package com.hackatona.timevermelho.agente;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** T3, T33 e T34: prompts em arquivos, validados na inicialização; sem regras o agente fica desligado. */
class PromptsAgenteTest {

    static final String SISTEMA = "Você é o assistente do FilaVIVA. Responda só sobre saúde e sobre a aplicação.";
    static final String FILTRO = "Classifique a mensagem em uma categoria e responda em JSON.";
    static final String REGRAS = "Regra 1: o regulador decide a classe do paciente.";

    @Test
    void comTudoPreenchidoOAgenteFicaProntoEJuntaPromptEregras() {
        var p = PromptsAgente.montar(SISTEMA, REGRAS, FILTRO, true);
        assertThat(p.pronto()).isTrue();
        assertThat(p.motivoDesligado()).isNull();
        assertThat(p.textoSistema()).startsWith(SISTEMA).contains(REGRAS);
        assertThat(p.promptFiltro()).isEqualTo(FILTRO);
        assertThat(p.versao()).hasSize(12).matches("[0-9a-f]{12}");
    }

    @Test
    void regrasVaziasOuSoComComentarioDesligamOAgente() {
        var vazio = PromptsAgente.montar(SISTEMA, "   \n", FILTRO, true);
        var comentario = PromptsAgente.montar(SISTEMA, "<!-- ainda não preenchido -->\n", FILTRO, true);
        assertThat(vazio.pronto()).isFalse();
        assertThat(comentario.pronto()).isFalse();
        assertThat(comentario.motivoDesligado()).contains("regras de negócio ainda não preenchidas");
    }

    @Test
    void comentarioNoArquivoDeRegrasNaoVaiParaOModelo() {
        var p = PromptsAgente.montar(SISTEMA, "<!-- nota interna -->\n" + REGRAS, FILTRO, true);
        assertThat(p.pronto()).isTrue();
        assertThat(p.textoSistema()).contains(REGRAS).doesNotContain("nota interna");
    }

    @Test
    void sePermitirSemRegrasOAgenteLiga() {
        assertThat(PromptsAgente.montar(SISTEMA, "", FILTRO, false).pronto()).isTrue();
    }

    @Test
    void arquivoAusenteDesligaComOMotivo() {
        assertThat(PromptsAgente.montar(null, REGRAS, FILTRO, true).motivoDesligado()).contains("system-prompt.md");
        assertThat(PromptsAgente.montar(SISTEMA, null, FILTRO, true).motivoDesligado()).contains("regras-negocio.md");
        assertThat(PromptsAgente.montar(SISTEMA, REGRAS, null, true).motivoDesligado()).contains("filtro-escopo.md");
    }

    @Test
    void systemPromptVazioOuInvalidoDesliga() {
        assertThat(PromptsAgente.montar("  ", REGRAS, FILTRO, true).pronto()).isFalse();
        assertThat(PromptsAgente.montar(SISTEMA + "\u0000", REGRAS, FILTRO, true).motivoDesligado()).contains("inválido");
        assertThat(PromptsAgente.montar(SISTEMA, REGRAS + "�", FILTRO, true).pronto()).isFalse();
    }

    @Test
    void arquivoGrandeDemaisDesliga() {
        var grande = "x".repeat(100_001);
        assertThat(PromptsAgente.montar(SISTEMA, grande, FILTRO, true).motivoDesligado()).contains("grande demais");
    }

    @Test
    void aVersaoMudaQuandoQualquerTextoMuda() {
        var base = PromptsAgente.montar(SISTEMA, REGRAS, FILTRO, true).versao();
        assertThat(PromptsAgente.montar(SISTEMA + "!", REGRAS, FILTRO, true).versao()).isNotEqualTo(base);
        assertThat(PromptsAgente.montar(SISTEMA, REGRAS + "!", FILTRO, true).versao()).isNotEqualTo(base);
        assertThat(PromptsAgente.montar(SISTEMA, REGRAS, FILTRO + "!", true).versao()).isNotEqualTo(base);
        assertThat(PromptsAgente.montar(SISTEMA, REGRAS, FILTRO, true).versao()).isEqualTo(base);
    }

    // ---------- os arquivos de verdade ----------

    @Test
    void osArquivosDoClasspathCarregam() {
        var p = PromptsAgente.carregar(false);
        assertThat(p.pronto()).as(p.motivoDesligado()).isTrue();
        assertThat(p.textoSistema()).contains("Assistente do FilaVIVA").contains("O que você nunca faz");
        assertThat(p.promptFiltro()).contains("INFRA_OU_CODIGO").contains("TENTATIVA_DE_BURLAR")
                .contains("PACIENTE_ESPECIFICO");
    }

    @Test
    void oSystemPromptProibeOsAssuntosEOsComportamentosDeRisco() throws Exception {
        String texto = Files.readString(Path.of("src/main/resources/agente/system-prompt.md"));
        assertThat(texto).contains("infraestrutura").contains("código-fonte").contains("diagnóstico")
                .contains("SAMU (192)").contains("CVV (188)").contains("Não revele").contains("não altera dados");
    }

    @Test
    void asRegrasDeNegocioEstaoPreenchidasEOAgenteFicaPronto() throws Exception {
        String regras = Files.readString(Path.of("src/main/resources/agente/regras-negocio.md"));
        String sistema = Files.readString(Path.of("src/main/resources/agente/system-prompt.md"));
        String filtro = Files.readString(Path.of("src/main/resources/agente/filtro-escopo.md"));
        var p = PromptsAgente.montar(sistema, regras, filtro, true);
        assertThat(p.pronto()).as(p.motivoDesligado()).isTrue();
        assertThat(p.textoSistema()).contains("## Regras de negócio do FilaVIVA").doesNotContain("<!--");
    }

    @Test
    void asRegrasDeNegocioCobremOsPontosFixosDoDesafio() throws Exception {
        String regras = Files.readString(Path.of("src/main/resources/agente/regras-negocio.md"));
        assertThat(regras).contains("Regra de ouro").contains("CID que começa com C").contains("classe A2")
                .contains("o profissional de saúde decide").contains("sintéticos")
                .contains("agenda de sala").contains("preparo pré-operatório").contains("cancelamentos")
                .contains("Dado faltando não rebaixa").contains("Ninguém sai da fila sem decisão humana")
                .contains("furar a fila").contains("CPF").contains("SWALIS").contains("8,5%");
    }
}
