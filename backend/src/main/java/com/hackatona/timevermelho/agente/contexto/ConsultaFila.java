package com.hackatona.timevermelho.agente.contexto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.hackatona.timevermelho.motor.texto.ExtratorDeTexto;

import tools.jackson.databind.JsonNode;

/**
 * Os filtros de uma consulta à fila, escolhidos pela IA (planejador) e SEMPRE validados aqui antes de rodar:
 * nomes só valem se existirem no catálogo da fila, números têm limites e qualquer campo estranho é ignorado.
 * A IA não escreve consulta nenhuma: ela só preenche estes campos; quem consulta é o código.
 *
 * @param solicitacoes números de solicitação pedidos (formato SOL-AAAA-NNNNN), para ver um paciente específico
 * @param nomes trechos de nome de paciente (cada palavra precisa aparecer no nome), para ver um paciente específico
 * @param naoEncontrados termos pedidos que não existem na fila (para a resposta dizer isso, em vez de inventar)
 */
public record ConsultaFila(List<String> procedimentos, List<String> especialidades, List<String> classes,
                           String cidPrefixo, Boolean oncologico, List<String> municipios, String sexo,
                           Integer idadeMin, Integer idadeMax, Status status, Boolean acimaDoPrazo,
                           Boolean comReavaliacao, boolean listar, Ordem ordem, int limite,
                           List<String> solicitacoes, List<String> nomes, List<String> naoEncontrados) {

    public enum Status { ATIVA, SAIDA_SUGERIDA, SAIDA_CONFIRMADA, TODOS }

    public enum Ordem { POSICAO, ESPERA, SCORE }

    public static final int LIMITE_PADRAO = 200;
    public static final int LIMITE_MAXIMO = 1000;
    private static final Set<String> CLASSES = Set.of("A1", "A2", "B", "C", "D");
    private static final Pattern CID = Pattern.compile("[A-Z]\\d{0,2}(\\.\\d{0,2})?");
    /** "SOL-2026-00489", "sol 2026 489", "SOL202600489": o mesmo número. */
    private static final Pattern SOLICITACAO = Pattern.compile("(?i)^\\s*sol[- ]?(\\d{4})[- ]?(\\d{1,6})\\s*$");
    private static final int MAX_NOMES = 5;

    /** Sem filtro: a fila ativa inteira, só os números. */
    public static ConsultaFila todaFila() {
        return new ConsultaFila(List.of(), List.of(), List.of(), null, null, List.of(), null, null, null, Status.ATIVA,
                null, null, false, Ordem.POSICAO, LIMITE_PADRAO, List.of(), List.of(), List.of());
    }

    /** Todos os pacientes ativos de um procedimento (já validado), com a lista. Usado nos relatórios. */
    public static ConsultaFila doProcedimento(String procedimento) {
        return new ConsultaFila(List.of(procedimento), List.of(), List.of(), null, null, List.of(), null, null, null,
                Status.ATIVA, null, null, true, Ordem.POSICAO, LIMITE_MAXIMO, List.of(), List.of(), List.of());
    }

    /** Lê os filtros devolvidos pelo planejador, descartando tudo o que não bate com o catálogo. */
    public static ConsultaFila deJson(JsonNode f, boolean listar, LeituraAplicacao.Catalogo catalogo) {
        List<String> faltando = new ArrayList<>();
        List<String> procedimentos = casar(textos(f.path("procedimentos")), catalogo.procedimentos(), faltando);
        List<String> especialidades = casar(textos(f.path("especialidades")), catalogo.especialidades(), faltando);
        List<String> municipios = casar(textos(f.path("municipios")), catalogo.municipios(), faltando);
        List<String> classes = textos(f.path("classes")).stream().map(c -> c.strip().toUpperCase(Locale.ROOT))
                .filter(CLASSES::contains).distinct().toList();

        String cid = f.path("cidPrefixo").asString("").strip().toUpperCase(Locale.ROOT);
        if (!CID.matcher(cid).matches()) cid = null;
        String sexo = f.path("sexo").asString("").strip().toUpperCase(Locale.ROOT);
        if (!sexo.equals("F") && !sexo.equals("M")) sexo = null;

        Status status = enumOu(Status.class, f.path("status").asString(""), Status.ATIVA);
        Ordem ordem = enumOu(Ordem.class, f.path("ordem").asString(""), Ordem.POSICAO);
        int limite = f.path("limite").isNumber() ? f.path("limite").asInt() : LIMITE_PADRAO;
        limite = Math.max(1, Math.min(LIMITE_MAXIMO, limite));

        List<String> solicitacoes = textos(f.path("solicitacoes")).stream().map(ConsultaFila::solicitacao)
                .filter(s -> s != null).distinct().toList();
        List<String> nomes = textos(f.path("nomes")).stream()
                .map(n -> ExtratorDeTexto.normalizar(n).replaceAll("[^\\p{L} ]", " ").replaceAll("\\s+", " ").strip())
                .filter(n -> n.length() >= 3 && n.length() <= 80).distinct().limit(MAX_NOMES).toList();
        // pedido de um paciente específico: procura em todos os status, para achar também quem já saiu da fila
        if ((!solicitacoes.isEmpty() || !nomes.isEmpty()) && f.path("status").asString("").isBlank()) status = Status.TODOS;

        return new ConsultaFila(procedimentos, especialidades, classes, cid, booleano(f.path("oncologico")),
                municipios, sexo, inteiro(f.path("idadeMin"), 0, 120), inteiro(f.path("idadeMax"), 0, 120), status,
                booleano(f.path("acimaDoPrazo")), booleano(f.path("comReavaliacao")), listar, ordem, limite,
                solicitacoes, nomes, List.copyOf(faltando));
    }

    /** Pedido de um paciente específico (por nome ou número de solicitação)? */
    public boolean porPaciente() {
        return !solicitacoes.isEmpty() || !nomes.isEmpty();
    }

    /** O que foi filtrado, em português, para o título da tabela (mostra o nome e o número pedidos). */
    public String descricao() {
        return descrever(true);
    }

    /** O mesmo, para o bloco que vai à IA: o nome e o número pedidos não saem da aplicação por aqui. */
    public String descricaoParaIA() {
        return descrever(false);
    }

    private String descrever(boolean comIdentificacao) {
        List<String> partes = new ArrayList<>();
        if (!solicitacoes.isEmpty()) {
            partes.add(comIdentificacao ? "solicitação: " + String.join(", ", solicitacoes)
                    : solicitacoes.size() + " número(s) de solicitação informado(s)");
        }
        if (!nomes.isEmpty()) {
            partes.add(comIdentificacao ? "nome contendo: " + String.join(", ", nomes) : nomes.size() + " nome(s) informado(s)");
        }
        if (!procedimentos.isEmpty()) partes.add("procedimento: " + String.join(", ", procedimentos));
        if (!especialidades.isEmpty()) partes.add("especialidade: " + String.join(", ", especialidades));
        if (!classes.isEmpty()) partes.add("classe: " + String.join(", ", classes));
        if (cidPrefixo != null) partes.add("CID começando com " + cidPrefixo);
        if (oncologico != null) partes.add(oncologico ? "só oncológicos" : "sem os oncológicos");
        if (!municipios.isEmpty()) partes.add("município: " + String.join(", ", municipios));
        if (sexo != null) partes.add("sexo: " + sexo);
        if (idadeMin != null) partes.add("idade a partir de " + idadeMin);
        if (idadeMax != null) partes.add("idade até " + idadeMax);
        if (acimaDoPrazo != null) partes.add(acimaDoPrazo ? "acima do prazo da classe" : "dentro do prazo da classe");
        if (comReavaliacao != null) partes.add(comReavaliacao ? "com motivo de reavaliação" : "sem motivo de reavaliação");
        partes.add(switch (status) {
            case ATIVA -> "fila ativa";
            case SAIDA_SUGERIDA -> "saída sugerida aguardando o regulador";
            case SAIDA_CONFIRMADA -> "fora da fila (saída confirmada)";
            case TODOS -> "todos os status";
        });
        return String.join("; ", partes);
    }

    // ---------- validação ----------

    /** Cada termo pedido vira o nome exato do catálogo (sem acento e sem caixa); o que não existir vai para {@code faltando}. */
    static List<String> casar(List<String> pedidos, List<String> catalogo, List<String> faltando) {
        List<String> achados = new ArrayList<>();
        for (String pedido : pedidos) {
            String p = ExtratorDeTexto.normalizar(pedido).strip();
            if (p.isEmpty() || p.length() > 80) continue;
            String exato = catalogo.stream().filter(c -> ExtratorDeTexto.normalizar(c).equals(p)).findFirst().orElse(null);
            // pedaço curto demais ("a", "de") pegaria quase tudo: só vale o nome exato
            List<String> encontrados = exato != null ? List.of(exato) : p.length() < 4 ? List.of()
                    : catalogo.stream().filter(c -> ExtratorDeTexto.normalizar(c).contains(p)).toList();
            if (encontrados.isEmpty()) faltando.add(ContextoDados.rotulo(pedido));
            encontrados.stream().filter(e -> !achados.contains(e)).forEach(achados::add);
        }
        return List.copyOf(achados);
    }

    /** Normaliza um número de solicitação para SOL-AAAA-NNNNN; {@code null} se não tiver esse formato. */
    static String solicitacao(String bruto) {
        var m = SOLICITACAO.matcher(bruto == null ? "" : bruto);
        if (!m.matches()) return null;
        return "SOL-" + m.group(1) + "-" + String.format("%05d", Integer.parseInt(m.group(2)));
    }

    private static List<String> textos(JsonNode no) {
        List<String> lista = new ArrayList<>();
        if (no.isArray()) {
            for (JsonNode n : no) if (n.isString()) lista.add(n.asString());
        } else if (no.isString() && !no.asString().isBlank()) {
            lista.add(no.asString());
        }
        return lista.size() > 30 ? lista.subList(0, 30) : lista;
    }

    private static Boolean booleano(JsonNode no) {
        return no.isBoolean() ? no.asBoolean() : null;
    }

    private static Integer inteiro(JsonNode no, int min, int max) {
        if (!no.isNumber()) return null;
        int v = no.asInt();
        return v < min || v > max ? null : v;
    }

    private static <E extends Enum<E>> E enumOu(Class<E> tipo, String valor, E padrao) {
        try {
            return valor.isBlank() ? padrao : Enum.valueOf(tipo, valor.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return padrao;
        }
    }
}
