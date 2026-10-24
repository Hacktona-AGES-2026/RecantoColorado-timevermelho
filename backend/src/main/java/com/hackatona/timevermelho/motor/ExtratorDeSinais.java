package com.hackatona.timevermelho.motor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.hackatona.timevermelho.motor.texto.ExtratorDeTexto;
import com.hackatona.timevermelho.motor.texto.RegrasClinicas;

/** Junta os sinais das colunas, dos eventos clínicos e do texto livre de um paciente. */
public class ExtratorDeSinais {

    static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int JANELA_PIORA_DIAS = 180;
    private static final int JANELA_EXAME_DIAS = 365;
    private static final double HBA1C_LIMITE = 8.5;

    private final LocalDate referencia;
    private final ExtratorDeTexto texto;
    private final Map<String, String> cidsUrgentes;
    private final Pattern cidsIncertos;

    public ExtratorDeSinais(RegrasClinicas regras, LocalDate referencia) {
        this.referencia = referencia;
        this.texto = new ExtratorDeTexto(regras);
        this.cidsUrgentes = regras.cidsUrgentes();
        this.cidsIncertos = Pattern.compile(regras.cidsIncertos());
    }

    public List<Sinal> extrair(Caso c) {
        List<Sinal> s = new ArrayList<>();
        diagnostico(c, s);
        eventos(c, s);
        sintomas(c, s);
        s.addAll(texto.extrair(c.justificativa(), c.observacoes()));
        textoDosEventosNovos(c, s);
        qualidadeDoCadastro(c, s);
        return s;
    }

    /**
     * Lê a descrição dos eventos registrados depois da importação (os do dataset de referência ficam como
     * estão). O texto nunca muda a classe: o que muda é o tipo e o resultado estruturados do evento. Uma
     * menção afirmativa de alarme grave só gera "possível agravamento grave, a confirmar" (revisão urgente).
     */
    private void textoDosEventosNovos(Caso c, List<Sinal> s) {
        if (c.eventos() == null) return;
        Set<String> jaTem = new java.util.HashSet<>();
        for (Sinal x : s) jaTem.add(x.tipo() + "|" + x.descricao());
        for (Evento e : c.eventos()) {
            if (!e.sintetico() || e.descricao() == null || e.descricao().isBlank()) continue;
            for (Sinal achado : texto.extrairDeEvento(e.descricao(), "evento " + e.id())) {
                if (jaTem.add(achado.tipo() + "|" + achado.descricao())) {
                    s.add(new Sinal(achado.tipo(), achado.descricao() + " (evento de " + e.data().format(BR) + ")",
                            achado.evidencia(), achado.fonte(), e.data()));
                }
            }
        }
    }

    private void diagnostico(Caso c, List<Sinal> s) {
        String cid = c.cid() == null ? "" : c.cid();
        String desc = "CID " + c.cidFormatado() + (c.cidDescricao() == null ? "" : " — " + c.cidDescricao());
        if (cid.startsWith("C")) {
            s.add(Sinal.de(TipoSinal.ONCO_CID, desc, desc, "cid_principal"));
        }
        if (cidsUrgentes.containsKey(cid)) {
            s.add(Sinal.de(TipoSinal.CID_URGENTE, cidsUrgentes.get(cid), desc, "cid_principal"));
        }
        if (cidsIncertos.matcher(cid).find()) {
            s.add(Sinal.de(TipoSinal.CID_INCERTO, "Lesão incerta ou achado suspeito (" + c.cidFormatado() + ")",
                    desc, "cid_principal"));
        }
    }

    private void eventos(Caso c, List<Sinal> s) {
        List<Evento> evs = c.eventos() == null ? List.of() : c.eventos().stream()
                .sorted(Comparator.comparing(Evento::data)).toList();
        Evento ultimoContato = null;
        List<Evento> contatos = new ArrayList<>();
        List<Evento> hba1c = new ArrayList<>();
        int ps = 0;

        for (Evento e : evs) {
            boolean recente = !e.data().isBefore(referencia.minusDays(JANELA_PIORA_DIAS));
            boolean exameRecente = !e.data().isBefore(referencia.minusDays(JANELA_EXAME_DIAS));
            String quando = e.data().format(BR);
            String fonte = "evento " + e.id();
            switch (e.tipo()) {
                case "biopsia" -> {
                    if ("positivo".equals(e.resultado())) {
                        s.add(new Sinal(TipoSinal.BIOPSIA_POSITIVA, "Biópsia positiva em " + quando,
                                e.descricao(), fonte, e.data()));
                    }
                }
                case "exame_imagem" -> {
                    if ("progressao".equals(e.resultado()) && exameRecente) {
                        s.add(new Sinal(TipoSinal.PROGRESSAO_IMAGEM, "Progressão em imagem em " + quando,
                                e.descricao(), fonte, e.data()));
                    }
                }
                case "pronto_socorro" -> {
                    if (recente) {
                        ps++;
                        s.add(new Sinal(TipoSinal.PS_RECENTE, "Pronto-socorro em " + quando,
                                e.descricao(), fonte, e.data()));
                    }
                }
                case "internacao" -> {
                    if (recente) {
                        String dias = e.valor() == null ? "" : " (" + e.valor().intValue() + " dias)";
                        s.add(new Sinal(TipoSinal.INTERNACAO_RECENTE, "Internação em " + quando + dias,
                                e.descricao(), fonte, e.data()));
                    }
                }
                case "consulta_reavaliacao" -> {
                    if ("piora".equals(e.resultado()) && exameRecente) {
                        s.add(new Sinal(TipoSinal.REAVALIACAO_PIORA, "Reavaliação com piora em " + quando,
                                e.descricao(), fonte, e.data()));
                    }
                }
                case "contato_telefonico" -> {
                    contatos.add(e);
                    ultimoContato = e;
                    if ("relata_piora".equals(e.resultado()) && recente) {
                        s.add(new Sinal(TipoSinal.RELATA_PIORA, "Relatou piora por telefone em " + quando,
                                e.descricao(), fonte, e.data()));
                    }
                }
                case "hba1c" -> {
                    if (e.valor() != null) hba1c.add(e);
                }
                default -> { /* falta e outros: não mudam a urgência */ }
            }
        }

        if (ultimoContato != null) {
            String quando = ultimoContato.data().format(BR);
            String fonte = "evento " + ultimoContato.id();
            switch (ultimoContato.resultado()) {
                case "desistiu" -> s.add(new Sinal(TipoSinal.SAIDA_DESISTENCIA, "Desistiu em contato de " + quando,
                        ultimoContato.descricao(), fonte, ultimoContato.data()));
                case "informou_cirurgia_outro_servico" -> s.add(new Sinal(TipoSinal.SAIDA_OUTRO_SERVICO,
                        "Informou cirurgia em outro serviço em " + quando, ultimoContato.descricao(), fonte,
                        ultimoContato.data()));
                default -> { }
            }
        }
        if (contatos.size() >= 2) {
            var ultimos = contatos.subList(contatos.size() - 2, contatos.size());
            if (ultimos.stream().allMatch(e -> "nao_atendeu".equals(e.resultado()))) {
                Evento ult = ultimos.get(1);
                s.add(new Sinal(TipoSinal.SEM_CONTATO, "Não atendeu as 2 últimas ligações (última em "
                        + ult.data().format(BR) + ")", ult.descricao(), "evento " + ult.id(), ult.data()));
            }
        }
        if (!hba1c.isEmpty()) {
            Evento ult = hba1c.getLast();
            Evento prim = hba1c.getFirst();
            if (ult.valor() > HBA1C_LIMITE) {
                s.add(new Sinal(TipoSinal.HBA1C_ALTA, "HbA1c " + fmt(ult.valor()) + "% em " + ult.data().format(BR),
                        ult.descricao(), "evento " + ult.id(), ult.data()));
            }
            if (hba1c.size() >= 2 && ult.valor() - prim.valor() >= 1.0) {
                s.add(new Sinal(TipoSinal.HBA1C_SUBINDO, "HbA1c subiu de " + fmt(prim.valor()) + "% para "
                        + fmt(ult.valor()) + "%", ult.descricao(), "evento " + ult.id(), ult.data()));
            }
        }
    }

    private void sintomas(Caso c, List<Sinal> s) {
        if (c.dor() == null) {
            s.add(Sinal.de(TipoSinal.DADO_INCOMPLETO, "Dor (EVA) não informada", "vazio", "intensidade_dor_eva"));
        } else if (c.dor() >= 7) {
            s.add(Sinal.de(TipoSinal.DOR_INTENSA, "Dor " + c.dor() + "/10", "EVA " + c.dor(), "intensidade_dor_eva"));
        } else if (c.dor() >= 4) {
            s.add(Sinal.de(TipoSinal.DOR_MODERADA, "Dor " + c.dor() + "/10", "EVA " + c.dor(), "intensidade_dor_eva"));
        }
        String lim = c.limitacao();
        if (lim == null || lim.isBlank()) {
            s.add(Sinal.de(TipoSinal.DADO_INCOMPLETO, "Limitação funcional não informada", "vazio", "limitacao_funcional"));
        } else if (lim.equals("grave")) {
            s.add(Sinal.de(TipoSinal.LIMITACAO_GRAVE, "Limitação funcional grave", lim, "limitacao_funcional"));
        } else if (lim.equals("moderada")) {
            s.add(Sinal.de(TipoSinal.LIMITACAO_MODERADA, "Limitação funcional moderada", lim, "limitacao_funcional"));
        }
        String j = c.justificativa() == null ? "" : c.justificativa().trim();
        if (j.length() < 30) {
            s.add(Sinal.de(TipoSinal.DADO_INCOMPLETO, "Justificativa clínica pobre",
                    j.isEmpty() ? "vazia" : "\"" + j + "\"", "justificativa_clinica"));
        }
    }

    private static final Set<TipoSinal> PIORA = EnumSet.of(TipoSinal.PS_RECENTE, TipoSinal.INTERNACAO_RECENTE,
            TipoSinal.PROGRESSAO_IMAGEM, TipoSinal.REAVALIACAO_PIORA, TipoSinal.RELATA_PIORA,
            TipoSinal.BIOPSIA_POSITIVA);

    private void qualidadeDoCadastro(Caso c, List<Sinal> s) {
        boolean onco = c.oncologicoPeloCid();
        boolean flagOnco = "sim".equalsIgnoreCase(c.flagOncologico());
        if (onco != flagOnco) {
            s.add(Sinal.de(TipoSinal.FLAG_ONCO_DIVERGENTE,
                    "Flag oncológico = \"" + c.flagOncologico() + "\", mas o CID é " + c.cidFormatado(),
                    "flag_oncologico=" + c.flagOncologico(), "flag_oncologico"));
        }
        boolean temPiora = s.stream().anyMatch(x -> PIORA.contains(x.tipo()));
        if (temPiora && !"sim".equalsIgnoreCase(c.flagPiora())) {
            s.add(Sinal.de(TipoSinal.FLAG_PIORA_DESATUALIZADA,
                    "Flag de piora = \"" + c.flagPiora() + "\", mas há eventos de piora",
                    "flag_piora_clinica=" + c.flagPiora(), "flag_piora_clinica"));
        }
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f", v);
    }
}
