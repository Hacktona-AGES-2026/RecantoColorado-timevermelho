package com.hackatona.timevermelho.dominio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ComunicacaoBranRepository extends JpaRepository<ComunicacaoBran, Long> {

    List<ComunicacaoBran> findBySolicitacaoIdOrderByCriadoEmDescIdDesc(String solicitacaoId);

    List<ComunicacaoBran> findAllByOrderByCriadoEmAscIdAsc();
}
