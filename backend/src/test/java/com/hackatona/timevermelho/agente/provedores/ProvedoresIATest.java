package com.hackatona.timevermelho.agente.provedores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hackatona.timevermelho.agente.provedores.ErroProvedor.Tipo;

/** T5 e T44: troca de provedor quando o crédito acaba, sem gastar crédito de verdade. */
class ProvedoresIATest {

    static final List<Mensagem> PEDIDO = List.of(Mensagem.usuario("oi"));
    static final ProvedorIA.Opcoes OPCOES = ProvedorIA.Opcoes.texto(100);

    RelogioFalso relogio;

    @BeforeEach
    void preparar() {
        relogio = new RelogioFalso();
    }

    ProvedoresIA roteador(ProvedorIA... provedores) {
        return new ProvedoresIA(List.of(provedores), Duration.ofMinutes(60), Duration.ofSeconds(30), 2, 0, relogio);
    }

    private ProvedoresIA.StatusProvedor status(ProvedoresIA r, String nome) {
        return r.status().stream().filter(s -> s.nome().equals(nome)).findFirst().orElseThrow();
    }

    // ---------- uso normal ----------

    @Test
    void usaOPrimeiroProvedorEnquantoElePuder() {
        var a = new ProvedorFalso("a");
        var b = new ProvedorFalso("b");
        roteador(a, b).gerar(PEDIDO, OPCOES);
        assertThat(a.chamadasGerar).hasSize(1);
        assertThat(b.chamadasGerar).isEmpty();
    }

    @Test
    void semProvedoresNenhumaChamadaFunciona() {
        var r = roteador();
        assertThat(r.temProvedores()).isFalse();
        assertThatThrownBy(() -> r.gerar(PEDIDO, OPCOES)).isInstanceOf(AgenteIndisponivelException.class);
    }

    // ---------- crédito esgotado ----------

    @Test
    void creditoEsgotadoPassaParaOProximoESaiDaRotacao() {
        var a = ProvedorFalso.que("a", Tipo.CREDITO_ESGOTADO);
        var b = new ProvedorFalso("b");
        var r = roteador(a, b);

        r.gerar(PEDIDO, OPCOES);
        r.gerar(PEDIDO, OPCOES);

        assertThat(a.chamadasGerar).hasSize(1); // na 2ª vez o "a" nem foi tentado
        assertThat(b.chamadasGerar).hasSize(2);
        assertThat(status(r, "a").situacao()).isEqualTo("INDISPONIVEL");
        assertThat(status(r, "a").motivo()).isEqualTo("crédito esgotado");
        assertThat(status(r, "b").situacao()).isEqualTo("ATIVO");
    }

    @Test
    void provedorSemCreditoVoltaDepoisDoTempoDeEspera() {
        var a = ProvedorFalso.que("a", Tipo.CREDITO_ESGOTADO);
        var b = new ProvedorFalso("b");
        var r = roteador(a, b);
        r.gerar(PEDIDO, OPCOES);

        a.aoGerar = m -> "{\"ok\":true}"; // o crédito foi recarregado
        relogio.avancar(Duration.ofMinutes(59));
        r.gerar(PEDIDO, OPCOES);
        assertThat(a.chamadasGerar).hasSize(1); // ainda de molho

        relogio.avancar(Duration.ofMinutes(2));
        r.gerar(PEDIDO, OPCOES);
        assertThat(a.chamadasGerar).hasSize(2); // voltou
        assertThat(status(r, "a").situacao()).isEqualTo("ATIVO");
    }

    @Test
    void chaveRecusadaTambemTiraOProvedorDaRotacao() {
        var a = ProvedorFalso.que("a", Tipo.AUTENTICACAO);
        var b = new ProvedorFalso("b");
        var r = roteador(a, b);
        r.gerar(PEDIDO, OPCOES);
        assertThat(b.chamadasGerar).hasSize(1);
        assertThat(status(r, "a").motivo()).isEqualTo("chave recusada");
    }

    // ---------- limite momentâneo ----------

    @Test
    void limiteMomentaneoTentaDeNovoNoMesmoProvedor() {
        var a = new ProvedorFalso("a");
        int[] chamadas = {0};
        a.aoGerar = m -> {
            if (++chamadas[0] == 1) throw new ErroProvedor(Tipo.LIMITE_TEMPORARIO, "a: HTTP 429");
            return "{\"ok\":true}";
        };
        var b = new ProvedorFalso("b");
        var r = roteador(a, b);

        assertThat(r.gerar(PEDIDO, OPCOES).texto()).isEqualTo("{\"ok\":true}");
        assertThat(a.chamadasGerar).hasSize(2);
        assertThat(b.chamadasGerar).isEmpty();
        assertThat(status(r, "a").situacao()).isEqualTo("ATIVO");
    }

    @Test
    void limiteQueNaoPassaDepoisDasTentativasSegueParaOProximoSemTirarDaRotacao() {
        var a = ProvedorFalso.que("a", Tipo.LIMITE_TEMPORARIO);
        var b = new ProvedorFalso("b");
        var r = roteador(a, b);

        r.gerar(PEDIDO, OPCOES);

        assertThat(a.chamadasGerar).hasSize(2); // tentativasLimite = 2
        assertThat(b.chamadasGerar).hasSize(1);
        assertThat(status(r, "a").situacao()).isEqualTo("ATIVO"); // limite é momentâneo: não é falta de crédito
    }

    // ---------- servidor fora do ar ----------

    @Test
    void erroDeServidorPassaParaOProximoPorPoucoTempo() {
        var a = ProvedorFalso.que("a", Tipo.INDISPONIVEL);
        var b = new ProvedorFalso("b");
        var r = roteador(a, b);
        r.gerar(PEDIDO, OPCOES);
        assertThat(status(r, "a").situacao()).isEqualTo("INDISPONIVEL");

        a.aoGerar = m -> "{\"ok\":true}";
        relogio.avancar(Duration.ofSeconds(31));
        r.gerar(PEDIDO, OPCOES);
        assertThat(a.chamadasGerar).hasSize(2);
    }

    // ---------- erro nosso ----------

    @Test
    void pedidoInvalidoNaoTrocaDeProvedorEOErroSobe() {
        var a = ProvedorFalso.que("a", Tipo.PEDIDO_INVALIDO);
        var b = new ProvedorFalso("b");
        var r = roteador(a, b);

        assertThatThrownBy(() -> r.gerar(PEDIDO, OPCOES))
                .isInstanceOf(ErroProvedor.class)
                .extracting(e -> ((ErroProvedor) e).tipo()).isEqualTo(Tipo.PEDIDO_INVALIDO);
        assertThat(b.chamadasGerar).isEmpty();
        assertThat(status(r, "a").situacao()).isEqualTo("ATIVO");
    }

    // ---------- todos falham ----------

    @Test
    void seTodosFalharemOAgenteFicaIndisponivel() {
        var r = roteador(ProvedorFalso.que("a", Tipo.CREDITO_ESGOTADO), ProvedorFalso.que("b", Tipo.INDISPONIVEL));
        assertThatThrownBy(() -> r.gerar(PEDIDO, OPCOES)).isInstanceOf(AgenteIndisponivelException.class);
        // e continua indisponível sem martelar os provedores enquanto esperam
        assertThatThrownBy(() -> r.gerar(PEDIDO, OPCOES)).isInstanceOf(AgenteIndisponivelException.class);
        assertThat(status(r, "a").chamadas()).isEqualTo(1);
        assertThat(status(r, "b").chamadas()).isEqualTo(1);
    }

    @Test
    void trocaPassaPorTresProvedoresEmOrdem() {
        var a = ProvedorFalso.que("a", Tipo.CREDITO_ESGOTADO);
        var b = ProvedorFalso.que("b", Tipo.CREDITO_ESGOTADO);
        var c = new ProvedorFalso("c");
        roteador(a, b, c).gerar(PEDIDO, OPCOES);
        assertThat(c.chamadasGerar).hasSize(1);
    }

    // ---------- streaming ----------

    @Test
    void streamingTrocaDeProvedorSeFalharAntesDoPrimeiroTrecho() {
        var a = ProvedorFalso.que("a", Tipo.CREDITO_ESGOTADO);
        var b = new ProvedorFalso("b");
        List<String> recebidos = new ArrayList<>();
        roteador(a, b).gerarStreaming(PEDIDO, OPCOES, recebidos::add);
        assertThat(recebidos).containsExactly("Resposta ", "de teste.");
    }

    @Test
    void streamingNaoTrocaDeProvedorDepoisDeJaTerMandadoTexto() {
        var a = new ProvedorFalso("a");
        a.falhaNoMeioDoStream = new ErroProvedor(Tipo.INDISPONIVEL, "a: caiu");
        var b = new ProvedorFalso("b");
        var r = roteador(a, b);
        List<String> recebidos = new ArrayList<>();

        assertThatThrownBy(() -> r.gerarStreaming(PEDIDO, OPCOES, recebidos::add)).isInstanceOf(ErroProvedor.class);

        assertThat(recebidos).containsExactly("Resposta "); // o que já foi entregue não se repete em outro provedor
        assertThat(b.chamadasStream).isEmpty();
        assertThat(status(r, "a").situacao()).isEqualTo("INDISPONIVEL");
    }

    // ---------- moderação ----------

    @Test
    void moderacaoSoUsaProvedoresQueSuportam() {
        var semModeracao = new ProvedorFalso("a");
        semModeracao.moderacao = false;
        var comModeracao = new ProvedorFalso("b");
        comModeracao.aoModerar = t -> true;
        var r = roteador(semModeracao, comModeracao);

        assertThat(r.moderar("texto")).isTrue();
        assertThat(semModeracao.textosModerados).isEmpty();
        assertThat(r.algumSuportaModeracao()).isTrue();
    }

    @Test
    void semNenhumProvedorComModeracaoAChamadaFalha() {
        var a = new ProvedorFalso("a");
        a.moderacao = false;
        var r = roteador(a);
        assertThat(r.algumSuportaModeracao()).isFalse();
        assertThatThrownBy(() -> r.moderar("texto")).isInstanceOf(AgenteIndisponivelException.class);
    }

    // ---------- contagem e status ----------

    @Test
    void statusContaChamadasFalhasETokensSemExporNadaSecreto() {
        var a = ProvedorFalso.que("a", Tipo.CREDITO_ESGOTADO);
        var b = new ProvedorFalso("b");
        var r = roteador(a, b);
        r.gerar(PEDIDO, OPCOES);
        r.gerar(PEDIDO, OPCOES);

        assertThat(status(r, "a").falhas()).isEqualTo(1);
        assertThat(status(r, "b").chamadas()).isEqualTo(2);
        assertThat(status(r, "b").tokensEntrada()).isEqualTo(20);
        assertThat(status(r, "b").tokensSaida()).isEqualTo(10);
        assertThat(ProvedoresIA.StatusProvedor.class.getRecordComponents()).extracting(c -> c.getName())
                .containsExactly("nome", "situacao", "indisponivelAte", "motivo", "chamadas", "falhas",
                        "tokensEntrada", "tokensSaida");
    }
}
