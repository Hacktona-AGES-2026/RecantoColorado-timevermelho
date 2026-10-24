package com.hackatona.timevermelho.motor.texto;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hackatona.timevermelho.motor.Sinal;
import com.hackatona.timevermelho.motor.TipoSinal;

/**
 * Lê a justificativa do médico e as observações da regulação e transforma em sinais,
 * sempre guardando o trecho do texto como evidência.
 * Ignora termos negados na mesma frase ("sem sinais de encarceramento", "nega icterícia").
 */
public class ExtratorDeTexto {

    private record Regra(TipoSinal tipo, Pattern padrao, String rotulo, boolean soObservacoes) {}

    private final List<Regra> regras = new ArrayList<>();
    private final Pattern negacao;

    public ExtratorDeTexto(RegrasClinicas r) {
        adicionar(TipoSinal.ALARME_GRAVE, r.alarmesGraves(), false);
        adicionar(TipoSinal.SUSPEITA_MALIGNIDADE, r.suspeitaMalignidade(), false);
        adicionar(TipoSinal.ALARME_FUNCIONAL, r.alarmesFuncionais(), false);
        adicionar(TipoSinal.PS_TEXTO, r.psNoTexto(), false);
        adicionar(TipoSinal.RELATA_PIORA, r.piora(), false);
        adicionar(TipoSinal.MELHORA_RELATADA, r.melhora(), true);
        adicionar(TipoSinal.SAIDA_DESISTENCIA, r.saidaDesistencia(), true);
        adicionar(TipoSinal.SAIDA_OUTRO_SERVICO, r.saidaOutroServico(), true);
        String negs = String.join("|", r.negacoes());
        // negação seguida de no máximo 3 palavras até o termo, dentro da mesma frase
        negacao = Pattern.compile("(?:^|\\s)(?:" + negs + ")\\s+(?:\\S+\\s+){0,3}$");
    }

    private void adicionar(TipoSinal tipo, List<RegrasClinicas.Termo> termos, boolean soObservacoes) {
        if (termos == null) return;
        for (var t : termos) {
            regras.add(new Regra(tipo, Pattern.compile(t.padrao()), t.rotulo(), soObservacoes));
        }
    }

    public List<Sinal> extrair(String justificativa, String observacoes) {
        // um sinal por tipo + rótulo, para não repetir o mesmo achado
        Map<String, Sinal> achados = new LinkedHashMap<>();
        procurar(justificativa, "justificativa_clinica", false, achados);
        procurar(observacoes, "observacoes", true, achados);
        return new ArrayList<>(achados.values());
    }

    private void procurar(String original, String fonte, boolean ehObservacao, Map<String, Sinal> achados) {
        if (original == null || original.isBlank()) return;
        String texto = normalizar(original);
        for (Regra regra : regras) {
            if (regra.soObservacoes() && !ehObservacao) continue;
            Matcher m = regra.padrao().matcher(texto);
            while (m.find()) {
                if (negado(texto, m.start())) continue;
                String chave = regra.tipo() + "|" + regra.rotulo();
                achados.putIfAbsent(chave, Sinal.de(regra.tipo(), capitalizar(regra.rotulo()),
                        trecho(original, m.start(), m.end()), fonte));
                break;
            }
        }
    }

    private boolean negado(String texto, int inicio) {
        int corte = Math.max(Math.max(texto.lastIndexOf('.', inicio - 1), texto.lastIndexOf(';', inicio - 1)),
                Math.max(texto.lastIndexOf(',', inicio - 1), texto.lastIndexOf('|', inicio - 1)));
        String frase = texto.substring(corte + 1, inicio);
        return negacao.matcher(frase).find();
    }

    /** Minúsculas e sem acento. Mantém o mesmo tamanho do texto original para os trechos baterem. */
    public static String normalizar(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            String semAcento = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            sb.append(semAcento.isEmpty() ? c : semAcento.charAt(0));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static String trecho(String original, int inicio, int fim) {
        int a = Math.max(0, inicio - 50);
        int b = Math.min(original.length(), fim + 50);
        String t = original.substring(a, b).trim();
        return (a > 0 ? "…" : "") + t + (b < original.length() ? "…" : "");
    }

    private static String capitalizar(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
