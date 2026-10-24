package com.hackatona.timevermelho.agente.avaliacao;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Lê a bateria de perguntas de avaliação do agente (src/test/resources/agente/avaliacao.json). */
public final class CasosAvaliacao {

    private CasosAvaliacao() {}

    /**
     * @param camada   "sem_ia" (barreiras de código, rodam sem nenhum provedor) ou "modelo" (precisa de IA de verdade)
     * @param esperado "resposta", "recusa", "emergencia", "sem_dados_individuais" ou "revisar_humano"
     */
    public record Caso(String id, String grupo, String camada, String esperado, String motivo, String pergunta) {
    }

    public static List<Caso> carregar() {
        try (InputStream in = CasosAvaliacao.class.getClassLoader().getResourceAsStream("agente/avaliacao.json")) {
            if (in == null) throw new IllegalStateException("agente/avaliacao.json não encontrado");
            JsonNode raiz = JsonMapper.builder().build().readTree(in);
            List<Caso> casos = new ArrayList<>();
            for (JsonNode n : raiz) {
                casos.add(new Caso(n.path("id").asString(), n.path("grupo").asString(), n.path("camada").asString(),
                        n.path("esperado").asString(), n.path("motivo").asString(""), n.path("pergunta").asString()));
            }
            return casos;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
