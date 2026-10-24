package com.hackatona.timevermelho.agente;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.hackatona.timevermelho.agente.contexto.ContextoDados;
import com.hackatona.timevermelho.agente.contexto.LeituraAplicacao;
import com.hackatona.timevermelho.agente.contexto.LeituraFalsa;

/**
 * Testes de GUARDA (T36 e T39): leem o código do agente e falham se alguém, um dia, der ao agente o poder de
 * gravar na aplicação, rodar comandos, falar com a rede além do provedor de IA, ou enxergar dados de paciente.
 * Se um destes testes quebrar, a mudança que o quebrou precisa de revisão de segurança (docs/AGENTE-IA.md).
 */
class AgenteGuardaTest {

    static final Path RAIZ = Path.of("src/main/java/com/hackatona/timevermelho/agente");

    private static List<Path> fontes() throws IOException {
        try (Stream<Path> s = Files.walk(RAIZ)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static String texto(Path p) throws IOException {
        return Files.readString(p);
    }

    private static boolean ehArquivo(Path p, String nome) {
        return p.getFileName().toString().equals(nome);
    }

    @Test
    void existemFontesParaConferir() throws IOException {
        assertThat(fontes()).hasSizeGreaterThan(20);
    }

    // ---------- o agente não escreve na aplicação ----------

    @Test
    void agenteNaoUsaNenhumServicoQueMudaAFilaNemOsRepositoriosOficiais() throws IOException {
        List<String> proibidos = List.of("DecisaoService", "SimulacaoService", "DemoService", "ConsultaService", "ExportService",
                "registrarEvento(", ".ajustar(", ".resetar(", ".cenario(", ".recalcular(",
                "AjusteReguladorRepository", "EventoClinicoRepository", "SolicitacaoRepository", "AlertaRepository",
                "ProcedimentoRepository", "AjusteRegulador", "EventoClinico ", "import com.hackatona.timevermelho.dominio");
        List<String> achados = new ArrayList<>();
        for (Path p : fontes()) {
            String t = texto(p);
            for (String proibido : proibidos) {
                if (t.contains(proibido)) achados.add(p.getFileName() + " usa " + proibido);
            }
        }
        assertThat(achados).as("o agente só conversa: não pode tocar em eventos, ajustes, alertas nem na fila").isEmpty();
    }

    @Test
    void soUmaClasseDoAgenteConheceOsServicosDeLeituraDaAplicacao() throws IOException {
        List<String> achados = new ArrayList<>();
        for (Path p : fontes()) {
            for (String linha : texto(p).lines().filter(l -> l.startsWith("import com.hackatona.timevermelho.servico.")).toList()) {
                boolean permitido = ehArquivo(p, "LeituraAplicacaoSpring.java") && Set.of("FilaService", "IndicadoresService",
                        "AlertaService").contains(linha.substring(linha.lastIndexOf('.') + 1).replace(";", ""))
                        || linha.endsWith(".Erros;");
                if (!permitido) achados.add(p.getFileName() + ": " + linha);
            }
        }
        assertThat(achados).isEmpty();
    }

    @Test
    void osUnicosRepositoriosDoAgenteSaoOsDasSuasProprias_tabelas() throws IOException {
        List<String> repos = new ArrayList<>();
        for (Path p : fontes()) {
            if (p.getFileName().toString().endsWith("Repository.java")) repos.add(p.getFileName().toString());
        }
        assertThat(repos).containsExactlyInAnyOrder("CategorizacaoRepository.java", "RelatorioRepository.java");
    }

    @Test
    void agenteNaoRodaComandosNemMexeEmArquivosNemAbreOutrasConexoes() throws IOException {
        List<String> proibidos = List.of("ProcessBuilder", "Runtime.getRuntime", "Files.write", "Files.delete", "Files.move",
                "Files.copy", "FileWriter", "FileOutputStream", "EntityManager", "JdbcTemplate", "createNativeQuery",
                "@Query", "Class.forName", "ScriptEngine", "URLClassLoader", "java.net.Socket", "new URL(");
        List<String> achados = new ArrayList<>();
        for (Path p : fontes()) {
            String t = texto(p);
            for (String proibido : proibidos) {
                if (t.contains(proibido)) achados.add(p.getFileName() + " usa " + proibido);
            }
        }
        assertThat(achados).isEmpty();
    }

    @Test
    void oUnicoArquivoQueOAgenteAbreEOCsvOriginalESoParaLeitura() throws IOException {
        List<String> abremArquivo = new ArrayList<>();
        for (Path p : fontes()) {
            String t = texto(p);
            if (t.contains("Files.") || t.contains("FileInputStream") || t.contains("FileReader")
                    || t.contains("RandomAccessFile") || t.contains("FileChannel")) abremArquivo.add(p.getFileName().toString());
        }
        assertThat(abremArquivo).containsExactly("CsvOriginal.java");

        String csv = texto(RAIZ.resolve("contexto/CsvOriginal.java"));
        assertThat(csv).contains("Files.newInputStream(caminho, StandardOpenOption.READ)")
                .contains("resolve(ARQUIVO)").contains("\"fila_cirurgica.csv\"");
        // nada que escreva, crie, apague ou mude o arquivo
        for (String proibido : List.of("Files.write", "Files.newOutputStream", "Files.newBufferedWriter", "OutputStream",
                "Writer", "StandardOpenOption.WRITE", "StandardOpenOption.APPEND", "StandardOpenOption.CREATE",
                "TRUNCATE_EXISTING", "Files.delete", "Files.move", "Files.copy", "setLastModified", "setAttribute",
                "CSVPrinter")) {
            assertThat(csv).as("CsvOriginal não pode usar " + proibido).doesNotContain(proibido);
        }
    }

    @Test
    void sohOProvedorFalaComARedeEOGuardaDeUrlEstaNoLugar() throws IOException {
        List<String> comRede = new ArrayList<>();
        for (Path p : fontes()) {
            if (texto(p).contains("HttpClient") || texto(p).contains("HttpRequest")) comRede.add(p.getFileName().toString());
        }
        assertThat(comRede).containsExactly("ProvedorOpenAiCompativel.java");
        assertThat(texto(RAIZ.resolve("AgenteConfig.java"))).contains("urlSegura(");
    }

    @Test
    void oModeloNaoRecebeFerramentasEntaoNaoTemComoPedirNadaAAplicacao() throws IOException {
        String provedor = texto(RAIZ.resolve("provedores/ProvedorOpenAiCompativel.java"));
        assertThat(provedor).doesNotContain("\"tools\"").doesNotContain("tool_choice").doesNotContain("\"functions\"")
                .doesNotContain("function_call").doesNotContain("tool_calls");
    }

    // ---------- a IA só enxerga agregados (linhas de paciente vão só para a tela) ----------

    @Test
    void aPortaDeLeituraSoTemMetodosDeConsultaEResultadosSemDadosDePaciente() {
        Set<String> prefixosDeLeitura = Set.of("resumo", "indicadores", "reavaliacoes", "especialidades", "catalogo",
                "consultar");
        for (Method m : LeituraAplicacao.class.getDeclaredMethods()) {
            assertThat(prefixosDeLeitura.stream().anyMatch(m.getName()::startsWith)).as("método " + m.getName()).isTrue();
            assertThat(m.getName()).doesNotStartWith("registrar").doesNotStartWith("salvar").doesNotStartWith("ajustar")
                    .doesNotStartWith("apagar").doesNotStartWith("resetar").doesNotStartWith("alterar");
        }
        Set<String> camposDePaciente = Set.of("pacienteId", "solicitacaoId", "nomePaciente", "cid", "cidFormatado",
                "cidDescricao", "idade", "sexo", "municipio", "comorbidades", "justificativa", "observacoes", "dataEntrada",
                "itens", "recentes");
        for (Class<?> tipo : LeituraAplicacao.class.getDeclaredClasses()) {
            // a linha da tabela é o único lugar com dado de paciente: vai do código para a tela, nunca para a IA
            // (ChatServiceConsultaTest confere que o nome não aparece em nada enviado ao provedor)
            if (tipo == LeituraAplicacao.LinhaPaciente.class) continue;
            for (RecordComponent c : tipo.getRecordComponents()) {
                assertThat(camposDePaciente).as(tipo.getSimpleName() + "." + c.getName()).doesNotContain(c.getName());
            }
        }
    }

    @Test
    void aListaDeConsultasDoChatEFechadaEFixa() {
        var contexto = ContextoDados.padrao(new LeituraFalsa());
        String tudo = contexto.montar("fila classe indicador antes alerta reavaliação saída");
        assertThat(tudo).contains("### Fonte: resumo_fila").contains("### Fonte: indicadores_antes_depois")
                .contains("### Fonte: reavaliacoes_pendentes");
        assertThat(tudo.lines().filter(l -> l.startsWith("### Fonte:")).count()).isEqualTo(3);
    }

    @Test
    void asEntidadesDoAgenteTemTabelasProprias() throws IOException {
        Map<String, String> tabelas = Map.of("categorizacao/CategorizacaoSugerida.java", "agente_categorizacao",
                "relatorios/RelatorioIA.java", "agente_relatorio");
        for (var e : tabelas.entrySet()) {
            assertThat(texto(RAIZ.resolve(e.getKey()))).contains("@Table(name = \"" + e.getValue() + "\")");
        }
    }
}
