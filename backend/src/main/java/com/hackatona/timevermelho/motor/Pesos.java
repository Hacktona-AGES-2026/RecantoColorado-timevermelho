package com.hackatona.timevermelho.motor;

/** Pesos máximos de cada componente do score. O regulador pode ajustar na tela do simulador. */
public record Pesos(double diagnostico, double piora, double dor, double limitacao, double espera, double risco) {

    public static final Pesos PADRAO = new Pesos(30, 25, 15, 10, 15, 5);

    public double soma() {
        return diagnostico + piora + dor + limitacao + espera + risco;
    }
}
