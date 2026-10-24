package com.hackatona.timevermelho.motor;

/** Motivos para o regulador olhar um paciente de novo. */
public enum TipoReavaliacao {
    REVISAO_URGENTE("REVISÃO CLÍNICA URGENTE: possível urgência descrita só em texto"),
    PRIORIDADE_DIVERGENTE("Prioridade manual diverge da classe clínica"),
    ESQUECIDO("Mais que o dobro do prazo e prioridade nunca revisada"),
    FLAG_DIVERGENTE("Cadastro com flag errada"),
    DADO_INCOMPLETO("Faltam dados clínicos para classificar com segurança"),
    SEM_CONTATO("Sem contato com o paciente"),
    CONFIRMAR_SAIDA("Confirmar saída da fila"),
    DESCOMPENSACAO_CLINICA("Descompensação clínica: encaminhar para controle antes da cirurgia"),
    REGRA_DE_OURO("Posição limitada pela regra de ouro"),
    DIVERGENCIA_VERIFICADOR("Urgência que o verificador oficial não reconhece");

    private final String titulo;

    TipoReavaliacao(String titulo) { this.titulo = titulo; }

    public String titulo() { return titulo; }
}
