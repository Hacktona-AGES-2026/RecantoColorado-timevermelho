package com.hackatona.timevermelho.api;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hackatona.timevermelho.api.dto.Conversor;
import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.servico.FilaService;

/** Confirma que a API está no ar e diz quais filas existem agora (operacional e de referência). */
@RestController
@RequestMapping("/api")
public class StatusController {

    @Value("${app.dataset.dir}")
    private String datasetDir;

    @Value("${app.data-referencia}")
    private LocalDate dataReferencia;

    private final FilaService fila;

    public StatusController(FilaService fila) {
        this.fila = fila;
    }

    public record Status(String app, LocalDate dataReferencia, boolean datasetEncontrado,
                         Map<String, Dtos.SnapshotDto> snapshots) {}

    @GetMapping("/status")
    public Status status() {
        boolean encontrado = Files.exists(Path.of(datasetDir, "fila_cirurgica.csv"));
        Map<String, Dtos.SnapshotDto> snapshots = new LinkedHashMap<>();
        if (encontrado) {
            snapshots.put(FilaService.OPERACIONAL, Conversor.snapshot(fila.atual().info()));
            snapshots.put(FilaService.OFICIAL, Conversor.snapshot(fila.oficial().info()));
        }
        return new Status("filaviva", dataReferencia, encontrado, snapshots);
    }
}
