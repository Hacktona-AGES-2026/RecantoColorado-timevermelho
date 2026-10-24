package com.hackatona.timevermelho.agente;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * System Prompt, regras de negócio e prompt do filtro de escopo, lidos de arquivos versionados em
 * {@code resources/agente/}. Só mudam por Pull Request: não existe endpoint nem tela para editá-los.
 *
 * <p>Na inicialização os arquivos são validados (existem, não são vazios, tamanho razoável, texto válido). Se algo
 * estiver errado, o agente fica desligado com o motivo, nunca rodando sem regras. Cada resposta registra a
 * versão (hash) dos textos usados.
 */
public final class PromptsAgente {

    private static final Logger log = LoggerFactory.getLogger(PromptsAgente.class);
    private static final int MAX_BYTES = 100_000;

    private final String textoSistema;
    private final String promptFiltro;
    private final String versao;
    private final String motivoDesligado;

    private PromptsAgente(String textoSistema, String promptFiltro, String versao, String motivoDesligado) {
        this.textoSistema = textoSistema;
        this.promptFiltro = promptFiltro;
        this.versao = versao;
        this.motivoDesligado = motivoDesligado;
    }

    /** Lê os três arquivos do classpath. */
    public static PromptsAgente carregar(boolean exigirRegras) {
        return montar(ler("agente/system-prompt.md"), ler("agente/regras-negocio.md"), ler("agente/filtro-escopo.md"),
                exigirRegras);
    }

    /** Monta a partir dos textos (usado também nos testes). Texto {@code null} significa arquivo ausente. */
    public static PromptsAgente montar(String sistema, String regras, String filtro, boolean exigirRegras) {
        String problema = problema("system-prompt.md", sistema);
        if (problema == null) problema = problema("filtro-escopo.md", filtro);
        if (problema == null && regras != null) problema = problema("regras-negocio.md", regras, true);
        String semComentarios = regras == null ? "" : regras.replaceAll("(?s)<!--.*?-->", "").strip();
        if (problema == null && regras == null) problema = "regras-negocio.md não encontrado";
        if (problema == null && exigirRegras && semComentarios.isEmpty()) {
            problema = "regras de negócio ainda não preenchidas (regras-negocio.md vazio)";
        }

        String completo = (sistema == null ? "" : sistema.strip())
                + (semComentarios.isEmpty() ? "" : "\n\n" + semComentarios);
        String versao = hash((sistema == null ? "" : sistema) + "\u0000" + semComentarios + "\u0000"
                + (filtro == null ? "" : filtro));
        if (problema != null) log.warn("agente desligado: {}", problema);
        else log.info("agente: prompts carregados (versão {})", versao);
        return new PromptsAgente(completo, filtro == null ? "" : filtro.strip(), versao, problema);
    }

    private static String problema(String arquivo, String texto) {
        return problema(arquivo, texto, false);
    }

    private static String problema(String arquivo, String texto, boolean podeFicarVazio) {
        if (texto == null) return arquivo + " não encontrado";
        if (texto.indexOf('\u0000') >= 0 || texto.indexOf('�') >= 0) return arquivo + " com texto inválido";
        if (texto.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) return arquivo + " grande demais";
        if (!podeFicarVazio && texto.isBlank()) return arquivo + " vazio";
        return null;
    }

    private static String ler(String caminho) {
        try (InputStream in = PromptsAgente.class.getClassLoader().getResourceAsStream(caminho)) {
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
        return motivoDesligado == null;
    }

    public String motivoDesligado() {
        return motivoDesligado;
    }

    /** System Prompt + regras de negócio, prontos para ir ao modelo. */
    public String textoSistema() {
        return textoSistema;
    }

    public String promptFiltro() {
        return promptFiltro;
    }

    public String versao() {
        return versao;
    }
}
