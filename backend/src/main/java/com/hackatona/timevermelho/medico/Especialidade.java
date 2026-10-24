package com.hackatona.timevermelho.medico;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hackatona.timevermelho.importacao.Normalizacao;
import com.hackatona.timevermelho.motor.Caso;

/**
 * Uma especialidade médica e os pacientes que ela atende (definida em especialidades.json).
 * O paciente entra na lista se bater em qualquer critério; idadeMaxima, quando existe, é obrigatória.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Especialidade(
        String codigo,
        String nome,
        String descricao,
        List<String> especialidadesCirurgicas,
        List<String> cidFaixas,
        List<String> cidSecundarioFaixas,
        List<String> termosComorbidade,
        Boolean oncologico,
        Integer idadeMaxima) {

    public Especialidade {
        especialidadesCirurgicas = especialidadesCirurgicas == null ? List.of() : List.copyOf(especialidadesCirurgicas);
        cidFaixas = cidFaixas == null ? List.of() : List.copyOf(cidFaixas);
        cidSecundarioFaixas = cidSecundarioFaixas == null ? List.of() : List.copyOf(cidSecundarioFaixas);
        termosComorbidade = termosComorbidade == null ? List.of() : List.copyOf(termosComorbidade);
    }

    private boolean temCriterioClinico() {
        return !especialidadesCirurgicas.isEmpty() || !cidFaixas.isEmpty() || !cidSecundarioFaixas.isEmpty()
                || !termosComorbidade.isEmpty() || Boolean.TRUE.equals(oncologico);
    }

    /**
     * Como o paciente se relaciona com esta especialidade.
     * @param cirurgica o procedimento cirúrgico pedido é desta especialidade (paciente atribuído à lista principal)
     * @param motivos   evidências encontradas; vazio = paciente não tem relação com a especialidade
     */
    public record Correspondencia(boolean cirurgica, List<String> motivos) {
        public static final Correspondencia NENHUMA = new Correspondencia(false, List.of());

        public boolean existe() {
            return !motivos.isEmpty();
        }
    }

    /** O procedimento pedido pertence a esta especialidade (coluna especialidade do pedido = do procedimento). */
    public boolean procedimentoDaEspecialidade(Caso c) {
        return c.especialidade() != null
                && especialidadesCirurgicas.stream().anyMatch(e -> e.equalsIgnoreCase(c.especialidade()));
    }

    /**
     * Lista principal: o procedimento cirúrgico é desta especialidade.
     * Possível avaliação: sem procedimento da especialidade, mas com CID, comorbidade, câncer ou idade relacionados.
     * @param pacienteOncologico CID C ou biópsia positiva (o mesmo critério da fila)
     */
    public Correspondencia avaliar(Caso c, boolean pacienteOncologico) {
        List<String> m = motivos(c, pacienteOncologico);
        if (m.isEmpty()) return Correspondencia.NENHUMA;
        return new Correspondencia(procedimentoDaEspecialidade(c), m);
    }

    /** Todas as evidências que ligam o paciente a esta especialidade. Lista vazia = nenhuma. */
    public List<String> motivos(Caso c, boolean pacienteOncologico) {
        if (idadeMaxima != null && (c.idade() == null || c.idade() > idadeMaxima)) return List.of();
        List<String> m = new ArrayList<>();
        if (idadeMaxima != null) m.add("Idade " + c.idade() + " anos (até " + idadeMaxima + ")");
        if (!temCriterioClinico()) return m;
        int soIdade = m.size();

        if (procedimentoDaEspecialidade(c)) {
            m.add("Procedimento de " + c.especialidade() + (c.procedimento() == null ? "" : ": " + c.procedimento()));
        }
        if (naFaixa(c.cid(), cidFaixas)) {
            m.add("CID principal " + Normalizacao.cidFormatado(c.cid())
                    + (c.cidDescricao() == null ? "" : " — " + c.cidDescricao()));
        }
        if (naFaixa(c.cidSecundario(), cidSecundarioFaixas)) {
            m.add("CID secundário " + Normalizacao.cidFormatado(c.cidSecundario()));
        }
        String termo = termoNasComorbidades(c.comorbidades());
        if (termo != null) m.add("Comorbidade: " + termo);
        if (Boolean.TRUE.equals(oncologico) && pacienteOncologico && !naFaixa(c.cid(), cidFaixas)) {
            m.add("Paciente oncológico (biópsia positiva)");
        }
        // com idade máxima, a idade sozinha não basta quando há critérios clínicos
        return m.size() == soIdade ? List.of() : m;
    }

    /** "I25" em "I20-I52": mesma letra e número de 2 dígitos dentro da faixa. */
    static boolean naFaixa(String cid, List<String> faixas) {
        if (cid == null || cid.length() < 3) return false;
        String c = cid.toUpperCase(Locale.ROOT).replace(".", "");
        Integer n = doisDigitos(c, 1);
        if (n == null) return false;
        for (String f : faixas) {
            String[] p = f.split("-");
            String ini = p[0].trim().toUpperCase(Locale.ROOT);
            String fim = (p.length > 1 ? p[1] : p[0]).trim().toUpperCase(Locale.ROOT);
            Integer a = doisDigitos(ini, 1), b = doisDigitos(fim, 1);
            if (a == null || b == null || ini.charAt(0) != fim.charAt(0)) continue;
            if (c.charAt(0) == ini.charAt(0) && n >= a && n <= b) return true;
        }
        return false;
    }

    private static Integer doisDigitos(String s, int inicio) {
        if (s.length() < inicio + 2) return null;
        try {
            return Integer.parseInt(s.substring(inicio, inicio + 2));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Primeiro termo que aparece como palavra (ou começo de palavra) nas comorbidades. */
    private String termoNasComorbidades(String comorbidades) {
        if (comorbidades == null || comorbidades.isBlank()) return null;
        String t = Normalizacao.semAcento(comorbidades).toLowerCase(Locale.ROOT);
        for (String termo : termosComorbidade) {
            String k = Normalizacao.semAcento(termo).toLowerCase(Locale.ROOT);
            if (Pattern.compile("\\b" + Pattern.quote(k)).matcher(t).find()) {
                // devolve o pedaço original (entre ";") onde o termo apareceu
                for (String parte : comorbidades.split(";")) {
                    if (Normalizacao.semAcento(parte).toLowerCase(Locale.ROOT).contains(k)) return parte.trim();
                }
                return termo;
            }
        }
        return null;
    }
}
