package com.hackatona.timevermelho.motor;

import java.util.List;

/**
 * Classe final do paciente.
 *
 * @param classe o que vale para a fila (igual à calculada, a não ser que o regulador tenha ajustado)
 * @param classeCalculada o que as regras disseram
 * @param regra frase da regra que decidiu a classe
 * @param determinantes sinais que dispararam a regra
 */
public record Classificacao(ClasseSwalis classe, ClasseSwalis classeCalculada, String regra,
                            List<Sinal> determinantes, boolean ajustada, String justificativaAjuste) {

    public Classificacao comAjuste(ClasseSwalis nova, String justificativa) {
        return new Classificacao(nova, classeCalculada, regra, determinantes, true, justificativa);
    }
}
