package com.hackatona.timevermelho.motor;

/** Uma parcela do score, em pontos (a soma de todas vai de 0 a 100), com a frase que explica o valor. */
public record ComponenteScore(String chave, String nome, double pontos, double maximo, String explicacao) {}
