package com.hackatona.timevermelho.dominio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitacaoRepository extends JpaRepository<Solicitacao, String> {

    List<Solicitacao> findBySituacao(Solicitacao.SituacaoCadastro situacao);

    List<Solicitacao> findByDuplicadaDe(String principal);
}
