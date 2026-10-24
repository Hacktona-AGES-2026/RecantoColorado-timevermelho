package com.hackatona.timevermelho.agente.categorizacao;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Sugestão de categoria feita pela IA, guardada em tabela PRÓPRIA. Nunca toca em paciente, classe, posição, eventos
 * ou decisões da aplicação. A sugestão original da IA nunca é sobrescrita: a revisão humana vai em campos à parte.
 */
@Entity
@Table(name = "agente_categorizacao")
@Getter
@Setter
@NoArgsConstructor
public class CategorizacaoSugerida {

    public enum Origem { IA, REGRA_FIXA }

    public enum StatusRevisao { PENDENTE, ACEITA, CORRIGIDA }

    @Id
    private String id;
    @Column(length = 2000)
    private String texto;
    @Enumerated(EnumType.STRING)
    private CategoriaDado categoriaSugerida;
    @Column(length = 200)
    private String subtemas;
    private double confianca;
    @Column(length = 300)
    private String justificativa;
    @Enumerated(EnumType.STRING)
    private Origem origem;
    /** Marcado pelo código (não pela IA) quando o texto tem sinal de emergência: precisa de olhar humano. */
    private boolean atencaoHumana;
    private String versaoPrompt;
    private String solicitante;
    private LocalDateTime criadoEm;

    @Enumerated(EnumType.STRING)
    private StatusRevisao statusRevisao = StatusRevisao.PENDENTE;
    @Enumerated(EnumType.STRING)
    private CategoriaDado categoriaFinal;
    @Column(length = 500)
    private String justificativaRevisao;
    private String revisadoPor;
    private LocalDateTime revisadoEm;
}
