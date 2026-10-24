package com.hackatona.timevermelho.servico;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.dominio.AjusteRegulador;
import com.hackatona.timevermelho.dominio.AjusteReguladorRepository;
import com.hackatona.timevermelho.dominio.AlertaRepository;
import com.hackatona.timevermelho.dominio.TipoAjuste;
import com.hackatona.timevermelho.motor.Reavaliacao;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.motor.StatusFila;
import com.hackatona.timevermelho.motor.TipoReavaliacao;

/** A revisão diária do regulador: o que mudou, quem precisa ser olhado de novo e quem pode ter saído da fila. */
@Service
public class AlertaService {

    private static final int MAX_ITENS = 50;

    private final FilaService fila;
    private final AlertaRepository alertas;
    private final AjusteReguladorRepository ajustes;

    public AlertaService(FilaService fila, AlertaRepository alertas, AjusteReguladorRepository ajustes) {
        this.fila = fila;
        this.alertas = alertas;
        this.ajustes = ajustes;
    }

    public Dtos.Alertas alertas() {
        var snap = fila.atual();

        List<Dtos.AlertaDto> recentes = alertas.findTop50ByOrderByCriadoEmDescIdDesc().stream()
                .map(a -> new Dtos.AlertaDto(a.getId(), a.getSolicitacaoId(), a.getNome(), a.getTipo().name(),
                        a.getMensagem(), a.getClasseAntes() == null ? null : a.getClasseAntes().name(),
                        a.getClasseDepois() == null ? null : a.getClasseDepois().name(), a.getPosicaoAntes(),
                        a.getPosicaoDepois(), a.getCriadoEm())).toList();

        // todos já estão na ordem clínica: ativos primeiro, depois os fora da fila
        Map<TipoReavaliacao, List<Dtos.ItemReavaliacao>> grupos = new EnumMap<>(TipoReavaliacao.class);
        for (ResultadoPaciente r : snap.todos()) {
            if (r.getStatus() == StatusFila.SAIDA_CONFIRMADA) continue;
            for (Reavaliacao x : r.getReavaliacoes()) {
                grupos.computeIfAbsent(x.tipo(), k -> new ArrayList<>()).add(new Dtos.ItemReavaliacao(
                        r.getCaso().solicitacaoId(), r.getCaso().nome(), r.classe().name(), r.getPosicao(),
                        r.getStatus().name(), x.mensagem()));
            }
        }
        List<Dtos.GrupoReavaliacao> reavaliacao = new ArrayList<>();
        for (var e : grupos.entrySet()) {
            var itens = e.getValue();
            reavaliacao.add(new Dtos.GrupoReavaliacao(e.getKey().name(), e.getKey().titulo(), itens.size(),
                    itens.subList(0, Math.min(MAX_ITENS, itens.size()))));
        }

        // o paciente disse que não quer ser operado, ou está em dúvida: o regulador conversa e decide (nada muda sozinho)
        List<Dtos.ItemReavaliacao> naoQuer = new ArrayList<>(), duvida = new ArrayList<>();
        for (ResultadoPaciente r : snap.ativos()) {
            String d = r.getDesejoCirurgia();
            if ("NAO_QUER_OPERAR".equals(d)) naoQuer.add(new Dtos.ItemReavaliacao(r.getCaso().solicitacaoId(), r.getCaso().nome(),
                    r.classe().name(), r.getPosicao(), r.getStatus().name(),
                    "Paciente disse que não quer ser operado. Converse sobre benefícios, riscos, alternativas e não fazer nada; "
                            + "se ele mantiver, confirme a saída na página do paciente."));
            if ("EM_DUVIDA".equals(d)) duvida.add(new Dtos.ItemReavaliacao(r.getCaso().solicitacaoId(), r.getCaso().nome(),
                    r.classe().name(), r.getPosicao(), r.getStatus().name(),
                    "Paciente em dúvida sobre a cirurgia. Converse sobre benefícios, riscos, alternativas e não fazer nada."));
        }
        if (!naoQuer.isEmpty()) reavaliacao.add(0, new Dtos.GrupoReavaliacao("PACIENTE_NAO_QUER_OPERAR",
                "Paciente não quer ser operado", naoQuer.size(), naoQuer.subList(0, Math.min(MAX_ITENS, naoQuer.size()))));
        if (!duvida.isEmpty()) reavaliacao.add(naoQuer.isEmpty() ? 0 : 1, new Dtos.GrupoReavaliacao("PACIENTE_EM_DUVIDA",
                "Paciente em dúvida sobre a cirurgia", duvida.size(), duvida.subList(0, Math.min(MAX_ITENS, duvida.size()))));

        List<Dtos.Saida> saidas = snap.todos().stream()
                .filter(r -> r.getStatus() == StatusFila.SAIDA_SUGERIDA)
                .map(r -> new Dtos.Saida(r.getCaso().solicitacaoId(), r.getCaso().nome(), r.getCaso().cidFormatado(),
                        r.oncologico(), r.getStatus().name(), r.getSinalSaida().descricao(),
                        r.getSinalSaida().evidencia(), r.getSinalSaida().fonte()))
                .toList();

        return new Dtos.Alertas(recentes, reavaliacao, saidas, rebaixamentos());
    }

    /** Um ciclo de rebaixamento: a sugestão (ou o bloqueio) e, se houver, a decisão do regulador. */
    private static final class Ciclo {
        final AjusteRegulador inicio;
        AjusteRegulador decisao;

        Ciclo(AjusteRegulador inicio) {
            this.inicio = inicio;
        }

        String estado() {
            if (inicio.getTipo() == TipoAjuste.REBAIXAMENTO_BLOQUEADO) return "BLOQUEADO";
            if (decisao == null) return "PENDENTE";
            return decisao.getTipo() == TipoAjuste.CONFIRMAR_REBAIXAMENTO ? "CONFIRMADO" : "REJEITADO";
        }
    }

    private List<Ciclo> ciclos() {
        List<Ciclo> todos = new ArrayList<>();
        Map<String, Ciclo> aberto = new HashMap<>();
        for (AjusteRegulador a : ajustes.findAllByOrderByCriadoEmAscIdAsc()) {
            switch (a.getTipo()) {
                case SUGERIR_REBAIXAMENTO -> {
                    Ciclo c = new Ciclo(a);
                    todos.add(c);
                    aberto.put(a.getSolicitacaoId(), c);
                }
                case REBAIXAMENTO_BLOQUEADO -> todos.add(new Ciclo(a));
                case CONFIRMAR_REBAIXAMENTO, REJEITAR_REBAIXAMENTO -> {
                    Ciclo c = aberto.remove(a.getSolicitacaoId());
                    if (c != null) c.decisao = a;
                }
                default -> { }
            }
        }
        return todos;
    }

    /**
     * Rebaixamentos para o regulador: todos os pendentes (na ordem da fila) e os últimos decididos ou bloqueados.
     * Divergência com a prioridade manual não entra aqui (fica em reavaliação): só sugestões concretas de rebaixamento.
     */
    public List<Dtos.Rebaixamento> rebaixamentos() {
        var snap = fila.atual();
        List<Dtos.Rebaixamento> lista = ciclos().stream().map(c -> dto(c, snap)).toList();
        List<Dtos.Rebaixamento> pendentes = lista.stream().filter(r -> "PENDENTE".equals(r.estado()))
                .sorted(Comparator.comparing(r -> r.posicao() == null ? Integer.MAX_VALUE : r.posicao())).toList();
        List<Dtos.Rebaixamento> outros = lista.stream().filter(r -> !"PENDENTE".equals(r.estado()))
                .sorted(Comparator.comparing((Dtos.Rebaixamento r) -> r.decididoEm() == null ? r.sugeridoEm() : r.decididoEm())
                        .reversed()).limit(MAX_ITENS).toList();
        List<Dtos.Rebaixamento> resultado = new ArrayList<>(pendentes);
        resultado.addAll(outros);
        return resultado;
    }

    /** O último ciclo de rebaixamento do paciente, ou null. */
    public Dtos.Rebaixamento rebaixamentoDe(String solicitacaoId) {
        var snap = fila.atual();
        List<Ciclo> doPaciente = ciclos().stream().filter(c -> c.inicio.getSolicitacaoId().equals(solicitacaoId)).toList();
        return doPaciente.isEmpty() ? null : dto(doPaciente.getLast(), snap);
    }

    private static Dtos.Rebaixamento dto(Ciclo c, FilaService.Snapshot snap) {
        AjusteRegulador s = c.inicio;
        ResultadoPaciente r = snap.get(s.getSolicitacaoId());
        boolean bloqueado = s.getTipo() == TipoAjuste.REBAIXAMENTO_BLOQUEADO;
        AjusteRegulador d = c.decisao;
        LocalDateTime decididoEm = bloqueado ? s.getCriadoEm() : d == null ? null : d.getCriadoEm();
        return new Dtos.Rebaixamento(s.getSolicitacaoId(), r == null ? null : r.getCaso().nome(),
                r != null && r.oncologico(), r == null ? null : r.getPosicao(), c.estado(),
                nome(s.getClasseAnterior()), nome(s.getClasseNova()), r == null ? null : r.classe().name(),
                s.getJustificativa(), s.getEvidencias(), s.isAutomatico(), s.getAutor(), s.getCriadoEm(),
                bloqueado ? "Rebaixamento de oncológico nunca é aplicado" : d == null ? null : d.getJustificativa(),
                bloqueado ? "FilaVIVA (regra de proteção)" : d == null ? null : d.getAutor(), decididoEm);
    }

    private static String nome(Enum<?> e) {
        return e == null ? null : e.name();
    }
}
