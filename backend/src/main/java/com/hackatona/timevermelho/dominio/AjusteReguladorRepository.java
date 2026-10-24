package com.hackatona.timevermelho.dominio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AjusteReguladorRepository extends JpaRepository<AjusteRegulador, Long> {

    List<AjusteRegulador> findAllByOrderByCriadoEmAscIdAsc();

    List<AjusteRegulador> findBySolicitacaoIdOrderByCriadoEmDescIdDesc(String solicitacaoId);
}
