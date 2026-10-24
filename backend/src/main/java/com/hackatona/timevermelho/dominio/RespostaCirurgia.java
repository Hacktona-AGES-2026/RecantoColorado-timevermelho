package com.hackatona.timevermelho.dominio;

/** O que o paciente respondeu sobre querer ser operado. Sem registro = não informado (o dataset não traz isso). */
public enum RespostaCirurgia {
    QUER_OPERAR("Quer ser operado"),
    NAO_QUER_OPERAR("Não quer ser operado"),
    EM_DUVIDA("Em dúvida");

    private final String rotulo;

    RespostaCirurgia(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
