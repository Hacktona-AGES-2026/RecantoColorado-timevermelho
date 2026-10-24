package com.hackatona.timevermelho.dominio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface EventoClinicoRepository extends JpaRepository<EventoClinico, String> {

    /** Eventos que contam para a fila (sem órfãos e sem duplicados). */
    List<EventoClinico> findByOrfaoFalseAndDuplicadoFalse();

    @Transactional
    long deleteBySinteticoTrue();
}
