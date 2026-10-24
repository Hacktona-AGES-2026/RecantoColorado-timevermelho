package com.hackatona.timevermelho.config;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hackatona.timevermelho.medico.CatalogoEspecialidades;
import com.hackatona.timevermelho.motor.MotorPriorizacao;
import com.hackatona.timevermelho.motor.texto.RegrasClinicas;

/** Liga o motor (classes puras) ao Spring. */
@Configuration
public class MotorConfig {

    @Bean
    RegrasClinicas regrasClinicas() {
        return RegrasClinicas.carregarPadrao();
    }

    @Bean
    CatalogoEspecialidades catalogoEspecialidades() {
        return CatalogoEspecialidades.carregarPadrao();
    }

    @Bean
    MotorPriorizacao motorPriorizacao(RegrasClinicas regras, @Value("${app.data-referencia}") LocalDate referencia) {
        return new MotorPriorizacao(regras, referencia);
    }
}
