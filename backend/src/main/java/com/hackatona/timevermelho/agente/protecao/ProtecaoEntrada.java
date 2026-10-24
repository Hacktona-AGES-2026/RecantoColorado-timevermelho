package com.hackatona.timevermelho.agente.protecao;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import com.hackatona.timevermelho.motor.texto.ExtratorDeTexto;

/**
 * Primeira barreira, sem chamar IA nenhuma: reconhece emergência de saúde e tentativas óbvias de burlar as
 * regras. Roda antes da moderação e do filtro de escopo, então funciona mesmo com todos os provedores fora do ar.
 * Os padrões são propositalmente simples; o filtro de escopo (modelo) cobre o resto.
 */
public class ProtecaoEntrada {

    public enum Tipo { RISCO_A_SAUDE, TENTATIVA_DE_BURLAR }

    public record Recusa(Tipo tipo, String texto) {
    }

    // Texto normalizado: minúsculas e sem acento.
    private static final List<Pattern> RISCO = compilar(
            "\\bquero (me )?(matar|morrer)\\b", "\\bvou me matar\\b", "\\bsuicid", "\\btirar (a )?(minha )?vida\\b",
            "\\bme machucar\\b", "\\bme cortar\\b", "\\bacabar com (a )?(minha )?vida\\b",
            "\\bdor (forte |intensa )?no peito\\b", "\\bnao consigo respirar\\b", "\\bfalta de ar (forte|intensa)\\b",
            "\\b(estou|to|esta|estao) (tendo|com) (um |uma )?(infarto|avc|derrame|convuls)",
            "\\b(desmaiei|desmaiou|desmaiando)\\b", "\\bconvulsionando\\b",
            "\\b(sangrando|sangramento) (muito|intenso|forte)\\b", "\\b(foi|teve|tive) (uma )?overdose\\b",
            "\\btomei (muito|varios|um monte de) (remedio|comprimido|medicamento)", "\\bengoli(u|i) (veneno|produto)");

    private static final List<Pattern> BURLAR = compilar(
            "\\b(ignore|ignora|desconsidere|esqueca|esqueça) (todas |todos |tudo |as |os |suas |seus |isso )*(instrucoes|regras|ordens|orientacoes|comandos)",
            "\\bsystem prompt\\b", "\\bprompt do sistema\\b", "\\bprompt inicial\\b",
            "\\b(mostre|revele|repita|exiba|imprima|traduza|resuma) (suas |seu |o |as |todo o |tudo |o texto )*(instrucoes|prompt|regras internas|configuracao)",
            "\\brepita (o )?(texto|tudo) (acima|anterior)", "\\bvoce (agora )?(e|sera|vai ser) (um |uma |o |a )?(outro|outra|dan|assistente sem)",
            "\\b(modo|mode) (desenvolvedor|developer|dan|deus)\\b", "\\bjailbreak\\b",
            "\\bdeveloper message\\b", "\\bmensagem do desenvolvedor\\b",
            "\\ba partir de agora (a |as |sua |suas )?(regra|regras|voce)");

    private static List<Pattern> compilar(String... padroes) {
        return java.util.Arrays.stream(padroes).map(p -> Pattern.compile(p)).toList();
    }

    public Optional<Recusa> avaliar(String mensagem) {
        String t = ExtratorDeTexto.normalizar(mensagem);
        for (Pattern p : RISCO) {
            if (p.matcher(t).find()) return Optional.of(new Recusa(Tipo.RISCO_A_SAUDE, TextosAgente.EMERGENCIA));
        }
        for (Pattern p : BURLAR) {
            if (p.matcher(t).find()) {
                return Optional.of(new Recusa(Tipo.TENTATIVA_DE_BURLAR, TextosAgente.TENTATIVA_DE_BURLAR));
            }
        }
        return Optional.empty();
    }
}
