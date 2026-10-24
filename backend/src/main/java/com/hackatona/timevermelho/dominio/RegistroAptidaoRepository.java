package com.hackatona.timevermelho.dominio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroAptidaoRepository extends JpaRepository<RegistroAptidao, Long> {

    List<RegistroAptidao> findBySolicitacaoIdOrderByCriadoEmDescIdDesc(String solicitacaoId);

    List<RegistroAptidao> findAllByOrderByCriadoEmAscIdAsc();
}
