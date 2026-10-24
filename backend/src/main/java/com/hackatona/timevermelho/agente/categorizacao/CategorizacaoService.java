package com.hackatona.timevermelho.agente.categorizacao;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hackatona.timevermelho.agente.PromptsAgente;
import com.hackatona.timevermelho.agente.PromptsTarefas;
import com.hackatona.timevermelho.agente.chat.Excecoes.AgenteDesligadoException;
import com.hackatona.timevermelho.agente.chat.Excecoes.LimiteExcedidoException;
import com.hackatona.timevermelho.agente.chat.Excecoes.MensagemInvalidaException;
import com.hackatona.timevermelho.agente.chat.LimitadorUso;
import com.hackatona.timevermelho.agente.categorizacao.CategorizacaoSugerida.Origem;
import com.hackatona.timevermelho.agente.categorizacao.CategorizacaoSugerida.StatusRevisao;
import com.hackatona.timevermelho.agente.protecao.ProtecaoEntrada;
import com.hackatona.timevermelho.agente.provedores.Mensagem;
import com.hackatona.timevermelho.agente.provedores.ProvedorIA;
import com.hackatona.timevermelho.agente.provedores.ProvedoresIA;
import com.hackatona.timevermelho.servico.Erros;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Categoriza textos por assunto (T15) e guarda a sugestão para revisão humana (T16). A IA só organiza: nada aqui
 * muda classe SWALIS, posição na fila nem qualquer dado oficial; o resultado vai para uma tabela própria.
 */
public class CategorizacaoService {

    public static final int MAX_TEXTO = 2000;
    public static final String AVISO = "Sugestão gerada por IA, para revisão humana. Não altera classe nem posição "
            + "na fila e não indica gravidade nem urgência.";

    public enum Acao { ACEITAR, CORRIGIR }

    private static final Logger log = LoggerFactory.getLogger(CategorizacaoService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ProvedoresIA provedores;
    private final PromptsAgente prompts;
    private final PromptsTarefas tarefas;
    private final ProtecaoEntrada protecao;
    private final CategorizacaoRepository repositorio;
    private final LimitadorUso limitador;
    private final Supplier<String> motivoDesligado;
    private final Clock clock;

    public CategorizacaoService(ProvedoresIA provedores, PromptsAgente prompts, PromptsTarefas tarefas,
                                ProtecaoEntrada protecao, CategorizacaoRepository repositorio, LimitadorUso limitador,
                                Supplier<String> motivoDesligado, Clock clock) {
        this.provedores = provedores;
        this.prompts = prompts;
        this.tarefas = tarefas;
        this.protecao = protecao;
        this.repositorio = repositorio;
        this.limitador = limitador;
        this.motivoDesligado = motivoDesligado;
        this.clock = clock;
    }

    public CategorizacaoSugerida categorizar(String textoBruto, String solicitante, String ip) {
        String motivo = motivoDesligado.get();
        if (motivo == null && !tarefas.pronto()) motivo = tarefas.motivoIndisponivel();
        if (motivo != null) throw new AgenteDesligadoException(motivo);
        String texto = textoBruto == null ? "" : textoBruto.strip();
        if (texto.isEmpty()) throw new MensagemInvalidaException("O texto não pode ser vazio.");
        if (texto.length() > MAX_TEXTO) {
            throw new MensagemInvalidaException("O texto pode ter no máximo " + MAX_TEXTO + " caracteres.");
        }
        if (!limitador.permitir("cat:" + ip)) throw new LimiteExcedidoException();

        CategorizacaoSugerida s = new CategorizacaoSugerida();
        s.setId(UUID.randomUUID().toString());
        s.setTexto(texto);
        s.setSolicitante(limpar(solicitante, 60, "anônimo"));
        s.setCriadoEm(LocalDateTime.now(clock));
        s.setVersaoPrompt(tarefas.versao());

        var barreira = protecao.avaliar(texto);
        if (barreira.isPresent() && barreira.get().tipo() == ProtecaoEntrada.Tipo.RISCO_A_SAUDE) {
            // emergência: nem chama a IA; só marca para um humano olhar
            preencher(s, CategoriaDado.SINTOMA_RELATADO, List.of(), 1.0, Origem.REGRA_FIXA, true,
                    "Texto com sinal de emergência de saúde: precisa de atenção humana imediata.");
        } else if (barreira.isPresent()) {
            preencher(s, CategoriaDado.OUTRO, List.of(), 1.0, Origem.REGRA_FIXA, false,
                    "O texto parece tentar mudar as regras do assistente.");
        } else {
            classificarComIa(s, texto);
        }
        log.info("agente: categorizacao id={} origem={} categoria={} atencao_humana={}", s.getId().substring(0, 8),
                s.getOrigem(), s.getCategoriaSugerida(), s.isAtencaoHumana());
        return repositorio.save(s);
    }

    private void classificarComIa(CategorizacaoSugerida s, String texto) {
        String sistema = tarefas.categorizacao();
        if (!prompts.regrasDeNegocio().isEmpty()) {
            sistema += "\n\n## Regras de negócio do FilaVIVA\n\n" + prompts.regrasDeNegocio();
        }
        var r = provedores.gerar(List.of(Mensagem.sistema(sistema),
                Mensagem.usuario("<<<TEXTO\n" + texto + "\nTEXTO>>>")), ProvedorIA.Opcoes.json(300));
        try {
            JsonNode no = JSON.readTree(r.texto());
            CategoriaDado categoria = CategoriaDado.valueOf(no.path("categoria").asString(""));
            List<String> subtemas = new ArrayList<>();
            for (JsonNode t : no.path("subtemas")) {
                String limpo = limpar(t.asString(""), 30, "");
                if (!limpo.isEmpty() && subtemas.size() < 3) subtemas.add(limpo);
            }
            double confianca = Math.max(0, Math.min(1, no.path("confianca").asDouble(0)));
            preencher(s, categoria, subtemas, confianca, Origem.IA, false, limpar(no.path("justificativa").asString(""), 300, ""));
        } catch (RuntimeException e) {
            // resposta fora do formato: não inventa categoria
            preencher(s, CategoriaDado.OUTRO, List.of(), 0.0, Origem.IA, false, "Classificação inválida; revise à mão.");
        }
    }

    private static void preencher(CategorizacaoSugerida s, CategoriaDado categoria, List<String> subtemas, double confianca,
                                  Origem origem, boolean atencaoHumana, String justificativa) {
        s.setCategoriaSugerida(categoria);
        s.setSubtemas(String.join(", ", subtemas));
        s.setConfianca(Math.round(confianca * 100) / 100.0);
        s.setOrigem(origem);
        s.setAtencaoHumana(atencaoHumana);
        s.setJustificativa(justificativa);
    }

    // ---------- revisão humana (T16) ----------

    public CategorizacaoSugerida revisar(String id, Acao acao, CategoriaDado corrigida, String justificativa, String autor) {
        CategorizacaoSugerida s = buscar(id);
        if (s.getStatusRevisao() != StatusRevisao.PENDENTE) {
            throw new Erros.RegraNegocio("Esta categorização já foi revisada.");
        }
        String motivo = limpar(justificativa, 500, "");
        if (acao == Acao.CORRIGIR) {
            if (corrigida == null) throw new Erros.RegraNegocio("Informe a categoria corrigida.");
            if (motivo.length() < 5) throw new Erros.RegraNegocio("Informe o motivo da correção (mínimo de 5 caracteres).");
            s.setCategoriaFinal(corrigida);
            s.setStatusRevisao(StatusRevisao.CORRIGIDA);
        } else {
            s.setCategoriaFinal(s.getCategoriaSugerida());
            s.setStatusRevisao(StatusRevisao.ACEITA);
        }
        s.setJustificativaRevisao(motivo.isEmpty() ? null : motivo);
        s.setRevisadoPor(limpar(autor, 60, "revisor"));
        s.setRevisadoEm(LocalDateTime.now(clock));
        return repositorio.save(s);
    }

    public CategorizacaoSugerida buscar(String id) {
        return repositorio.findById(id).orElseThrow(() -> new Erros.NaoEncontrado("Categorização " + id + " não encontrada"));
    }

    public List<CategorizacaoSugerida> listar(StatusRevisao status) {
        return status == null ? repositorio.findTop50ByOrderByCriadoEmDesc()
                : repositorio.findTop50ByStatusRevisaoOrderByCriadoEmDesc(status);
    }

    /** Tira caracteres de controle e símbolos de marcação, junta espaços e corta no tamanho. */
    static String limpar(String bruto, int max, String padrao) {
        if (bruto == null) return padrao;
        String t = bruto.replaceAll("[\\p{Cntrl}<>`{}$\\\\]", " ").replaceAll("\\s+", " ").strip();
        if (t.length() > max) t = t.substring(0, max).strip();
        return t.isEmpty() ? padrao : t;
    }
}
