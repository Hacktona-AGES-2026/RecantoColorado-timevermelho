package com.hackatona.timevermelho.api;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.servico.AlertaService;
import com.hackatona.timevermelho.servico.ConsultaService;
import com.hackatona.timevermelho.servico.DecisaoService;
import com.hackatona.timevermelho.servico.DemoService;
import com.hackatona.timevermelho.servico.ExportService;
import com.hackatona.timevermelho.servico.IndicadoresService;
import com.hackatona.timevermelho.servico.SimulacaoService;

import jakarta.validation.Valid;

/** Todas as rotas da API do FilaVIVA. Contrato em frontend/src/lib/api.ts. */
@RestController
@RequestMapping("/api")
@Validated
public class FilaController {

    private final ConsultaService consulta;
    private final DecisaoService decisao;
    private final SimulacaoService simulacao;
    private final AlertaService alertas;
    private final IndicadoresService indicadores;
    private final ExportService export;
    private final DemoService demo;

    public FilaController(ConsultaService consulta, DecisaoService decisao, SimulacaoService simulacao,
                          AlertaService alertas, IndicadoresService indicadores, ExportService export, DemoService demo) {
        this.consulta = consulta;
        this.decisao = decisao;
        this.simulacao = simulacao;
        this.alertas = alertas;
        this.indicadores = indicadores;
        this.export = export;
        this.demo = demo;
    }

    @GetMapping("/fila")
    public Dtos.Pagina<Dtos.ItemFila> fila(@RequestParam(required = false) String classe,
                                           @RequestParam(required = false) String especialidade,
                                           @RequestParam(required = false) String busca,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) Boolean oncologico,
                                           @RequestParam(defaultValue = "0") int pagina,
                                           @RequestParam(defaultValue = "50") int tamanho) {
        return consulta.fila(classe, especialidade, busca, status, oncologico, pagina, tamanho);
    }

    @GetMapping(value = "/fila/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportar() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fila_priorizada.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(export.csv().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/pacientes/{solicitacaoId}")
    public Dtos.PacienteDetalhe paciente(@PathVariable String solicitacaoId) {
        return consulta.paciente(solicitacaoId);
    }

    @PostMapping("/eventos")
    public Dtos.Impacto registrarEvento(@Valid @RequestBody Dtos.NovoEvento evento) {
        return decisao.registrarEvento(evento);
    }

    @PostMapping("/ajustes")
    public Dtos.Impacto ajustar(@Valid @RequestBody Dtos.AjusteRequest ajuste) {
        return decisao.ajustar(ajuste);
    }

    @PostMapping("/simulacao")
    public Dtos.Simulacao simular(@Valid @RequestBody Dtos.SimulacaoRequest req) {
        return simulacao.simular(req);
    }

    @PostMapping("/simulacao/pesos")
    public Dtos.SimulacaoPesos simularPesos(@Valid @RequestBody Dtos.PesosDto pesos) {
        return simulacao.simularPesos(pesos);
    }

    @GetMapping("/alertas")
    public Dtos.Alertas alertas() {
        return alertas.alertas();
    }

    @GetMapping("/indicadores")
    public Dtos.Indicadores indicadores() {
        return indicadores.indicadores();
    }

    @PostMapping("/demo/cenario")
    public java.util.List<Dtos.Impacto> cenario() {
        return demo.cenario();
    }

    @PostMapping("/demo/reset")
    public java.util.Map<String, Boolean> reset() {
        decisao.resetar();
        return java.util.Map.of("ok", true);
    }
}
