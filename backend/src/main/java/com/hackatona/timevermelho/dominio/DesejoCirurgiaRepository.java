package com.hackatona.timevermelho.dominio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DesejoCirurgiaRepository extends JpaRepository<DesejoCirurgia, Long> {

    List<DesejoCirurgia> findBySolicitacaoIdOrderByCriadoEmDescIdDesc(String solicitacaoId);

    List<DesejoCirurgia> findAllByOrderByCriadoEmAscIdAsc();
}
