package com.hackatona.timevermelho.servico;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.hackatona.timevermelho.dominio.RegistroAptidao;
import com.hackatona.timevermelho.importacao.Normalizacao;
import com.hackatona.timevermelho.motor.Caso;
import com.hackatona.timevermelho.motor.ClasseSwalis;
import com.hackatona.timevermelho.motor.Evento;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.motor.TipoSinal;

/**
 * Parecer de aptidão cirúrgica e estimativa de consumo de recursos, a partir dos dados de saúde do paciente.
 * Regras simples e explicáveis (pontos por fator), para apoiar a decisão humana. Não muda a posição na fila:
 * a prioridade é pela necessidade clínica; a aptidão diz se dá para operar agora e o que precisa melhorar antes.
 *
 * Pontos e cortes são de um protótipo: devem ser validados com a equipe de anestesia e calibrados com dados reais.
 */
public final class AvaliadorAptidao {

    private AvaliadorAptidao() {}

    public enum Veredito { APTO, APTO_COM_RESSALVAS, OTIMIZAR_ANTES }

    public enum Nivel { BAIXO, MODERADO, ALTO }

    public enum Uti { NAO, POSSIVEL, PROVAVEL, PREVISTA }

    /**
     * @param modificavel o paciente pode melhorar (controle, hábito)
     * @param bloqueante precisa ser controlado antes de uma cirurgia eletiva
     */
    public record Fator(String descricao, int pontos, boolean modificavel, boolean bloqueante, String fonte) {}

    public record Recursos(int tempoCirurgiaMin, int tempoBaseMin, int permanenciaDias, Uti uti, Nivel consumo,
                           List<String> explicacao) {}

    public record Resultado(Veredito veredito, Nivel risco, int pontos, List<Fator> fatores, List<String> acoes,
                            List<String> preRequisitos, Recursos recursos, String observacaoPrioridade) {}

    static final double HBA1C_LIMITE = 8.5;
    private static final Locale BR = Locale.forLanguageTag("pt-BR");

    /** Última HbA1c conhecida: o evento mais recente, ou a do cadastro. */
    static Double ultimaHba1c(Caso c) {
        Evento ult = c.eventos() == null ? null : c.eventos().stream()
                .filter(e -> "hba1c".equals(e.tipo()) && e.valor() != null)
                .max(java.util.Comparator.comparing(Evento::data)).orElse(null);
        return ult != null ? ult.valor() : c.hba1cCadastro();
    }

    public static Resultado avaliar(ResultadoPaciente r, RegistroAptidao informados) {
        Caso c = r.getCaso();
        String com = c.comorbidades() == null ? "" : Normalizacao.semAcento(c.comorbidades()).toLowerCase(Locale.ROOT);
        List<Fator> f = new ArrayList<>();
        Set<String> acoes = new LinkedHashSet<>();

        int riscoProc = c.riscoCirurgico() == null ? 2 : c.riscoCirurgico();
        f.add(new Fator("Risco do procedimento " + riscoProc + " de 5" + (c.porte() == null ? "" : " (porte " + c.porte() + ")"),
                riscoProc, false, false, "procedimentos.csv"));
        if (c.asa() != null && c.asa() >= 3) {
            f.add(new Fator("Estado físico ASA " + c.asa(), c.asa() >= 4 ? 4 : 2, false, false, "asa"));
            acoes.add("Avaliação pré-anestésica com atenção ao ASA " + c.asa());
        }
        if (c.idade() != null && c.idade() >= 80) f.add(new Fator("Idade de " + c.idade() + " anos", 2, false, false, "idade"));
        else if (c.idade() != null && c.idade() >= 70) f.add(new Fator("Idade de " + c.idade() + " anos", 1, false, false, "idade"));

        boolean diabetes = c.diabetesNoCadastro();
        Double hba1c = ultimaHba1c(c);
        boolean descontrolado = hba1c != null && hba1c > HBA1C_LIMITE;
        if (descontrolado) {
            if (diabetes) {
                f.add(new Fator("Diabetes descontrolado: HbA1c " + String.format(BR, "%.1f", hba1c) + "% (acima de 8,5%)",
                        3, true, true, "hba1c"));
                acoes.add("Controlar a glicemia com a atenção básica ou a endocrinologia e repetir a HbA1c antes de agendar");
            } else {
                // o cadastro não registra diabetes: mostra só o exame, sem dar um diagnóstico que não está no dado
                f.add(new Fator("HbA1c alta: " + String.format(BR, "%.1f", hba1c) + "% (acima de 8,5%), sem diabetes "
                        + "registrado no cadastro", 3, true, true, "hba1c"));
                acoes.add("Investigar a HbA1c alta com a atenção básica ou a endocrinologia e repetir o exame antes de agendar");
            }
        } else if (diabetes) {
            f.add(new Fator("Diabetes" + (hba1c == null ? " (sem HbA1c recente)" : " controlado (HbA1c "
                    + String.format(BR, "%.1f", hba1c) + "%)"), 1, true, false, "comorbidades"));
            if (hba1c == null) acoes.add("Pedir HbA1c antes da cirurgia");
        }
        if (r.temSinal(TipoSinal.HBA1C_SUBINDO)) {
            f.add(new Fator("HbA1c subindo durante a espera", 1, true, false, "eventos"));
        }
        if (com.matches(".*\\bicc\\b.*") || com.contains("insuficiencia cardiaca")) {
            f.add(new Fator("Insuficiência cardíaca", 3, false, false, "comorbidades"));
            acoes.add("Avaliação cardiológica pré-operatória");
        }
        if (com.contains("cardiopatia")) {
            f.add(new Fator("Cardiopatia isquêmica", 2, false, false, "comorbidades"));
            acoes.add("Avaliação cardiológica pré-operatória");
        }
        if (com.contains("fibrilacao") || com.contains("anticoagul")) {
            f.add(new Fator("Arritmia ou anticoagulante (risco de sangramento e trombose)", 1, false, false, "comorbidades"));
            acoes.add("Plano para suspender e retomar o anticoagulante, com o médico assistente");
        }
        if (com.contains("dpoc") || com.contains("enfisema")) {
            f.add(new Fator("DPOC", 2, false, false, "comorbidades"));
            acoes.add("Avaliação pneumológica e otimização dos broncodilatadores");
        }
        if (com.contains("asma")) f.add(new Fator("Asma", 1, false, false, "comorbidades"));
        if (com.matches(".*\\bdrc\\b.*") || com.contains("doenca renal") || com.contains("insuficiencia renal")) {
            f.add(new Fator("Doença renal crônica", 2, false, false, "comorbidades"));
            acoes.add("Avaliar a função dos rins antes da cirurgia");
        }

        boolean obesidadeGrave = informados != null && informados.getImc() != null && informados.getImc() >= 40;
        if (obesidadeGrave) {
            f.add(new Fator("Obesidade grave: IMC " + String.format(BR, "%.1f", informados.getImc()), 3, true, false, "médico"));
            acoes.add("Orientação nutricional e plano de perda de peso");
        } else if (com.contains("obesidade") || (informados != null && informados.getImc() != null && informados.getImc() >= 30)) {
            f.add(new Fator("Obesidade" + (informados != null && informados.getImc() != null
                    ? " (IMC " + String.format(BR, "%.1f", informados.getImc()) + ")" : ""), 2, true, false,
                    com.contains("obesidade") ? "comorbidades" : "médico"));
            acoes.add("Orientação nutricional e plano de perda de peso");
        }
        boolean fuma = com.contains("tabagismo") || (informados != null && informados.isTabagismoAtual());
        if (fuma) {
            f.add(new Fator("Tabagismo", 1, true, false, com.contains("tabagismo") ? "comorbidades" : "médico"));
            acoes.add("Parar de fumar o quanto antes: semanas sem fumar antes da cirurgia reduzem complicações");
        }
        boolean pressaoDescontrolada = informados != null && informados.isPressaoDescontrolada();
        if (pressaoDescontrolada) {
            f.add(new Fator("Pressão alta descontrolada", 2, true, true, "médico"));
            acoes.add("Controlar a pressão arterial antes de agendar a cirurgia");
        } else if (com.matches(".*\\bhas\\b.*") || com.contains("hipertens")) {
            f.add(new Fator("Hipertensão", 1, true, false, "comorbidades"));
        }
        if (informados != null && informados.isColesterolAlto()) {
            f.add(new Fator("Colesterol alto", 1, true, false, "médico"));
            acoes.add("Acompanhar o colesterol com a atenção básica (em geral não adia a cirurgia)");
        }
        if (informados != null && informados.isAdesaoRuim()) {
            f.add(new Fator("Não está seguindo o tratamento das doenças crônicas", 2, true, false, "médico"));
            acoes.add("Conversar sobre adesão ao tratamento e envolver a atenção básica");
        }
        if (informados != null && informados.getOutros() != null && !informados.getOutros().isBlank()) {
            f.add(new Fator("Outros (informado pelo médico): " + informados.getOutros(), 0, true, false, "médico"));
        }

        int pontos = f.stream().mapToInt(Fator::pontos).sum();
        Nivel risco = pontos >= 10 ? Nivel.ALTO : pontos >= 6 ? Nivel.MODERADO : Nivel.BAIXO;
        boolean bloqueio = f.stream().anyMatch(Fator::bloqueante);
        Veredito veredito = bloqueio ? Veredito.OTIMIZAR_ANTES : risco == Nivel.ALTO ? Veredito.APTO_COM_RESSALVAS : Veredito.APTO;
        if (veredito == Veredito.APTO_COM_RESSALVAS) acoes.add("Liberação do anestesista e dos especialistas antes de agendar");

        List<String> pre = new ArrayList<>();
        if (!"realizada".equals(c.avaliacaoPreAnestesica())) {
            pre.add("Avaliação pré-anestésica " + (c.avaliacaoPreAnestesica() == null ? "não informada" : c.avaliacaoPreAnestesica()));
        }
        if (!"sim".equals(c.examesPreOpValidos())) pre.add("Exames pré-operatórios a atualizar");

        String obsPrioridade = null;
        if (veredito == Veredito.OTIMIZAR_ANTES && (r.oncologico() || r.classe() == ClasseSwalis.A1)) {
            obsPrioridade = (r.oncologico() ? "Paciente oncológico" : "Paciente A1") + ": a otimização precisa ser rápida e não "
                    + "deve passar do prazo da classe " + r.classe() + " (" + r.classe().prazoDias() + " dias). A equipe pode "
                    + "decidir operar mesmo assim se o benefício for maior que o risco.";
        }
        return new Resultado(veredito, risco, pontos, f, List.copyOf(acoes), pre,
                recursos(c, f, risco, descontrolado, obesidadeGrave), obsPrioridade);
    }

    /** Estimativa simples para planejamento: tempo de sala, dias de internação, UTI e índice de consumo. */
    static Recursos recursos(Caso c, List<Fator> f, Nivel risco, boolean diabetesDescontrolado, boolean obesidadeGrave) {
        List<String> exp = new ArrayList<>();
        int base = c.duracaoMin() == null ? 60 : c.duracaoMin();
        double fator = 1.0;
        boolean asa3 = c.asa() != null && c.asa() >= 3;
        boolean obeso = obesidadeGrave || f.stream().anyMatch(x -> x.descricao().startsWith("Obesidade"));
        boolean idoso = c.idade() != null && c.idade() >= 80;
        if (asa3) { fator += 0.10; exp.add("+10% de tempo de cirurgia por ASA " + c.asa()); }
        if (obeso) { fator += obesidadeGrave ? 0.25 : 0.15; exp.add("+" + (obesidadeGrave ? 25 : 15) + "% de tempo por obesidade"); }
        if (idoso) { fator += 0.10; exp.add("+10% de tempo por idade"); }
        int tempo = (int) (Math.round(base * fator / 5.0) * 5);

        int dias = switch (c.porte() == null ? "medio" : c.porte()) {
            case "pequeno" -> 1;
            case "grande" -> 5;
            case "especial" -> 7;
            default -> 2;
        };
        exp.add("Internação base de " + dias + " dia(s) pelo porte " + (c.porte() == null ? "médio" : c.porte()));
        int extra = 0;
        for (String chave : List.of("Insuficiência cardíaca", "DPOC", "Doença renal", "Diabetes descontrolado", "HbA1c alta")) {
            if (f.stream().anyMatch(x -> x.descricao().startsWith(chave))) { extra++; exp.add("+1 dia: " + chave.toLowerCase(Locale.ROOT)); }
        }
        if (idoso) { extra++; exp.add("+1 dia: idade"); }
        if (asa3) { extra++; exp.add("+1 dia: ASA " + c.asa()); }
        dias += Math.min(extra, 4);

        String precisa = c.necessitaUti() == null ? "nao" : c.necessitaUti();
        boolean cardioPulmonar = f.stream().anyMatch(x -> x.descricao().startsWith("Insuficiência") || x.descricao().startsWith("DPOC"));
        Uti uti = switch (precisa) {
            case "sim" -> Uti.PREVISTA;
            case "eventual" -> risco == Nivel.ALTO ? Uti.PROVAVEL : Uti.POSSIVEL;
            default -> risco == Nivel.ALTO && (cardioPulmonar || (c.asa() != null && c.asa() >= 4)) ? Uti.POSSIVEL : Uti.NAO;
        };
        if (uti != Uti.NAO) exp.add("UTI " + uti.name().toLowerCase(Locale.ROOT) + " (procedimento: " + precisa + ", risco " + risco.name().toLowerCase(Locale.ROOT) + ")");

        int indice = dias + switch (uti) { case PREVISTA -> 4; case PROVAVEL -> 3; case POSSIVEL -> 1; default -> 0; };
        Nivel consumo = indice >= 8 ? Nivel.ALTO : indice >= 4 ? Nivel.MODERADO : Nivel.BAIXO;
        return new Recursos(tempo, base, dias, uti, consumo, exp);
    }
}
