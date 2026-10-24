package com.hackatona.timevermelho.motor;

import java.util.Objects;

/** Decide quem precisa ser olhado de novo pelo regulador, para ninguém ficar esquecido na fila. */
public class AnalisadorReavaliacao {

    public void analisar(ResultadoPaciente r) {
        Caso c = r.getCaso();
        ClasseSwalis classe = r.classe();
        String manual = c.prioridadeManual() == null ? "sem prioridade" : c.prioridadeManual();

        boolean urgente = classe == ClasseSwalis.A1 || classe == ClasseSwalis.A2;
        if (urgente && !"alta".equals(c.prioridadeManual())) {
            add(r, TipoReavaliacao.PRIORIDADE_DIVERGENTE,
                    "Prioridade manual \"" + manual + "\", mas a classe clínica é " + classe);
        } else if (classe == ClasseSwalis.D && "alta".equals(c.prioridadeManual())) {
            add(r, TipoReavaliacao.PRIORIDADE_DIVERGENTE,
                    "Prioridade manual \"alta\" sem urgência clínica (classe D): confirmar o motivo");
        }

        boolean nuncaRevisada = c.dataUltimaRevisao() == null || Objects.equals(c.dataUltimaRevisao(), c.dataEntrada());
        if (nuncaRevisada && c.diasEmFila() > 2 * classe.prazoDias()) {
            add(r, TipoReavaliacao.ESQUECIDO, c.diasEmFila() + " dias de fila, mais que o dobro do prazo de "
                    + classe.prazoDias() + " dias, e prioridade nunca revisada");
        }

        for (Sinal s : r.getSinais()) {
            switch (s.tipo()) {
                case FLAG_ONCO_DIVERGENTE, FLAG_PIORA_DESATUALIZADA -> add(r, TipoReavaliacao.FLAG_DIVERGENTE, s.descricao());
                case DADO_INCOMPLETO -> add(r, TipoReavaliacao.DADO_INCOMPLETO, s.descricao());
                case SEM_CONTATO -> add(r, TipoReavaliacao.SEM_CONTATO, s.descricao());
                case HBA1C_ALTA -> add(r, TipoReavaliacao.DESCOMPENSACAO_CLINICA,
                        s.descricao() + ": diabetes descontrolado para cirurgia eletiva");
                default -> { }
            }
        }

        if (r.getStatus() == StatusFila.SAIDA_SUGERIDA) {
            add(r, TipoReavaliacao.CONFIRMAR_SAIDA, r.getSinalSaida().descricao()
                    + (r.oncologico() ? " — paciente oncológico: confirmar com cuidado" : "")
                    + ". Continua na posição " + r.getPosicao() + " até o regulador decidir");
        }

        if (r.isMovidoPelaRegraDeOuro()) {
            add(r, TipoReavaliacao.REGRA_DE_OURO,
                    "Classe " + r.classe() + " pelo FilaVIVA, mas sem urgência pela definição oficial (dor e limitação baixas, "
                            + "sem evento de piora): ficou abaixo de todos os oncológicos. Revisar se o texto do médico indica urgência");
        }
    }

    private static void add(ResultadoPaciente r, TipoReavaliacao tipo, String msg) {
        r.getReavaliacoes().add(new Reavaliacao(tipo, msg));
    }
}
