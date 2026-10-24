package com.hackatona.timevermelho.motor;

/**
 * Última palavra do regulador sobre um paciente.
 *
 * @param classe classe definida pelo regulador (null = aceita a calculada)
 * @param saidaConfirmada true = confirmou a saída da fila; false = recusou a sugestão; null = não decidiu
 * @param classeMinima piso da classe vigente (null = sem piso). Usado enquanto um rebaixamento aguarda o regulador,
 *                     quando ele rejeita o rebaixamento e quando o rebaixamento de um oncológico é bloqueado.
 *                     A classe pode subir acima do piso; nunca cai abaixo dele.
 */
public record DecisaoRegulador(ClasseSwalis classe, String justificativaClasse,
                               Boolean saidaConfirmada, String justificativaSaida,
                               ClasseSwalis classeMinima, String justificativaMinima) {

    public static final DecisaoRegulador NENHUMA = new DecisaoRegulador(null, null, null, null);

    public DecisaoRegulador(ClasseSwalis classe, String justificativaClasse, Boolean saidaConfirmada,
                            String justificativaSaida) {
        this(classe, justificativaClasse, saidaConfirmada, justificativaSaida, null, null);
    }

    public DecisaoRegulador comClasse(ClasseSwalis nova, String justificativa) {
        return new DecisaoRegulador(nova, justificativa, saidaConfirmada, justificativaSaida, classeMinima,
                justificativaMinima);
    }

    public DecisaoRegulador comSaida(Boolean confirmada, String justificativa) {
        return new DecisaoRegulador(classe, justificativaClasse, confirmada, justificativa, classeMinima,
                justificativaMinima);
    }

    public DecisaoRegulador comPiso(ClasseSwalis piso, String justificativa) {
        return new DecisaoRegulador(classe, justificativaClasse, saidaConfirmada, justificativaSaida, piso, justificativa);
    }
}
