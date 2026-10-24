package com.hackatona.timevermelho.motor.texto;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.databind.json.JsonMapper;

/** Conteúdo do regras.json: dicionários do texto livre e listas de CID. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RegrasClinicas(
        List<Termo> alarmesGraves,
        List<Termo> suspeitaMalignidade,
        List<Termo> alarmesFuncionais,
        List<Termo> gravidadeClinica,
        List<Termo> psNoTexto,
        List<Termo> piora,
        List<Termo> melhora,
        List<Termo> saidaDesistencia,
        List<Termo> saidaOutroServico,
        List<String> negacoes,
        Map<String, String> cidsUrgentes,
        String cidsIncertos) {

    public record Termo(String padrao, String rotulo) {}

    public static RegrasClinicas carregarPadrao() {
        try (InputStream in = RegrasClinicas.class.getResourceAsStream("/regras.json")) {
            if (in == null) throw new IllegalStateException("regras.json não encontrado no classpath");
            return JsonMapper.builder().build().readValue(in, RegrasClinicas.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
