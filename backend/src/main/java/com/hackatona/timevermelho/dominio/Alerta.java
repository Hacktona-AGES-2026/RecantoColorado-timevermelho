package com.hackatona.timevermelho.dominio;

import java.time.LocalDateTime;

import com.hackatona.timevermelho.motor.ClasseSwalis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Algo que mudou na fila e o regulador precisa ver. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Alerta {

    public enum Tipo { EVENTO_NOVO, MUDANCA_CLASSE, AJUSTE_REGULADOR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String solicitacaoId;
    private String nome;
    @Enumerated(EnumType.STRING)
    private Tipo tipo;
    @Column(length = 2000)
    private String mensagem;
    @Enumerated(EnumType.STRING)
    private ClasseSwalis classeAntes;
    @Enumerated(EnumType.STRING)
    private ClasseSwalis classeDepois;
    private Integer posicaoAntes;
    private Integer posicaoDepois;
    private LocalDateTime criadoEm;
}
