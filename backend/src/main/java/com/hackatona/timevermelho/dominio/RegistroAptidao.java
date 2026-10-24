package com.hackatona.timevermelho.dominio;

import java.time.LocalDate;
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
 * Registro da avaliação de aptidão cirúrgica feito por uma pessoa: fatores de saúde que o sistema não tem (informados
 * pelo médico) e/ou a decisão (liberar, aguardar otimização, contraindicar). Cada registro é uma linha nova; o mais
 * recente de cada tipo vale. Não muda a posição na fila.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class RegistroAptidao {

    public enum Tipo { FATORES, DECISAO }

    /** Decisão humana sobre a aptidão. */
    public enum Decisao { LIBERADO, AGUARDAR_OTIMIZACAO, CONTRAINDICADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String solicitacaoId;
    @Enumerated(EnumType.STRING)
    private Tipo tipo;

    // fatores informados pelo médico (o dataset não traz)
    private boolean colesterolAlto;
    private boolean pressaoDescontrolada;
    private Double imc;
    private boolean tabagismoAtual;
    private boolean adesaoRuim;
    @Column(length = 1000)
    private String outros;

    // decisão
    @Enumerated(EnumType.STRING)
    private Decisao decisao;
    @Column(length = 2000)
    private String plano;
    private LocalDate reavaliarEm;

    @Column(length = 2000)
    private String justificativa;
    private String autor;
    private Long medicoId;
    private LocalDateTime criadoEm;
}
