package com.hackatona.timevermelho.motor;

/** Classes de urgência do SWALIS adaptado (referencias/swalis.csv). A ordem do enum é a ordem de urgência. */
public enum ClasseSwalis {
    A1(15, "Progressão rápida com risco de vida ou de perda de órgão/função"),
    A2(21, "Progressão rápida com risco de agravamento ou incapacidade"),
    B(60, "Dor, disfunção ou incapacidade importantes, sem progressão rápida"),
    C(180, "Dor ou disfunção moderadas, sem tendência a agravamento"),
    D(360, "Sem dor, disfunção ou incapacidade relevantes");

    private final int prazoDias;
    private final String descricao;

    ClasseSwalis(int prazoDias, String descricao) {
        this.prazoDias = prazoDias;
        this.descricao = descricao;
    }

    public int prazoDias() { return prazoDias; }

    public String descricao() { return descricao; }

    public boolean maisUrgenteQue(ClasseSwalis outra) { return ordinal() < outra.ordinal(); }
}
