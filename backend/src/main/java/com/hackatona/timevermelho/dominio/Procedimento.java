package com.hackatona.timevermelho.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Catálogo de procedimentos (procedimentos.csv). */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Procedimento {
    @Id
    private String id;
    private String nome;
    private String especialidade;
    private String porte;
    private Integer riscoCirurgico;
    private Integer duracaoMediaMin;
    private String necessitaUti;
}
