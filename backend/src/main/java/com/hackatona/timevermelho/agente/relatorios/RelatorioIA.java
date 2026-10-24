package com.hackatona.timevermelho.agente.relatorios;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Relatório gerado pela IA para médicos e reguladores, guardado em tabela PRÓPRIA com quem pediu, quando, qual a
 * versão do prompt e exatamente quais dados agregados foram usados. Não altera nenhum dado oficial. No relatório por
 * procedimento, a tabela de pacientes é escrita pelo código a partir do dataset, não pela IA.
 */
@Entity
@Table(name = "agente_relatorio")
@Getter
@Setter
@NoArgsConstructor
public class RelatorioIA {

    public enum Tipo { VISAO_GERAL, ESPECIALIDADE, PROCEDIMENTO }

    @Id
    private String id;
    @Enumerated(EnumType.STRING)
    private Tipo tipo;
    private String especialidade;
    private String procedimento;
    private String titulo;
    /** Texto da IA + tabela de pacientes (no relatório por procedimento), por isso o tamanho folgado. */
    @Column(length = 200000)
    private String texto;
    /** Os números agregados que foram entregues à IA, tal como foram lidos. */
    @Column(length = 8000)
    private String dadosUsados;
    private String solicitante;
    private String versaoPrompt;
    private LocalDateTime criadoEm;
}
