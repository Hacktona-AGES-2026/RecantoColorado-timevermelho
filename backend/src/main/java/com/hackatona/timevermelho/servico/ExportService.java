package com.hackatona.timevermelho.servico;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.motor.StatusFila;

/**
 * Gera o entregável comum: fila_priorizada.csv (solicitacao_id, posicao, justificativa).
 * Primeiro a fila ativa; depois as saídas sugeridas, que só saem de vez quando o regulador confirma.
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
        var snap = fila.atual();
        try (CSVPrinter p = new CSVPrinter(out, formato)) {
            for (ResultadoPaciente r : snap.ativos()) {
                p.printRecord(r.getCaso().solicitacaoId(), r.getPosicao(), r.getJustificativa());
            }
            // Saída sugerida não é exclusão: fica no fim do arquivo até o regulador confirmar ou recusar.
            int posicao = snap.ativos().size();
            for (ResultadoPaciente r : snap.todos()) {
                if (r.getStatus() != StatusFila.SAIDA_SUGERIDA) continue;
                p.printRecord(r.getCaso().solicitacaoId(), ++posicao, "SAÍDA SUGERIDA, aguardando confirmação do regulador: "
                        + r.getSinalSaida().descricao() + " · classe se continuar na fila: " + r.getJustificativa());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString();
    }
}
