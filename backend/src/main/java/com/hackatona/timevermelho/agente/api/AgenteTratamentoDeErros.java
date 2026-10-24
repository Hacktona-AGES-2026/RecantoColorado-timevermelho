package com.hackatona.timevermelho.agente.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.hackatona.timevermelho.agente.chat.Excecoes.AgenteDesligadoException;
import com.hackatona.timevermelho.agente.chat.Excecoes.LimiteExcedidoException;
import com.hackatona.timevermelho.agente.chat.Excecoes.MensagemInvalidaException;
import com.hackatona.timevermelho.agente.protecao.TextosAgente;
import com.hackatona.timevermelho.api.dto.Dtos;

/** Erros do chat antes de começar a transmitir. O motivo técnico do desligamento só aparece em /api/agente/status. */
@RestControllerAdvice(assignableTypes = AgenteController.class)
public class AgenteTratamentoDeErros {

    @ExceptionHandler(AgenteDesligadoException.class)
    ResponseEntity<Dtos.Erro> desligado(AgenteDesligadoException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new Dtos.Erro(TextosAgente.INDISPONIVEL));
    }

    @ExceptionHandler(LimiteExcedidoException.class)
    ResponseEntity<Dtos.Erro> limite(LimiteExcedidoException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new Dtos.Erro(e.getMessage()));
    }

    @ExceptionHandler(MensagemInvalidaException.class)
    ResponseEntity<Dtos.Erro> invalida(MensagemInvalidaException e) {
        return ResponseEntity.badRequest().body(new Dtos.Erro(e.getMessage()));
    }
}
