package com.hackatona.timevermelho.agente.protecao;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import com.hackatona.timevermelho.motor.texto.ExtratorDeTexto;

/**
 * Segunda barreira sem IA, específica do Desafio 1: segura, antes de qualquer chamada a provedor externo, os
 * pedidos que saem do papel do assistente na fila cirúrgica.
 *
 * <ul>
 *   <li><b>Dado pessoal</b> (CPF, cartão do SUS): a mensagem não vai para a IA nem fica no histórico.</li>
 *   <li><b>Paciente específico</b> (número de solicitação {@code SOL-AAAA-NNNNN}): o assistente não tem dado
 *       individual, e o identificador não sai da aplicação.</li>
 *   <li><b>Rebaixar oncológico</b>: a regra de ouro não tem exceção; a resposta explica o caminho certo.</li>
 *   <li><b>Furar a fila</b>: não existe atalho; a resposta explica como a piora entra como evento clínico.</li>
 * </ul>
 *
 * <p>Nenhum desses casos conta como abuso: são perguntas honestas que recebem uma resposta fixa e segura.
 * Os padrões são simples de propósito; o filtro de escopo (categoria {@code PACIENTE_ESPECIFICO}) cobre o resto.
 */
public class LimitesDoContexto {

    public enum Tipo { DADO_PESSOAL, PACIENTE_ESPECIFICO, REBAIXAR_ONCOLOGICO, FURAR_FILA }

    public record Recusa(Tipo tipo, String texto) {
    }

    // Texto normalizado: minúsculas e sem acento.
    private static final List<Pattern> DADO_PESSOAL = compilar(
            "\\b\\d{3}\\.\\d{3}\\.\\d{3}-?\\d{2}\\b",                 // CPF com pontos
            "\\bcpf\\D{0,15}\\d{11}\\b",                              // "meu cpf é 12345678901"
            "\\b[1-9]\\d{2}[ .]?\\d{4}[ .]?\\d{4}[ .]?\\d{4}\\b");     // cartão nacional de saúde (15 dígitos)

    private static final List<Pattern> PACIENTE_ESPECIFICO = compilar("\\bsol[- ]?\\d{4}[- ]?\\d{3,6}\\b");

    private static final List<Pattern> REBAIXAR_ONCOLOGICO = compilar(
            "\\b(rebaix\\w*|baixar|descer|tirar|remover|empurrar|jogar|colocar|deixar|passar)\\b[^.?!]{0,50}"
                    + "\\b(oncolog\\w*|cancer\\w*|neoplasia\\w*|tumor\\w*)\\b[^.?!]{0,40}"
                    + "\\b(abaixo|atras|tras|para baixo|pro fim|para o fim|fim da fila|classe [bcd])\\b",
            "\\b(oncolog\\w*|cancer\\w*|neoplasia\\w*)\\b[^.?!]{0,30}\\b(para|pra) (a )?classe [bcd]\\b");

    private static final List<Pattern> FURAR_FILA = compilar(
            "\\bfur(ar|o|a|ei|ou|amos)\\b[^.?!]{0,15}\\bfila\\b", "\\bpul(ar|o|ei|ou)\\b[^.?!]{0,15}\\bfila\\b",
            "\\bpass(ar|o|ei|e|em)\\b[^.?!]{0,20}\\bna frente\\b", "\\bpular (a )?vez\\b",
            "\\b(exagerar|inventar|mentir sobre|fingir)\\b[^.?!]{0,25}\\b(sintoma|dor|piora|doenca)\\w*",
            "\\b(adiantar|antecipar|acelerar)\\b[^.?!]{0,20}\\b(minha|a minha|sua|a sua) (cirurgia|vez|posicao)\\b",
            "\\b(subir|melhorar)\\b[^.?!]{0,15}\\b(minha )?posicao\\b[^.?!]{0,30}\\b(sem|mesmo sem|conhec|parente|amigo|influencia|pistolao|jeitinho)",
            "\\b(jeitinho|pistolao|quem indica)\\b");

    private static List<Pattern> compilar(String... padroes) {
        return java.util.Arrays.stream(padroes).map(Pattern::compile).toList();
    }

    public Optional<Recusa> avaliar(String mensagem) {
        if (mensagem == null || mensagem.isBlank()) return Optional.empty();
        String t = ExtratorDeTexto.normalizar(mensagem);
        if (casa(DADO_PESSOAL, t)) return Optional.of(new Recusa(Tipo.DADO_PESSOAL, TextosAgente.DADO_PESSOAL));
        if (casa(PACIENTE_ESPECIFICO, t)) {
            return Optional.of(new Recusa(Tipo.PACIENTE_ESPECIFICO, TextosAgente.PACIENTE_ESPECIFICO));
        }
        if (casa(REBAIXAR_ONCOLOGICO, t)) {
            return Optional.of(new Recusa(Tipo.REBAIXAR_ONCOLOGICO, TextosAgente.REBAIXAR_ONCOLOGICO));
        }
        if (casa(FURAR_FILA, t)) return Optional.of(new Recusa(Tipo.FURAR_FILA, TextosAgente.FURAR_FILA));
        return Optional.empty();
    }

    private static boolean casa(List<Pattern> padroes, String texto) {
        return padroes.stream().anyMatch(p -> p.matcher(texto).find());
    }
}
