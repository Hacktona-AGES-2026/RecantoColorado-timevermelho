package com.hackatona.timevermelho.agente.contexto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.hackatona.timevermelho.motor.ClasseSwalis;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.motor.StatusFila;
import com.hackatona.timevermelho.servico.AlertaService;
import com.hackatona.timevermelho.servico.FilaService;
import com.hackatona.timevermelho.servico.IndicadoresService;

/**
 * Lê os serviços que já existem, só pelos métodos de consulta, e entrega apenas agregados. É a ÚNICA classe do
 * agente que conhece os serviços da aplicação (um teste de guarda confere isso).
 */
@Component
public class LeituraAplicacaoSpring implements LeituraAplicacao {

    private final FilaService fila;
    private final IndicadoresService indicadores;
    private final AlertaService alertas;

    public LeituraAplicacaoSpring(FilaService fila, IndicadoresService indicadores, AlertaService alertas) {
        this.fila = fila;
        this.indicadores = indicadores;
        this.alertas = alertas;
    }

    @Override
    public ResumoFila resumoFila() {
        var snap = fila.atual();
        Map<String, Long> porClasse = new LinkedHashMap<>();
        for (ClasseSwalis c : ClasseSwalis.values()) porClasse.put(c.name(), 0L);
        Map<String, Long> porEspecialidade = new LinkedHashMap<>();
        for (ResultadoPaciente r : snap.ativos()) {
            porClasse.merge(r.classe().name(), 1L, Long::sum);
            String esp = r.getCaso().especialidade();
            if (esp != null) porEspecialidade.merge(esp, 1L, Long::sum);
        }
        return new ResumoFila(snap.ativos().size(), snap.todos().size() - snap.ativos().size(), porClasse,
                porEspecialidade);
    }

    @Override
    public List<Indicador> indicadores() {
        return indicadores.indicadores().comparacoes().stream()
                .map(c -> new Indicador(c.indicador(), c.antes(), c.depois())).toList();
    }

    @Override
    public List<String> especialidades() {
        return fila.atual().ativos().stream().map(r -> r.getCaso().especialidade()).filter(e -> e != null)
                .distinct().sorted().toList();
    }

    @Override
    public ResumoEspecialidade resumoEspecialidade(String especialidade) {
        var doGrupo = fila.atual().ativos().stream().filter(r -> especialidade.equals(r.getCaso().especialidade())).toList();
        if (doGrupo.isEmpty()) return null;
        Map<String, Long> porClasse = new LinkedHashMap<>();
        for (ClasseSwalis c : ClasseSwalis.values()) porClasse.put(c.name(), 0L);
        doGrupo.forEach(r -> porClasse.merge(r.classe().name(), 1L, Long::sum));
        return new ResumoEspecialidade(especialidade, doGrupo.size(), porClasse,
                (int) doGrupo.stream().filter(ResultadoPaciente::oncologico).count(),
                (int) doGrupo.stream().filter(r -> !r.getReavaliacoes().isEmpty()).count(),
                (int) doGrupo.stream().filter(r -> r.getStatus() == StatusFila.SAIDA_SUGERIDA).count());
    }

    @Override
    public List<GrupoReavaliacao> reavaliacoes() {
        // os itens de cada grupo (com nomes de pacientes) são descartados aqui: só título e total saem
        return alertas.alertas().reavaliacao().stream().map(g -> new GrupoReavaliacao(g.titulo(), g.total())).toList();
    }
}
