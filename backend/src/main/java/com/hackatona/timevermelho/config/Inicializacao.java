package com.hackatona.timevermelho.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.hackatona.timevermelho.importacao.ImportadorDataset;
import com.hackatona.timevermelho.servico.ExportService;
import com.hackatona.timevermelho.servico.FilaService;

/** Ao subir: importa o dataset, calcula a fila e grava o fila_priorizada.csv. */
@Component
public class Inicializacao implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Inicializacao.class);

    private final ImportadorDataset importador;
    private final FilaService fila;
    private final ExportService export;
    private final String arquivoExport;

    public Inicializacao(ImportadorDataset importador, FilaService fila, ExportService export,
                         @Value("${app.export.arquivo:}") String arquivoExport) {
        this.importador = importador;
        this.fila = fila;
        this.export = export;
        this.arquivoExport = arquivoExport;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        importador.importar();
        fila.recalcular();
        if (!arquivoExport.isBlank()) {
            Path destino = Path.of(arquivoExport);
            // o entregável é sempre a fila de referência (só o dataset), nunca a operacional
            var oficial = fila.oficial();
            Files.writeString(destino, export.csv(oficial));
            log.info("fila_priorizada.csv (fila de referência {}) gravado em {}", oficial.info().versao(),
                    destino.toAbsolutePath().normalize());
        }
    }
}
