package com.hackatona.timevermelho.motor;

/**
 * Última palavra do regulador sobre um paciente.
 *
 * @param classe classe definida pelo regulador (null = aceita a calculada)
 * @param saidaConfirmada true = confirmou a saída da fila; false = recusou a sugestão; null = não decidiu
 */
public record DecisaoRegulador(ClasseSwalis classe, String justificativaClasse,
                               Boolean saidaConfirmada, String justificativaSaida) {

    public static final DecisaoRegulador NENHUMA = new DecisaoRegulador(null, null, null, null);
}
