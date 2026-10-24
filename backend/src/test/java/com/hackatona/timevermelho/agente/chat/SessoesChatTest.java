package com.hackatona.timevermelho.agente.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.hackatona.timevermelho.agente.provedores.Mensagem;
import com.hackatona.timevermelho.agente.provedores.RelogioFalso;

/** T30: conversas isoladas por sessão, com histórico só no servidor. */
class SessoesChatTest {

    final RelogioFalso relogio = new RelogioFalso();
    final SessoesChat sessoes = new SessoesChat(Duration.ofMinutes(30), 100, 6, relogio);

    @Test
    void sessaoNovaTemIdAleatorioDe128Bits() {
        var a = sessoes.obter(null);
        var b = sessoes.obter(null);
        assertThat(a.id()).hasSize(32).matches("[0-9a-f]{32}");
        assertThat(a.id()).isNotEqualTo(b.id());
    }

    @Test
    void mesmoIdVoltaAMesmaSessao() {
        var a = sessoes.obter(null);
        assertThat(sessoes.obter(a.id())).isSameAs(a);
    }

    @Test
    void idDesconhecidoGanhaSessaoNovaComOutroId() {
        var s = sessoes.obter("id-que-eu-inventei");
        assertThat(s.id()).isNotEqualTo("id-que-eu-inventei");
        assertThat(s.ultimas(10)).isEmpty();
    }

    @Test
    void duasSessoesNuncaCompartilhamHistorico() {
        var a = sessoes.obter(null);
        var b = sessoes.obter(null);
        sessoes.registrar(a, "pergunta da Ana", "resposta para a Ana");
        sessoes.registrar(b, "pergunta da Bia", "resposta para a Bia");

        assertThat(a.ultimas(10)).extracting(Mensagem::conteudo).containsExactly("pergunta da Ana", "resposta para a Ana");
        assertThat(b.ultimas(10)).extracting(Mensagem::conteudo).containsExactly("pergunta da Bia", "resposta para a Bia");
    }

    @Test
    void historicoGuardaOsPapeisCertos() {
        var s = sessoes.obter(null);
        sessoes.registrar(s, "oi", "olá");
        assertThat(s.ultimas(10)).extracting(Mensagem::papel).containsExactly("user", "assistant");
    }

    @Test
    void historicoTemTamanhoMaximoEDescartaOMaisAntigo() {
        var s = sessoes.obter(null);
        for (int i = 1; i <= 5; i++) sessoes.registrar(s, "p" + i, "r" + i); // 10 mensagens, máximo 6
        assertThat(s.ultimas(100)).extracting(Mensagem::conteudo).containsExactly("p3", "r3", "p4", "r4", "p5", "r5");
        assertThat(s.ultimas(2)).extracting(Mensagem::conteudo).containsExactly("p5", "r5");
    }

    @Test
    void sessaoExpiradaSomeEOIdAntigoNaoVoltaAosDados() {
        var s = sessoes.obter(null);
        sessoes.registrar(s, "segredo", "resposta");
        relogio.avancar(Duration.ofMinutes(31));

        var nova = sessoes.obter(s.id());
        assertThat(nova).isNotSameAs(s);
        assertThat(nova.ultimas(10)).isEmpty();
    }

    @Test
    void usarASessaoRenovaOPrazo() {
        var s = sessoes.obter(null);
        relogio.avancar(Duration.ofMinutes(20));
        sessoes.obter(s.id());
        relogio.avancar(Duration.ofMinutes(20));
        assertThat(sessoes.obter(s.id())).isSameAs(s);
    }

    @Test
    void passandoDoMaximoDeSessoesAMaisAntigaSai() {
        var pequeno = new SessoesChat(Duration.ofMinutes(30), 2, 6, relogio);
        var a = pequeno.obter(null);
        relogio.avancar(Duration.ofSeconds(1));
        var b = pequeno.obter(null);
        relogio.avancar(Duration.ofSeconds(1));
        pequeno.obter(null);

        assertThat(pequeno.total()).isEqualTo(2);
        assertThat(pequeno.obter(b.id())).isSameAs(b);
        assertThat(pequeno.obter(a.id())).isNotSameAs(a);
    }
}
