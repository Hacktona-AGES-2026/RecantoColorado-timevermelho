package com.hackatona.timevermelho.agente.contexto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Leitura de mentira para os testes: números agregados e, na consulta, duas linhas de pacientes inventados. */
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

    @Override
    public Catalogo catalogo() {
        return new Catalogo(List.of("Histerectomia", "Herniorrafia inguinal"), especialidades(), List.of("Porto Alegre"));
    }

    /** Dois pacientes de mentira; o resumo (texto para a IA) só tem números. */
    @Override
    public ResultadoConsulta consultar(ConsultaFila c) {
        leituras++;
        var linhas = List.of(
                new LinhaPaciente(1, "SOL-2026-00489", "Eliane Lopes Ramos", 50, "F", "Porto Alegre", "Histerectomia",
                        "Ginecologia", "C54.1", "Neoplasia maligna do endometrio", true, "A1", 86.5, 138, 15, "ATIVA"),
                new LinhaPaciente(40, "SOL-2025-00010", "Maria | Souza", 44, "F", "Canoas", "Histerectomia", "Ginecologia",
                        "D25", "Leiomioma do utero", false, "C", 40.0, 90, 180, "ATIVA"));
        return new ResultadoConsulta(2, "Filtros aplicados: " + c.descricaoParaIA() + ".\nPacientes encontrados: 2.\n"
                + "Por classe: A1 1; C 1.\nOncológicos: 1.", c.listar() ? linhas : List.of());
    }
}
