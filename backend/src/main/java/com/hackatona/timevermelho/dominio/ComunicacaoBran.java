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
 * Conversa BRAN com o paciente: Benefícios, Riscos, Alternativas e Nada (o que acontece sem a cirurgia).
 * Cada comunicação é uma linha nova (histórico); a mais recente é a que vale. Não muda a fila.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ComunicacaoBran {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String solicitacaoId;
    @Column(length = 4000)
    private String beneficios;
    @Column(length = 4000)
    private String riscos;
    @Column(length = 4000)
    private String alternativas;
    @Column(length = 4000)
    private String nada;
    @Enumerated(EnumType.STRING)
    private FormaComunicacao forma;
    /** Dúvidas que o paciente (ou a família) levantou. */
    @Column(length = 2000)
    private String duvidas;
    /** Quem comunicou confirma que o paciente recebeu as informações e teve as dúvidas respondidas. */
    private boolean compreendeu;
    private String autor;
    private Long medicoId;
    private LocalDateTime criadoEm;
}
