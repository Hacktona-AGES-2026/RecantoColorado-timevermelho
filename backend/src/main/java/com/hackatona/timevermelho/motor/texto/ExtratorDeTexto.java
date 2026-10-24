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
        adicionar(TipoSinal.GRAVIDADE_CLINICA, r.gravidadeClinica(), false);
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

    /** Como a menção aparece na frase. Só a AFIRMATIVA descreve algo que está acontecendo. */
    public enum Qualificador { AFIRMATIVO, NEGADO, HIPOTETICO, HISTORICO }

    // na mesma frase, até 3 palavras antes do termo: "risco de estrangulamento", "suspeita de sepse"
    private static final Pattern HIPOTESE_ANTES = Pattern.compile(
            "(?:^|\\s)(?:risco de|suspeita de|possivel|possibilidade de|pode evoluir para|podendo evoluir para|"
                    + "a descartar|descartar|investigar|se houver|caso haja|orientad[oa] sobre)\\s+(?:\\S+\\s+){0,3}$");
    // histórico antes ("história de sepse") ou logo depois ("sepse em 2019", "sepse há 5 anos", "cirurgia prévia")
    private static final Pattern HISTORICO_ANTES = Pattern.compile(
            "(?:^|\\s)(?:historia de|historico de|antecedentes? de|passado de|no passado,?)\\s+(?:\\S+\\s+){0,3}$");
    private static final Pattern HISTORICO_DEPOIS = Pattern.compile(
            "^\\S*\\s*(?:\\S+\\s+){0,3}?(?:previ[oa]s?\\b|ha \\d+ anos|anos atras|em (?:19|20)\\d\\d\\b|no passado|antig[oa])");

    /**
     * Lê a descrição de um evento registrado depois da importação. Texto livre nunca muda a classe sozinho:
     * só uma menção AFIRMATIVA de alarme grave vira "possível agravamento grave, a confirmar", que pede
     * revisão clínica urgente. Menções negadas, hipotéticas ou históricas são ignoradas.
     */
    public List<Sinal> extrairDeEvento(String descricao, String fonte) {
        Map<String, Sinal> achados = new LinkedHashMap<>();
        procurar(descricao, fonte, false, achados, true);
        List<Sinal> sinais = new ArrayList<>();
        for (Sinal s : achados.values()) {
            if (s.tipo() == TipoSinal.ALARME_GRAVE) {
                sinais.add(Sinal.de(TipoSinal.ALARME_A_CONFIRMAR, s.descricao(), s.evidencia(), s.fonte()));
            }
        }
        return sinais;
    }

    private void procurar(String original, String fonte, boolean ehObservacao, Map<String, Sinal> achados) {
        procurar(original, fonte, ehObservacao, achados, false);
    }

    /**
     * @param soAfirmativas true (eventos novos): ignora menções negadas, hipotéticas e históricas.
     *                      false (texto do dataset de referência): ignora só as negadas, como sempre, e marca as
     *                      hipotéticas e históricas na descrição, para não parecerem diagnóstico confirmado.
     */
    private void procurar(String original, String fonte, boolean ehObservacao, Map<String, Sinal> achados,
                          boolean soAfirmativas) {
        if (original == null || original.isBlank()) return;
        String texto = normalizar(original);
        for (Regra regra : regras) {
            if (regra.soObservacoes() && !ehObservacao) continue;
            Matcher m = regra.padrao().matcher(texto);
            Sinal primeiroQualificado = null;
            Sinal afirmativo = null;
            while (m.find()) {
                Qualificador q = qualificador(texto, m.start(), m.end());
                if (q == Qualificador.NEGADO) continue;
                String evidencia = trecho(original, m.start(), m.end());
                if (q == Qualificador.AFIRMATIVO) {
                    afirmativo = Sinal.de(regra.tipo(), capitalizar(regra.rotulo()), evidencia, fonte);
                    break;
                }
                if (primeiroQualificado == null && !soAfirmativas) {
                    String marca = q == Qualificador.HIPOTETICO ? " (menção hipotética)" : " (menção a histórico)";
                    primeiroQualificado = Sinal.de(regra.tipo(), capitalizar(regra.rotulo()) + marca, evidencia, fonte);
                }
            }
            Sinal escolhido = afirmativo != null ? afirmativo : primeiroQualificado;
            if (escolhido != null) achados.putIfAbsent(regra.tipo() + "|" + regra.rotulo(), escolhido);
        }
    }

    /** Classifica a menção pelo que vem antes (na mesma frase) e logo depois do termo. */
    public Qualificador qualificador(String texto, int inicio, int fim) {
        int corte = Math.max(Math.max(texto.lastIndexOf('.', inicio - 1), texto.lastIndexOf(';', inicio - 1)),
                Math.max(texto.lastIndexOf(',', inicio - 1), texto.lastIndexOf('|', inicio - 1)));
        String antes = texto.substring(corte + 1, inicio);
        if (negacao.matcher(antes).find()) return Qualificador.NEGADO;
        if (HIPOTESE_ANTES.matcher(antes).find()) return Qualificador.HIPOTETICO;
        if (HISTORICO_ANTES.matcher(antes).find()) return Qualificador.HISTORICO;
        int fimFrase = texto.length();
        for (char ch : new char[]{'.', ';', ',', '|'}) {
            int i = texto.indexOf(ch, fim);
            if (i >= 0 && i < fimFrase) fimFrase = i;
        }
        String depois = texto.substring(fim, Math.min(fimFrase, fim + 40));
        if (HISTORICO_DEPOIS.matcher(depois).find()) return Qualificador.HISTORICO;
        return Qualificador.AFIRMATIVO;
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
