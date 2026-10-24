package com.hackatona.timevermelho.motor;

import java.util.Objects;

/** Decide quem precisa ser olhado de novo pelo regulador, para ninguém ficar esquecido na fila. */
public class AnalisadorReavaliacao {

    public void analisar(ResultadoPaciente r) {
        Caso c = r.getCaso();
        ClasseSwalis classe = r.classe();
        String manual = c.prioridadeManual() == null ? "sem prioridade" : c.prioridadeManual();

        // Divergência histórica com a prioridade manual do cadastro (atribuída na inclusão, ruidosa e raramente
        // revisada, segundo o dicionário de dados). Não é rebaixamento: só pede revisão. Não há equivalência oficial
        // entre alta/média/baixa e A1..D; comparamos só os extremos que a tabela SWALIS do desafio sustenta:
        // urgente (A1/A2) sem prioridade alta, e prioridade alta em classe sem dor ou disfunção importantes (C/D).
        // B ("dor, disfunção ou incapacidade importantes") é compatível com prioridade alta e não é sinalizado.
        boolean urgente = classe == ClasseSwalis.A1 || classe == ClasseSwalis.A2;
        boolean semDisfuncaoImportante = classe == ClasseSwalis.C || classe == ClasseSwalis.D;
        if (urgente && !"alta".equals(c.prioridadeManual())) {
            add(r, TipoReavaliacao.PRIORIDADE_DIVERGENTE,
                    "Prioridade manual \"" + manual + "\", mas a classe clínica é " + classe + " (urgente)");
        } else if (semDisfuncaoImportante && "alta".equals(c.prioridadeManual())) {
            add(r, TipoReavaliacao.PRIORIDADE_DIVERGENTE, "Prioridade manual \"alta\", mas a classe clínica é " + classe
                    + " (" + (classe == ClasseSwalis.D ? "sem dor ou disfunção relevantes" : "dor ou disfunção moderadas")
                    + "). Divergência histórica, não é rebaixamento: confirme a classe ou suba com justificativa");
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
                case HBA1C_ALTA -> add(r, TipoReavaliacao.DESCOMPENSACAO_CLINICA, s.descricao()
                        + (c.diabetesNoCadastro() ? ": diabetes descontrolado para cirurgia eletiva"
                                : ": HbA1c alta sem diabetes registrado no cadastro, investigar antes da cirurgia eletiva"));
                case ALARME_A_CONFIRMAR -> add(r, TipoReavaliacao.REVISAO_URGENTE,
                        s.descricao() + " — relatado em \"" + s.evidencia() + "\" (" + s.fonte() + "). Não muda a "
                                + "classe sozinho: confirme com avaliação clínica e registre o atendimento");
                default -> { }
            }
        }

        if (r.getStatus() == StatusFila.SAIDA_SUGERIDA) {
            add(r, TipoReavaliacao.CONFIRMAR_SAIDA, r.getSinalSaida().descricao()
                    + (r.oncologico() ? " — paciente oncológico: confirmar com cuidado" : "")
                    + ". Continua na posição " + r.getPosicao() + " até o regulador decidir");
        }

        if (r.isMovidoPelaRegraDeOuro()) {
            add(r, TipoReavaliacao.REGRA_DE_OURO, r.getReferencia() == Referencia.OFICIAL
                    ? "Classe " + r.classe() + ", mas sem urgência pela definição oficial (dor e limitação baixas, "
                            + "sem evento de piora no dataset): ficou abaixo de todos os oncológicos"
                    : "Classe " + r.classe() + " por decisão do regulador, mas sem evidência de agravamento registrada "
                            + "(classe pelas regras clínicas: " + r.getClassificacao().classeCalculada() + "): ficou abaixo de "
                            + "todos os oncológicos. Registre o atendimento ou exame que justifica a urgência para ultrapassá-los");
        }
        ClasseSwalis calculada = r.getClassificacao().classeCalculada();
        if (r.isClassePorTexto() && (calculada == ClasseSwalis.A1 || calculada == ClasseSwalis.A2)) {
            Sinal texto = r.getClassificacao().determinantes().stream().filter(MotorPriorizacao::vemDeTextoLivre)
                    .findFirst().orElse(null);
            add(r, TipoReavaliacao.REVISAO_URGENTE, "Classe " + calculada + " provisória: a urgência está só no texto"
                    + (texto == null ? "" : " — " + texto.descricao() + ": \"" + texto.evidencia() + "\" (" + texto.fonte() + ")")
                    + ". Pelos dados estruturados seria " + r.getClasseEstruturada() + ". A posição foi mantida até a "
                    + "revisão clínica; isto não é confirmação médica de " + calculada);
        }
        if (r.isDivergenteDoVerificador()) {
            add(r, TipoReavaliacao.DIVERGENCIA_VERIFICADOR, "Classe " + r.getClassificacao().classeCalculada()
                    + " pela leitura do FilaVIVA (" + r.getClassificacao().regra() + "), mas a definição do verificador "
                    + "oficial não reconhece essa urgência. Na fila operacional ele segue a ordem clínica; na fila de "
                    + "referência da entrega fica abaixo dos oncológicos. Confirmar com o regulador");
        }
    }

    private static void add(ResultadoPaciente r, TipoReavaliacao tipo, String msg) {
        r.getReavaliacoes().add(new Reavaliacao(tipo, msg));
    }
}
