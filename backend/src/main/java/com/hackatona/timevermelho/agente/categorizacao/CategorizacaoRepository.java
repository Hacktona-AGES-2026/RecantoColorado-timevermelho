package com.hackatona.timevermelho.agente.categorizacao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategorizacaoRepository extends JpaRepository<CategorizacaoSugerida, String> {

    List<CategorizacaoSugerida> findTop50ByOrderByCriadoEmDesc();

    List<CategorizacaoSugerida> findTop50ByStatusRevisaoOrderByCriadoEmDesc(CategorizacaoSugerida.StatusRevisao status);
}
