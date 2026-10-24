package com.hackatona.timevermelho.agente.provedores;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Junta vários provedores em ordem de preferência e troca de um para o outro sozinho.
 *
 * <ul>
 *   <li>crédito esgotado ou chave recusada: o provedor sai da rotação por um tempo e passa para o próximo;</li>
 *   <li>limite de requisições momentâneo: espera e tenta de novo no mesmo provedor; se continuar, passa adiante;</li>
 *   <li>erro de servidor, timeout ou rede: o provedor fica de molho por pouco tempo e passa para o próximo;</li>
 *   <li>pedido inválido (erro nosso): não troca de provedor, o erro sobe;</li>
 *   <li>streaming que já mandou texto ao usuário não troca de provedor no meio: o erro sobe.</li>
 * </ul>
 *
 * Nenhum log desta classe contém conteúdo de conversa, chave ou resposta do provedor.
 */
public class ProvedoresIA {

    private static final Logger log = LoggerFactory.getLogger(ProvedoresIA.class);

    public record StatusProvedor(String nome, String situacao, Instant indisponivelAte, String motivo, long chamadas,
                                 long falhas, long tokensEntrada, long tokensSaida) {
    }

    private static final class Estado {
        final ProvedorIA provedor;
        volatile Instant indisponivelAte;
        volatile String motivo;
        final AtomicLong chamadas = new AtomicLong();
        final AtomicLong falhas = new AtomicLong();
        final AtomicLong tokensEntrada = new AtomicLong();
        final AtomicLong tokensSaida = new AtomicLong();

        Estado(ProvedorIA provedor) {
            this.provedor = provedor;
        }
    }

    private final List<Estado> estados;
    private final Duration cooldownCredito;
    private final Duration cooldownErro;
    private final int tentativasLimite;
    private final long esperaLimiteMs;
    private final Clock clock;

    public ProvedoresIA(List<ProvedorIA> provedores, Duration cooldownCredito, Duration cooldownErro,
                        int tentativasLimite, long esperaLimiteMs, Clock clock) {
        this.estados = provedores.stream().map(Estado::new).toList();
        this.cooldownCredito = cooldownCredito;
        this.cooldownErro = cooldownErro;
        this.tentativasLimite = Math.max(1, tentativasLimite);
        this.esperaLimiteMs = Math.max(0, esperaLimiteMs);
        this.clock = clock;
    }

    public boolean temProvedores() {
        return !estados.isEmpty();
    }

    public boolean algumSuportaModeracao() {
        return estados.stream().anyMatch(e -> e.provedor.suportaModeracao());
    }

    // ---------- operações ----------

    public ProvedorIA.Resposta gerar(List<Mensagem> mensagens, ProvedorIA.Opcoes opcoes) {
        return executar(p -> p.gerar(mensagens, opcoes), p -> true, () -> false, true);
    }

    public ProvedorIA.Resposta gerarStreaming(List<Mensagem> mensagens, ProvedorIA.Opcoes opcoes,
                                              Consumer<String> aoReceber) {
        AtomicBoolean emitiu = new AtomicBoolean();
        Consumer<String> marcando = trecho -> {
            emitiu.set(true);
            aoReceber.accept(trecho);
        };
        return executar(p -> p.gerarStreaming(mensagens, opcoes, marcando), p -> true, emitiu::get, true);
    }

    /** {@code true} se o texto foi sinalizado. Só usa provedores com moderação ligada. */
    public boolean moderar(String texto) {
        return executar(p -> p.moderar(texto), ProvedorIA::suportaModeracao, () -> false, false);
    }

    // ---------- núcleo da troca ----------

    private <T> T executar(Function<ProvedorIA, T> chamada, Predicate<ProvedorIA> elegivel,
                           java.util.function.BooleanSupplier jaEmitiu, boolean contaTokens) {
        for (Estado e : estados) {
            if (!elegivel.test(e.provedor) || !disponivel(e)) continue;
            int tentativa = 0;
            while (true) {
                tentativa++;
                e.chamadas.incrementAndGet();
                try {
                    T resultado = chamada.apply(e.provedor);
                    if (contaTokens && resultado instanceof ProvedorIA.Resposta r) {
                        e.tokensEntrada.addAndGet(r.tokensEntrada());
                        e.tokensSaida.addAndGet(r.tokensSaida());
                    }
                    return resultado;
                } catch (ErroProvedor erro) {
                    e.falhas.incrementAndGet();
                    boolean tentarDeNovo = tratar(e, erro, tentativa, jaEmitiu.getAsBoolean());
                    if (!tentarDeNovo) break;
                    dormir(esperaLimiteMs * tentativa);
                }
            }
        }
        throw new AgenteIndisponivelException("nenhum provedor de IA disponível no momento");
    }

    /** Decide o que fazer com o erro. Devolve {@code true} para tentar de novo no mesmo provedor. */
    private boolean tratar(Estado e, ErroProvedor erro, int tentativa, boolean jaEmitiu) {
        switch (erro.tipo()) {
            case PEDIDO_INVALIDO -> {
                log.error("agente: pedido recusado por {} ({}); não vou trocar de provedor", e.provedor.nome(), erro.getMessage());
                throw erro;
            }
            case LIMITE_TEMPORARIO -> {
                if (!jaEmitiu && tentativa < tentativasLimite) return true;
                log.warn("agente: {} segue no limite de requisições; passando para o próximo", e.provedor.nome());
            }
            case CREDITO_ESGOTADO, AUTENTICACAO ->
                    marcar(e, cooldownCredito, erro.tipo() == ErroProvedor.Tipo.CREDITO_ESGOTADO
                            ? "crédito esgotado" : "chave recusada");
            case INDISPONIVEL -> marcar(e, cooldownErro, "fora do ar ou lento");
        }
        if (jaEmitiu) throw erro; // o usuário já recebeu parte da resposta: não dá para trocar no meio
        return false;
    }

    private void marcar(Estado e, Duration duracao, String motivo) {
        e.indisponivelAte = clock.instant().plus(duracao);
        e.motivo = motivo;
        log.warn("agente: provedor {} fora da rotação até {} ({})", e.provedor.nome(), e.indisponivelAte, motivo);
    }

    private boolean disponivel(Estado e) {
        Instant ate = e.indisponivelAte;
        if (ate == null) return true;
        if (clock.instant().isBefore(ate)) return false;
        e.indisponivelAte = null;
        e.motivo = null;
        log.info("agente: provedor {} voltou para a rotação", e.provedor.nome());
        return true;
    }

    private static void dormir(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------- status (sem chaves) ----------

    public List<StatusProvedor> status() {
        return estados.stream().map(e -> {
            boolean disponivel = e.indisponivelAte == null || !clock.instant().isBefore(e.indisponivelAte);
            return new StatusProvedor(e.provedor.nome(), disponivel ? "ATIVO" : "INDISPONIVEL",
                    disponivel ? null : e.indisponivelAte, disponivel ? null : e.motivo, e.chamadas.get(),
                    e.falhas.get(), e.tokensEntrada.get(), e.tokensSaida.get());
        }).toList();
    }
}
