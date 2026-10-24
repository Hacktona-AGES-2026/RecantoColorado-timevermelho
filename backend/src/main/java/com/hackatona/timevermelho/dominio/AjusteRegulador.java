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

/** Trilha de auditoria: cada decisão do regulador, com justificativa obrigatória. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class AjusteRegulador {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String solicitacaoId;
    @Enumerated(EnumType.STRING)
    private TipoAjuste tipo;
    @Enumerated(EnumType.STRING)
    private ClasseSwalis classeAnterior;
    @Enumerated(EnumType.STRING)
    private ClasseSwalis classeNova;
    @Column(length = 2000)
    private String justificativa;
    private String autor;
    private LocalDateTime criadoEm;
    /** Rebaixamento sugerido pelo recálculo automático (e não por uma pessoa). */
    private boolean automatico;
    /** Ao confirmar o rebaixamento, a classe volta a seguir a calculada (em vez de fixar classeNova). */
    private boolean seguirCalculada;
    /** Evidências da classe proposta, para o regulador decidir (rebaixamentos). */
    @Column(length = 4000)
    private String evidencias;
}
