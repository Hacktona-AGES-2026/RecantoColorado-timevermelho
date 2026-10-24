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
    /** Sob qual referência a regra de ouro foi aplicada (fila operacional ou fila de referência). */
    private Referencia referencia = Referencia.ATUAL;
    /** "Sem urgência clínica" segundo a referência usada (ver GuardRegraDeOuro). */
    private boolean semUrgencia;
    /** Protegido pela regra de ouro: nunca abaixo de um paciente sem urgência. */
    private boolean protegido;
    /** Urgência reconhecida pelo FilaVIVA que a definição do verificador oficial não reconhece. */
    private boolean divergenteDoVerificador;
    /** Classe que só os dados estruturados (CID, eventos, dor, limitação) dariam, sem o texto livre. */
    private ClasseSwalis classeEstruturada;
    /** A classe calculada é mais urgente que a estruturada só por causa do texto livre: provisória, pendente de revisão. */
    private boolean classePorTexto;
    /** Regulador subiu a classe sem evidência de agravamento registrada: não pode passar à frente de protegidos. */
    private boolean promocaoSemEvidencia;
    /** Sinal que sustenta a urgência numa promoção (rastreabilidade), quando existe. */
    private Sinal evidenciaDaPromocao;
    private boolean movidoPelaRegraDeOuro;
    private String justificativa;
    /** Situação de rebaixamento na fila operacional: PENDENTE, CONFIRMADO, REJEITADO, BLOQUEADO ou null. */
    private String estadoRebaixamento;
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
