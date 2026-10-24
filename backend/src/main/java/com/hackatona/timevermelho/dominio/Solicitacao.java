package com.hackatona.timevermelho.dominio;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Uma linha de fila_cirurgica.csv, já normalizada. Os valores originais ficam guardados para auditoria. */
@Entity
@Table(indexes = @Index(columnList = "pacienteId"))
@Getter
@Setter
@NoArgsConstructor
public class Solicitacao {

    public enum SituacaoCadastro { ATIVA, DUPLICADA }

    @Id
    private String id;
    private String pacienteId;
    private String nome;
    private String sexo;
    private Integer idade;
    private String municipio;
    private String municipioOriginal;
    private String uf;
    private Integer distanciaKm;
    private String procedimentoId;
    private String procedimento;
    private String especialidade;
    /** CID normalizado: maiúsculo e sem ponto (K409). */
    private String cid;
    private String cidOriginal;
    @Column(length = 500)
    private String cidDescricao;
    private String cidSecundario;
    private Integer asa;
    @Column(length = 1000)
    private String comorbidades;
    private Integer dor;
    private String limitacao;
    @Column(length = 4000)
    private String justificativa;
    @Column(length = 4000)
    private String observacoes;
    private LocalDate dataEntrada;
    private Integer diasEmFilaOriginal;
    /** alta | media | baixa | null (normalizado). */
    private String prioridadeManual;
    private String prioridadeOriginal;
    private LocalDate dataUltimaRevisao;
    private String flagOncologico;
    private String flagDiabetico;
    private String flagPiora;
    private Double hba1cUltima;
    private java.time.LocalDate dataHba1cUltima;
    private String avaliacaoPreAnestesica;
    private String examesPreOpValidos;

    @Enumerated(EnumType.STRING)
    private SituacaoCadastro situacao = SituacaoCadastro.ATIVA;
    /** Se for duplicada, a solicitação principal que ficou na fila. */
    private String duplicadaDe;
}
