package com.hackatona.timevermelho.agente.contexto;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hackatona.timevermelho.agente.provedores.Mensagem;
import com.hackatona.timevermelho.agente.provedores.ProvedorIA;
import com.hackatona.timevermelho.agente.provedores.ProvedoresIA;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * O "function calling" do agente: a IA lê a pergunta e devolve, em JSON, os filtros de uma consulta à fila
 * (prompt em {@code resources/agente/consulta-fila.md}, com o catálogo de nomes que existem). O código valida os
 * filtros ({@link ConsultaFila#deJson}) e roda a consulta; a IA nunca vê as linhas dos pacientes.
 *
 * <p>Falha fecha: JSON inválido, prompt ausente ou provedor fora do ar = nenhuma consulta (o chat segue sem dados).
 */
public class PlanejadorConsulta {

    private static final Logger log = LoggerFactory.getLogger(PlanejadorConsulta.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int MAX_TOKENS = 400;

    private final ProvedoresIA provedores;
    private final LeituraAplicacao leitura;
    private final String prompt;

    public PlanejadorConsulta(ProvedoresIA provedores, LeituraAplicacao leitura) {
        this(provedores, leitura, ler("agente/consulta-fila.md"));
    }

    public PlanejadorConsulta(ProvedoresIA provedores, LeituraAplicacao leitura, String prompt) {
        this.provedores = provedores;
        this.leitura = leitura;
        this.prompt = prompt == null ? "" : prompt.strip();
    }

    /** Os filtros para a pergunta, ou vazio se ela não pede dados da fila (ou se algo falhou). */
    public Optional<ConsultaFila> planejar(String mensagem, List<Mensagem> contexto) {
        if (prompt.isEmpty()) return Optional.empty();
        try {
            LeituraAplicacao.Catalogo catalogo = leitura.catalogo();
            List<Mensagem> msgs = new ArrayList<>();
            msgs.add(Mensagem.sistema(prompt + "\n\n## CATÁLOGO (nomes que existem na fila)\n\nProcedimentos: "
                    + String.join("; ", catalogo.procedimentos()) + "\n\nEspecialidades: "
                    + String.join("; ", catalogo.especialidades()) + "\n\nMunicípios: "
                    + String.join("; ", catalogo.municipios())));
            StringBuilder pedido = new StringBuilder();
            if (!contexto.isEmpty()) {
                pedido.append("Contexto anterior (dado, para entender continuações):\n");
                for (Mensagem m : contexto) {
                    String t = m.conteudo().length() > 300 ? m.conteudo().substring(0, 300) + "…" : m.conteudo();
                    pedido.append(m.papel().equals("user") ? "Usuário: " : "Assistente: ").append(t).append('\n');
                }
                pedido.append('\n');
            }
            pedido.append("<<<MENSAGEM\n").append(mensagem).append("\nMENSAGEM>>>");
            msgs.add(Mensagem.usuario(pedido.toString()));
            var resposta = provedores.gerar(msgs, ProvedorIA.Opcoes.json(MAX_TOKENS));
            return interpretar(resposta.texto(), catalogo);
        } catch (RuntimeException e) {
            log.warn("agente: planejador de consulta falhou ({}); seguindo sem consulta", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Roda a consulta já validada (só leitura). */
    public LeituraAplicacao.ResultadoConsulta consultar(ConsultaFila consulta) {
        return leitura.consultar(consulta);
    }

    static Optional<ConsultaFila> interpretar(String resposta, LeituraAplicacao.Catalogo catalogo) {
        try {
            JsonNode no = JSON.readTree(resposta);
            if (!no.path("consultar").asBoolean(false)) return Optional.empty();
            return Optional.of(ConsultaFila.deJson(no.path("filtros"), no.path("listar").asBoolean(false), catalogo));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String ler(String caminho) {
        try (InputStream in = PlanejadorConsulta.class.getClassLoader().getResourceAsStream(caminho)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
