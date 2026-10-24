package com.hackatona.timevermelho.api;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint simples para o front confirmar que a API está no ar. */
@RestController
@RequestMapping("/api")
public class StatusController {

    @Value("${app.dataset.dir}")
    private String datasetDir;

    @Value("${app.data-referencia}")
    private LocalDate dataReferencia;

    public record Status(String app, LocalDate dataReferencia, boolean datasetEncontrado) {}

    @GetMapping("/status")
    public Status status() {
        boolean encontrado = Files.exists(Path.of(datasetDir, "fila_cirurgica.csv"));
        return new Status("filaviva", dataReferencia, encontrado);
    }
}
