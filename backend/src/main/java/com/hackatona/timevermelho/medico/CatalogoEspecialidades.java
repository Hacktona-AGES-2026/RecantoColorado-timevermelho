package com.hackatona.timevermelho.medico;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.databind.json.JsonMapper;

/** As especialidades médicas disponíveis para cadastro, lidas de especialidades.json. */
public class CatalogoEspecialidades {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Arquivo(List<Especialidade> especialidades) {}

    private final Map<String, Especialidade> porCodigo = new LinkedHashMap<>();

    public CatalogoEspecialidades(List<Especialidade> especialidades) {
        for (Especialidade e : especialidades) {
            if (porCodigo.put(e.codigo(), e) != null) {
                throw new IllegalStateException("Especialidade repetida: " + e.codigo());
            }
        }
    }

    public static CatalogoEspecialidades carregarPadrao() {
        try (InputStream in = CatalogoEspecialidades.class.getResourceAsStream("/especialidades.json")) {
            if (in == null) throw new IllegalStateException("especialidades.json não encontrado no classpath");
            return new CatalogoEspecialidades(JsonMapper.builder().build().readValue(in, Arquivo.class).especialidades());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<Especialidade> todas() {
        return List.copyOf(porCodigo.values());
    }

    public Optional<Especialidade> buscar(String codigo) {
        return Optional.ofNullable(codigo == null ? null : porCodigo.get(codigo));
    }
}
