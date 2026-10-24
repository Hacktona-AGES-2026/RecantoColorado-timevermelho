package com.hackatona.timevermelho.servico;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import com.hackatona.timevermelho.motor.ResultadoPaciente;

/**
 * Gera o entregável comum: fila_priorizada.csv (solicitacao_id, posicao, justificativa).
 * Saídas sugeridas continuam na posição calculada, com aviso na justificativa, até o regulador confirmar.
 */
@Service
public class ExportService {

    private final FilaService fila;

    public ExportService(FilaService fila) {
        this.fila = fila;
    }

    public String csv() {
        StringWriter out = new StringWriter();
        CSVFormat formato = CSVFormat.DEFAULT.builder()
                .setHeader("solicitacao_id", "posicao", "justificativa").setRecordSeparator("\n").get();
        try (CSVPrinter p = new CSVPrinter(out, formato)) {
            // todos que estão na fila, inclusive saídas sugeridas ainda não confirmadas
            for (ResultadoPaciente r : fila.atual().ativos()) {
                p.printRecord(r.getCaso().solicitacaoId(), r.getPosicao(), r.getJustificativa());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString();
    }
}
