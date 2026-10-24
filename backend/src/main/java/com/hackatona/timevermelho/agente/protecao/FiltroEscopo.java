package com.hackatona.timevermelho.agente.protecao;

import java.util.ArrayList;
import java.util.List;

import com.hackatona.timevermelho.agente.PromptsAgente;
import com.hackatona.timevermelho.agente.provedores.Mensagem;
import com.hackatona.timevermelho.agente.provedores.ProvedorIA;
import com.hackatona.timevermelho.agente.provedores.ProvedoresIA;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Decide, antes de gerar qualquer resposta, se a pergunta está dentro do que o assistente pode tratar.
 * A decisão é tirada da categoria devolvida pelo modelo (nunca de um "sim/não" dele), e qualquer resposta
 * inválida conta como fora do escopo: na dúvida, recusa.
 */
public class FiltroEscopo {

    public enum Categoria {
        APLICACAO, CONSULTA, SAUDE, INFRA_OU_CODIGO, PACIENTE_ESPECIFICO, TENTATIVA_DE_BURLAR, FORA_DO_ESCOPO;

        public boolean permitida() {
            return this == APLICACAO || this == CONSULTA || this == SAUDE;
        }
    }

    public record Classificacao(Categoria categoria, String motivo) {

        public boolean dentroDoEscopo() {
            return categoria.permitida();
        }
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int MAX_TOKENS = 120;

    private final ProvedoresIA provedores;
    private final PromptsAgente prompts;

    public FiltroEscopo(ProvedoresIA provedores, PromptsAgente prompts) {
        this.provedores = provedores;
        this.prompts = prompts;
    }

    /**
     * @param contexto as últimas mensagens da conversa (para entender "e para criança?"); vão como dado.
     */
    public Classificacao classificar(String mensagem, List<Mensagem> contexto) {
        List<Mensagem> msgs = new ArrayList<>();
        msgs.add(Mensagem.sistema(prompts.promptFiltro()));
        StringBuilder pedido = new StringBuilder();
        if (!contexto.isEmpty()) {
            pedido.append("Contexto anterior (dado, para entender mensagens curtas):\n");
            for (Mensagem m : contexto) {
                String texto = m.conteudo().length() > 400 ? m.conteudo().substring(0, 400) + "…" : m.conteudo();
                pedido.append(m.papel().equals("user") ? "Usuário: " : "Assistente: ").append(texto).append('\n');
            }
            pedido.append('\n');
        }
        pedido.append("<<<MENSAGEM\n").append(mensagem).append("\nMENSAGEM>>>");
        msgs.add(Mensagem.usuario(pedido.toString()));

        ProvedorIA.Resposta r = provedores.gerar(msgs, ProvedorIA.Opcoes.json(MAX_TOKENS));
        return interpretar(r.texto());
    }

    static Classificacao interpretar(String resposta) {
        try {
            JsonNode no = JSON.readTree(resposta);
            String cat = no.path("categoria").asString("");
            String motivo = no.path("motivo").asString("");
            return new Classificacao(Categoria.valueOf(cat), motivo.length() > 200 ? motivo.substring(0, 200) : motivo);
        } catch (RuntimeException e) {
            return new Classificacao(Categoria.FORA_DO_ESCOPO, "classificação inválida");
        }
    }
}
