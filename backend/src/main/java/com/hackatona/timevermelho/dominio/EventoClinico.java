package com.hackatona.timevermelho.dominio;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Uma linha de eventos_clinicos.csv, ou um evento registrado pela ferramenta. */
@Entity
@Table(indexes = @Index(columnList = "solicitacaoId"))
@Getter
@Setter
@NoArgsConstructor
public class EventoClinico {
    @Id
    private String id;
    /** Solicitação que vale para a fila (a principal, se o evento era de uma duplicata). */
    private String solicitacaoId;
    /** Solicitação que estava no CSV. */
    private String solicitacaoOriginal;
    private String pacienteId;
    private LocalDate data;
    private String tipo;
    private String resultado;
    private Double valor;
    @Column(length = 2000)
    private String descricao;
    private String origem;
    /** Registrado pela ferramenta (demo ou regulador), não veio do dataset. */
    private boolean sintetico;
    /** Aponta para uma solicitação que não está na fila. Guardado, mas ignorado. */
    private boolean orfao;
    /** Mesmo conteúdo de outro evento com id diferente. Guardado, mas ignorado. */
    private boolean duplicado;
    private LocalDateTime criadoEm;
}
