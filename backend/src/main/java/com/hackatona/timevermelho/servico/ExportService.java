package com.hackatona.timevermelho.servico;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import com.hackatona.timevermelho.motor.ResultadoPaciente;

/**
 * Gera o CSV no formato do entregável (solicitacao_id, posicao, justificativa) a partir de UM snapshot.
 * - oficial: a fila de referência da entrega, o fila_priorizada.csv da banca.
 * - operacional: exatamente a fila que a API mostra naquele snapshot, com eventos e decisões registrados.
 * Saídas sugeridas continuam na posição calculada, com aviso na justificativa, até o regulador confirmar.
 */
@Service
public class ExportService {

    private static final DateTimeFormatter CARIMBO =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.of("America/Sao_Paulo"));

    private final FilaService fila;

    public ExportService(FilaService fila) {
        this.fila = fila;
    }

    /** O entregável oficial (fila de referência). */
    public String csv() {
        return csv(fila.oficial());
    }

    public String csv(FilaService.Snapshot snapshot) {
        StringWriter out = new StringWriter();
        CSVFormat formato = CSVFormat.DEFAULT.builder()
                .setHeader("solicitacao_id", "posicao", "justificativa").setRecordSeparator("\n").get();
        try (CSVPrinter p = new CSVPrinter(out, formato)) {
            for (ResultadoPaciente r : snapshot.ativos()) {
                p.printRecord(r.getCaso().solicitacaoId(), r.getPosicao(), r.getJustificativa());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString();
    }

    /** Nome do arquivo baixado: o oficial é o fila_priorizada.csv; o operacional leva versão e horário. */
    public static String nomeArquivo(FilaService.Snapshot snapshot) {
        var info = snapshot.info();
        return FilaService.OFICIAL.equals(info.tipo()) ? "fila_priorizada.csv"
                : "fila_operacional_" + info.versao() + "_" + CARIMBO.format(info.calculadoEm()) + ".csv";
    }
}
