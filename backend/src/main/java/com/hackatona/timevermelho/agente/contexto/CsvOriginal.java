package com.hackatona.timevermelho.agente.contexto;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Acesso SOMENTE DE LEITURA ao CSV original da fila ({@code fila_cirurgica.csv}), do jeito que veio do desafio.
 * O agente usa para ligar o ID do paciente às solicitações dele e para achar as duplicadas, que a importação mescla
 * na solicitação principal e tira da fila (sem isto, perguntar por uma duplicada dava "não existe").
 *
 * <p>Nunca grava: o arquivo é aberto só com {@link StandardOpenOption#READ}, lido uma vez e guardado em memória como
 * lista imutável. O caminho é fixo (pasta do dataset + nome do arquivo), sem nada vindo da conversa. Um teste de guarda
 * confere que esta classe não tem como escrever, e outro que o arquivo sai intacto depois da leitura.
 */
@Component
public class CsvOriginal {

    private static final Logger log = LoggerFactory.getLogger(CsvOriginal.class);
    static final String ARQUIVO = "fila_cirurgica.csv";

    /** Uma linha do CSV, só com o que liga as solicitações ao paciente. */
    public record Registro(String solicitacaoId, String pacienteId, String procedimentoId, LocalDate dataEntrada) {
    }

    private final Path caminho;
    private volatile List<Registro> registros;
    private volatile Map<String, String> principalDe;
    private volatile Map<String, List<Registro>> porPaciente;

    public CsvOriginal(@Value("${app.dataset.dir}") String pasta) {
        this.caminho = Path.of(pasta).resolve(ARQUIVO).normalize();
    }

    Path caminho() {
        return caminho;
    }

    /** Todas as linhas do CSV original, duplicadas incluídas. Vazio se o arquivo não puder ser lido. */
    public List<Registro> registros() {
        carregar();
        return registros;
    }

    /**
     * A solicitação que ficou na fila no lugar desta: ela mesma, ou a mais antiga do grupo duplicado (mesmo paciente +
     * mesmo procedimento, a mesma regra da importação). {@code null} se o número não existir no CSV.
     */
    public String principal(String solicitacaoId) {
        carregar();
        return solicitacaoId == null ? null : principalDe.get(solicitacaoId);
    }

    /** As solicitações do paciente no CSV original, duplicadas incluídas. */
    public List<Registro> doPaciente(String pacienteId) {
        carregar();
        return pacienteId == null ? List.of() : porPaciente.getOrDefault(pacienteId, List.of());
    }

    /** Quantas solicitações do CSV foram mescladas nesta (as duplicadas dela, sem contar ela mesma). */
    public int duplicadasDe(String solicitacaoId) {
        carregar();
        if (solicitacaoId == null) return 0;
        return (int) principalDe.entrySet().stream()
                .filter(e -> solicitacaoId.equals(e.getValue()) && !solicitacaoId.equals(e.getKey())).count();
    }

    private void carregar() {
        if (registros != null) return;
        synchronized (this) {
            if (registros != null) return;
            List<Registro> lidos = ler();
            Map<String, List<Registro>> grupos = new HashMap<>();
            Map<String, List<Registro>> doPaciente = new HashMap<>();
            for (Registro r : lidos) {
                grupos.computeIfAbsent(r.pacienteId() + "|" + r.procedimentoId(), k -> new ArrayList<>()).add(r);
                doPaciente.computeIfAbsent(r.pacienteId(), k -> new ArrayList<>()).add(r);
            }
            Map<String, String> principais = new HashMap<>();
            for (List<Registro> g : grupos.values()) {
                g.sort(Comparator.comparing(Registro::dataEntrada, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Registro::solicitacaoId));
                String principal = g.getFirst().solicitacaoId();
                g.forEach(r -> principais.put(r.solicitacaoId(), principal));
            }
            Map<String, List<Registro>> imutavel = new HashMap<>();
            doPaciente.forEach((k, v) -> imutavel.put(k, List.copyOf(v)));
            principalDe = Map.copyOf(principais);
            porPaciente = Map.copyOf(imutavel);
            registros = List.copyOf(lidos);
        }
    }

    private List<Registro> ler() {
        List<Registro> lista = new ArrayList<>();
        CSVFormat formato = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).get();
        try (Reader in = new InputStreamReader(Files.newInputStream(caminho, StandardOpenOption.READ), StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(in, formato)) {
            for (CSVRecord r : parser) {
                String sol = r.get("solicitacao_id");
                String pac = r.get("paciente_id");
                if (sol == null || sol.isBlank() || pac == null || pac.isBlank()) continue;
                lista.add(new Registro(sol, pac, r.get("procedimento_id"), data(r.get("data_entrada_fila"))));
            }
        } catch (IOException | RuntimeException e) {
            // falha fecha: sem o CSV, o agente segue só com a fila calculada
            log.warn("agente: não consegui ler o CSV original {} ({})", caminho, e.getClass().getSimpleName());
            return List.of();
        }
        return lista;
    }

    private static LocalDate data(String texto) {
        try {
            return texto == null || texto.isBlank() ? null : LocalDate.parse(texto.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
