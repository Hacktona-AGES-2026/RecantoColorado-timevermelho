package com.hackatona.timevermelho.agente.contexto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

/** O agente lê o CSV original da fila e NUNCA o altera. */
class CsvOriginalTest {

    static final String PASTA = "../data";

    private static String hash(Path p) throws IOException, NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
    }

    @Test
    void leTodasAsLinhasEDeixaOArquivoIntacto() throws Exception {
        var csv = new CsvOriginal(PASTA);
        Path arquivo = csv.caminho();
        String antes = hash(arquivo);
        FileTime modificadoAntes = Files.getLastModifiedTime(arquivo);
        long tamanhoAntes = Files.size(arquivo);

        assertThat(csv.registros()).hasSize(941);
        csv.principal("SOL-2024-00044");
        csv.doPaciente("PAC-00087");

        assertThat(hash(arquivo)).isEqualTo(antes);
        assertThat(Files.getLastModifiedTime(arquivo)).isEqualTo(modificadoAntes);
        assertThat(Files.size(arquivo)).isEqualTo(tamanhoAntes);
    }

    @Test
    void oQueDevolveNaoPodeSerAlteradoPorQuemUsa() {
        var csv = new CsvOriginal(PASTA);

        assertThatThrownBy(() -> csv.registros().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> csv.doPaciente("PAC-00087").clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void duplicadasApontamParaAMaisAntigaComoNaImportacao() {
        var csv = new CsvOriginal(PASTA);

        // PAC-00087, mesmo procedimento: SOL-2024-00003 (jan/2024) fica, SOL-2024-00044 (mai/2024) é duplicada
        assertThat(csv.principal("SOL-2024-00044")).isEqualTo("SOL-2024-00003");
        assertThat(csv.principal("SOL-2024-00003")).isEqualTo("SOL-2024-00003");
        assertThat(csv.duplicadasDe("SOL-2024-00003")).isEqualTo(1);
        assertThat(csv.principal("SOL-2099-99999")).isNull();
        // as 22 duplicadas que a importação mescla
        assertThat(csv.registros().stream().filter(r -> !r.solicitacaoId().equals(csv.principal(r.solicitacaoId()))))
                .hasSize(22);
    }

    @Test
    void pacienteComProcedimentosDiferentesTemTodasAsSolicitacoes() {
        var csv = new CsvOriginal(PASTA);

        assertThat(csv.doPaciente("PAC-00492")).extracting(CsvOriginal.Registro::solicitacaoId)
                .containsExactlyInAnyOrder("SOL-2025-00326", "SOL-2026-00448");
        assertThat(csv.doPaciente("PAC-99999")).isEmpty();
    }

    @Test
    void semOArquivoOAgenteSegueSemDadosEmVezDeQuebrar() {
        var csv = new CsvOriginal("pasta-que-nao-existe");

        assertThat(csv.registros()).isEmpty();
        assertThat(csv.principal("SOL-2024-00044")).isNull();
    }
}
