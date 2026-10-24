package com.hackatona.timevermelho.motor;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Tudo o que o motor concluiu sobre um paciente: classe, score, posição e o porquê. */
@Getter
@Setter
public class ResultadoPaciente {

    private final Caso caso;
    private final List<Sinal> sinais;
    private Classificacao classificacao;
    private double score;
    private List<ComponenteScore> componentes;
    private StatusFila status = StatusFila.ATIVA;
    /** Sinal que sugere a saída da fila (desistência, cirurgia em outro serviço). */
    private Sinal sinalSaida;
    private Integer posicao;
    private Integer posicaoManual;
    /** "Sem urgência clínica" pela definição conservadora do verificador oficial. */
    private boolean semUrgencia;
    private boolean movidoPelaRegraDeOuro;
    private String justificativa;
    private final List<Reavaliacao> reavaliacoes = new ArrayList<>();

    public ResultadoPaciente(Caso caso, List<Sinal> sinais) {
        this.caso = caso;
        this.sinais = sinais;
    }

    public ClasseSwalis classe() {
        return classificacao.classe();
    }

    /** Oncológico pelo CID ou por biópsia positiva. */
    public boolean oncologico() {
        return caso.oncologicoPeloCid() || temSinal(TipoSinal.BIOPSIA_POSITIVA);
    }

    public double percentualPrazo() {
        return Math.round(1000.0 * caso.diasEmFila() / classe().prazoDias()) / 10.0;
    }

    public boolean temSinal(TipoSinal tipo) {
        return sinais.stream().anyMatch(s -> s.tipo() == tipo);
    }
}
