package com.hackatona.timevermelho.medico;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Médico cadastrado, com a especialidade que define a lista de pacientes dele. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Medico {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nome;
    @Column(unique = true)
    private String crm;
    /** Código em especialidades.json (ex.: CARDIOLOGIA). */
    private String especialidade;
    private LocalDateTime criadoEm;
}
