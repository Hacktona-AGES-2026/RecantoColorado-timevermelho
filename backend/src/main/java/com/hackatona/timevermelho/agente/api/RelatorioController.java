package com.hackatona.timevermelho.agente.api;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hackatona.timevermelho.agente.api.AgenteDtos.Especialidades;
import com.hackatona.timevermelho.agente.api.AgenteDtos.RelatorioDto;
import com.hackatona.timevermelho.agente.api.AgenteDtos.RelatorioRequest;
import com.hackatona.timevermelho.agente.api.AgenteDtos.RelatorioResumoDto;
import com.hackatona.timevermelho.agente.relatorios.RelatorioIA;
import com.hackatona.timevermelho.agente.relatorios.RelatorioService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** Relatórios para médicos gerados por IA a partir de dados agregados. Tudo marcado como "revisar antes de usar". */
@RestController
@RequestMapping("/api/agente/relatorios")
public class RelatorioController {

    private final RelatorioService servico;

    public RelatorioController(RelatorioService servico) {
        this.servico = servico;
    }

    @PostMapping
    public RelatorioDto gerar(@Valid @RequestBody RelatorioRequest req, HttpServletRequest http) {
        return RelatorioDto.de(servico.gerar(req.tipo(), req.especialidade(), req.solicitante(), http.getRemoteAddr()));
    }

    @GetMapping
    public List<RelatorioResumoDto> listar() {
        return servico.listar().stream().map(RelatorioResumoDto::de).toList();
    }

    /** Especialidades que existem na fila, para o formulário de pedido. */
    @GetMapping("/especialidades")
    public Especialidades especialidades() {
        return new Especialidades(servico.especialidades());
    }

    @GetMapping("/{id}")
    public RelatorioDto buscar(@PathVariable String id) {
        return RelatorioDto.de(servico.buscar(id));
    }

    /** O mesmo texto, como arquivo Markdown para baixar. */
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> baixar(@PathVariable String id) {
        RelatorioIA r = servico.buscar(id);
        String nome = "relatorio-" + r.getId().substring(0, 8) + ".md";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                .contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
                .body(r.getTexto().getBytes(StandardCharsets.UTF_8));
    }
}
