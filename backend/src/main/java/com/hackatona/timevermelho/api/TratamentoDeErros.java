package com.hackatona.timevermelho.api;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.servico.Erros;

/** Todo erro volta como {"mensagem": "..."} em português, pronto para a tela mostrar. */
@RestControllerAdvice
public class TratamentoDeErros {

    @ExceptionHandler(Erros.RegraNegocio.class)
    ResponseEntity<Dtos.Erro> regra(Erros.RegraNegocio e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new Dtos.Erro(e.getMessage()));
    }

    @ExceptionHandler(Erros.NaoEncontrado.class)
    ResponseEntity<Dtos.Erro> naoEncontrado(Erros.NaoEncontrado e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Dtos.Erro(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Dtos.Erro> invalido(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new Dtos.Erro(msg));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Dtos.Erro> ilegivel(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new Dtos.Erro("Corpo da requisição inválido"));
    }
}
