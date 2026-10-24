package com.hackatona.timevermelho.medico;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicoRepository extends JpaRepository<Medico, Long> {

    List<Medico> findAllByOrderByNomeAsc();

    boolean existsByCrmIgnoreCase(String crm);
}
