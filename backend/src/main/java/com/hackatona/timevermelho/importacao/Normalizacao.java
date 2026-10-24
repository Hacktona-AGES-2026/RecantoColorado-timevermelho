package com.hackatona.timevermelho.importacao;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

/** Funções de limpeza dos campos do dataset. */
public final class Normalizacao {

    private static final Set<String> MINUSCULAS = Set.of("de", "do", "da", "dos", "das", "e");

    private Normalizacao() {}

    public static String texto(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** "k40.9" → "K409". */
    public static String cid(String s) {
        String t = texto(s);
        return t == null ? null : t.toUpperCase(Locale.ROOT).replace(".", "");
    }

    /** "K409" → "K40.9" (para exibir). */
    public static String cidFormatado(String cid) {
        if (cid == null) return null;
        return cid.length() > 3 ? cid.substring(0, 3) + "." + cid.substring(3) : cid;
    }

    /** "Média" → "media"; vazio → null. */
    public static String prioridade(String s) {
        String t = texto(s);
        return t == null ? null : semAcento(t).toLowerCase(Locale.ROOT);
    }

    /** "PORTO ALEGRE" / "porto alegre" → "Porto Alegre". */
    public static String municipio(String s) {
        String t = texto(s);
        if (t == null) return null;
        String[] partes = t.toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length; i++) {
            String p = partes[i];
            if (i > 0) sb.append(' ');
            if (i > 0 && MINUSCULAS.contains(p)) sb.append(p);
            else sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    public static Integer inteiro(String s) {
        String t = texto(s);
        if (t == null) return null;
        try {
            return (int) Math.round(Double.parseDouble(t));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Double decimal(String s) {
        String t = texto(s);
        if (t == null) return null;
        try {
            return Double.parseDouble(t.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static LocalDate data(String s) {
        String t = texto(s);
        return t == null ? null : LocalDate.parse(t);
    }

    public static String minusculo(String s) {
        String t = texto(s);
        return t == null ? null : t.toLowerCase(Locale.ROOT);
    }

    public static String semAcento(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
