package com.hackatona.timevermelho.agente;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Prompts das tarefas que não são o chat (categorização e relatórios), lidos de arquivos versionados em
 * {@code resources/agente/}. Mesmas regras do chat: só mudam por Pull Request, sem endpoint de edição, com versão (hash).
 * Se faltarem ou estiverem vazios, a tarefa recusa em vez de rodar sem instruções.
 */
public final class PromptsTarefas {

    private final String categorizacao;
    private final String relatorio;
    private final String versao;
    private final String motivoIndisponivel;

    private PromptsTarefas(String categorizacao, String relatorio, String versao, String motivo) {
        this.categorizacao = categorizacao;
        this.relatorio = relatorio;
        this.versao = versao;
        this.motivoIndisponivel = motivo;
    }

    public static PromptsTarefas carregar() {
        return montar(ler("agente/categorizacao.md"), ler("agente/relatorio.md"));
    }

    public static PromptsTarefas montar(String categorizacao, String relatorio) {
        String problema = null;
        if (categorizacao == null || categorizacao.isBlank()) problema = "categorizacao.md ausente ou vazio";
        else if (relatorio == null || relatorio.isBlank()) problema = "relatorio.md ausente ou vazio";
        else if (categorizacao.indexOf('\u0000') >= 0 || relatorio.indexOf('\u0000') >= 0) problema = "prompt com texto inválido";
        String c = categorizacao == null ? "" : categorizacao.strip();
        String r = relatorio == null ? "" : relatorio.strip();
        return new PromptsTarefas(c, r, hash(c + "\u0000" + r), problema);
    }

    private static String ler(String caminho) {
        try (InputStream in = PromptsTarefas.class.getClassLoader().getResourceAsStream(caminho)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static String hash(String texto) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean pronto() {
        return motivoIndisponivel == null;
    }

    public String motivoIndisponivel() {
        return motivoIndisponivel;
    }

    public String categorizacao() {
        return categorizacao;
    }

    public String relatorio() {
        return relatorio;
    }

    public String versao() {
        return versao;
    }
}
