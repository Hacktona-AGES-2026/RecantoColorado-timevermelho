package com.hackatona.timevermelho.dominio;

import java.time.LocalDateTime;

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

/**
 * Se o paciente quer ser operado, com quem registrou e quando. Cada resposta é uma linha nova (histórico completo);
 * a mais recente é a que vale. Não muda classe nem posição: só avisa o regulador.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class DesejoCirurgia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String solicitacaoId;
    @Enumerated(EnumType.STRING)
    private RespostaCirurgia resposta;
    @Column(length = 1000)
    private String observacao;
    private String autor;
    /** Médico cadastrado que registrou (null = regulador). */
    private Long medicoId;
    private LocalDateTime criadoEm;
}
