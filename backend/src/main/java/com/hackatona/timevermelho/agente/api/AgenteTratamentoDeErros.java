package com.hackatona.timevermelho.agente.api;

import java.util.stream.Collectors;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.hackatona.timevermelho.agente.chat.Excecoes.AgenteDesligadoException;
import com.hackatona.timevermelho.agente.chat.Excecoes.LimiteExcedidoException;
import com.hackatona.timevermelho.agente.chat.Excecoes.MensagemInvalidaException;
import com.hackatona.timevermelho.agente.protecao.TextosAgente;
import com.hackatona.timevermelho.api.dto.Dtos;

/**
 * Erros do agente antes de começar a transmitir. O motivo técnico do desligamento só aparece em /api/agente/status.
 *
 * <p>Toda resposta de erro declara {@code application/json} de propósito: o navegador pede o chat com
 * {@code Accept: text/event-stream}, e sem o tipo fixo o Spring não acharia como escrever o erro em JSON e
 * devolveria 500 no lugar do status certo. Esta classe tem precedência sobre o tratador geral da aplicação
 * para os controllers do agente, para que os erros de validação do corpo também sigam essa regra.
 */
@RestControllerAdvice(basePackages = "com.hackatona.timevermelho.agente.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AgenteTratamentoDeErros {

    private static ResponseEntity<Dtos.Erro> json(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(new Dtos.Erro(mensagem));
    }

    @ExceptionHandler(AgenteDesligadoException.class)
    ResponseEntity<Dtos.Erro> desligado(AgenteDesligadoException e) {
        return json(HttpStatus.SERVICE_UNAVAILABLE, TextosAgente.INDISPONIVEL);
    }

    @ExceptionHandler(LimiteExcedidoException.class)
    ResponseEntity<Dtos.Erro> limite(LimiteExcedidoException e) {
        return json(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(MensagemInvalidaException.class)
    ResponseEntity<Dtos.Erro> invalida(MensagemInvalidaException e) {
        return json(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Dtos.Erro> corpoInvalido(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).collect(Collectors.joining("; "));
        return json(HttpStatus.BAD_REQUEST, msg.isBlank() ? "Pedido inválido" : msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Dtos.Erro> corpoIlegivel(HttpMessageNotReadableException e) {
        return json(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido");
    }
}
