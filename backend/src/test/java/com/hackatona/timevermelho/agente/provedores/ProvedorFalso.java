package com.hackatona.timevermelho.agente.provedores;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Provedor de mentira para os testes: nenhuma chamada de rede, nenhum crédito gasto. */
public class ProvedorFalso implements ProvedorIA {

    final String nome;
    public boolean moderacao = true;
    /** Resposta de {@link #gerar} (usada pelo filtro de escopo). Pode lançar ErroProvedor. */
    public Function<List<Mensagem>, String> aoGerar = msgs -> "{\"categoria\":\"APLICACAO\",\"motivo\":\"ok\"}";
    /** Trechos que {@link #gerarStreaming} entrega em sequência. Pode lançar ErroProvedor. */
    public Function<List<Mensagem>, List<String>> aoStreamar = msgs -> List.of("Resposta ", "de teste.");
    /** {@code true} = texto sinalizado pela moderação. Pode lançar ErroProvedor. */
    public Predicate<String> aoModerar = texto -> false;
    /** Se não for nulo, é lançado depois de entregar {@link #falharDepoisDeTrechos} trechos. */
    public ErroProvedor falhaNoMeioDoStream;
    public int falharDepoisDeTrechos = 1;

    public final List<List<Mensagem>> chamadasGerar = new ArrayList<>();
    public final List<List<Mensagem>> chamadasStream = new ArrayList<>();
    public final List<String> textosModerados = new ArrayList<>();
    public Opcoes ultimasOpcoesGerar;

    public ProvedorFalso(String nome) {
        this.nome = nome;
    }

    public static ProvedorFalso que(String nome, ErroProvedor.Tipo tipo) {
        ProvedorFalso p = new ProvedorFalso(nome);
        ErroProvedor erro = new ErroProvedor(tipo, nome + ": erro de teste");
        p.aoGerar = m -> { throw erro; };
        p.aoStreamar = m -> { throw erro; };
        p.aoModerar = t -> { throw erro; };
        return p;
    }

    @Override
    public String nome() {
        return nome;
    }

    @Override
    public boolean suportaModeracao() {
        return moderacao;
    }

    @Override
    public Resposta gerar(List<Mensagem> mensagens, Opcoes opcoes) {
        chamadasGerar.add(mensagens);
        ultimasOpcoesGerar = opcoes;
        return new Resposta(aoGerar.apply(mensagens), 10, 5);
    }

    @Override
    public Resposta gerarStreaming(List<Mensagem> mensagens, Opcoes opcoes, Consumer<String> aoReceber) {
        chamadasStream.add(mensagens);
        List<String> trechos = aoStreamar.apply(mensagens);
        StringBuilder todo = new StringBuilder();
        int n = 0;
        for (String t : trechos) {
            if (falhaNoMeioDoStream != null && n == falharDepoisDeTrechos) throw falhaNoMeioDoStream;
            aoReceber.accept(t);
            todo.append(t);
            n++;
        }
        return new Resposta(todo.toString(), 20, 8);
    }

    @Override
    public boolean moderar(String texto) {
        textosModerados.add(texto);
        return aoModerar.test(texto);
    }
}
