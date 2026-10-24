package com.hackatona.timevermelho.agente.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackatona.timevermelho.agente.api.AgenteDtos.CategorizacaoDto;
import com.hackatona.timevermelho.agente.api.AgenteDtos.CategorizarRequest;
import com.hackatona.timevermelho.agente.api.AgenteDtos.RevisaoRequest;
import com.hackatona.timevermelho.agente.categorizacao.CategorizacaoService;
import com.hackatona.timevermelho.agente.categorizacao.CategorizacaoSugerida.StatusRevisao;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** Categorização de textos por assunto, com revisão humana. A IA só sugere; nada aqui altera a fila. */
@RestController
@RequestMapping("/api/agente")
public class CategorizacaoController {

    private final CategorizacaoService servico;

    public CategorizacaoController(CategorizacaoService servico) {
        this.servico = servico;
    }

    @PostMapping("/categorizar")
    public CategorizacaoDto categorizar(@Valid @RequestBody CategorizarRequest req, HttpServletRequest http) {
        return CategorizacaoDto.de(servico.categorizar(req.texto(), req.solicitante(), http.getRemoteAddr()));
    }

    @GetMapping("/categorizacoes")
    public List<CategorizacaoDto> listar(@RequestParam(required = false) StatusRevisao status) {
        return servico.listar(status).stream().map(CategorizacaoDto::de).toList();
    }

    @GetMapping("/categorizacoes/{id}")
    public CategorizacaoDto buscar(@PathVariable String id) {
        return CategorizacaoDto.de(servico.buscar(id));
    }

    @PostMapping("/categorizacoes/{id}/revisao")
    public CategorizacaoDto revisar(@PathVariable String id, @Valid @RequestBody RevisaoRequest req) {
        return CategorizacaoDto.de(servico.revisar(id, req.acao(), req.categoriaCorrigida(), req.justificativa(), req.autor()));
    }
}
