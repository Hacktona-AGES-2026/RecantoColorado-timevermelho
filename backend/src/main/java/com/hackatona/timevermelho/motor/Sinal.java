package com.hackatona.timevermelho.motor;

import java.time.LocalDate;

/**
 * Um fato clínico encontrado no paciente.
 *
 * @param descricao frase curta para a tela ("Biópsia positiva em 13/09/2026")
 * @param evidencia o trecho que prova o sinal (laudo, texto do médico, valor da coluna)
 * @param fonte de onde veio: nome da coluna ou "evento EVT-000123"
 */
public record Sinal(TipoSinal tipo, String descricao, String evidencia, String fonte, LocalDate data) {

    public static Sinal de(TipoSinal tipo, String descricao, String evidencia, String fonte) {
        return new Sinal(tipo, descricao, evidencia, fonte, null);
    }
}
