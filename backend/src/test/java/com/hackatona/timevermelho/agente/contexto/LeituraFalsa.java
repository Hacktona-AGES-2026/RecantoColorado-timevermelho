package com.hackatona.timevermelho.agente.contexto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Leitura de mentira para os testes: só números agregados, como o contrato manda. */
public class LeituraFalsa implements LeituraAplicacao {

    public final Map<String, Long> especialidades = new LinkedHashMap<>();
    public int leituras;

    public LeituraFalsa() {
        especialidades.put("Cirurgia Geral", 300L);
        especialidades.put("Urologia", 120L);
    }

    @Override
    public ResumoFila resumoFila() {
        leituras++;
        Map<String, Long> classes = new LinkedHashMap<>();
        classes.put("A1", 146L);
        classes.put("A2", 142L);
        classes.put("B", 224L);
        classes.put("C", 205L);
        classes.put("D", 202L);
        return new ResumoFila(919, 22, classes, new LinkedHashMap<>(especialidades));
    }

    @Override
    public List<Indicador> indicadores() {
        leituras++;
        return List.of(new Indicador("Oncológicos no primeiro terço da fila", "53%", "100%"),
                new Indicador("Oncológicos abaixo de um paciente sem urgência", "172", "0"));
    }

    @Override
    public List<GrupoReavaliacao> reavaliacoes() {
        leituras++;
        return List.of(new GrupoReavaliacao("Prioridade manual diverge da classe clínica", 74),
                new GrupoReavaliacao("Faltam dados clínicos para classificar com segurança", 63));
    }

    @Override
    public List<String> especialidades() {
        return List.copyOf(especialidades.keySet());
    }

    @Override
    public ResumoEspecialidade resumoEspecialidade(String especialidade) {
        leituras++;
        if (!especialidades.containsKey(especialidade)) return null;
        Map<String, Long> classes = new LinkedHashMap<>();
        classes.put("A1", 10L);
        classes.put("B", 50L);
        return new ResumoEspecialidade(especialidade, 120, classes, 7, 30, 2);
    }
}
