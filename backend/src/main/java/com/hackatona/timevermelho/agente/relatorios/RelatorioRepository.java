package com.hackatona.timevermelho.agente.relatorios;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RelatorioRepository extends JpaRepository<RelatorioIA, String> {

    List<RelatorioIA> findTop50ByOrderByCriadoEmDesc();
}
