package com.hackatona.timevermelho.agente.api;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import com.hackatona.timevermelho.agente.categorizacao.CategoriaDado;
import com.hackatona.timevermelho.agente.categorizacao.CategorizacaoService;
import com.hackatona.timevermelho.agente.categorizacao.CategorizacaoSugerida;
import com.hackatona.timevermelho.agente.relatorios.RelatorioIA;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contratos da API do agente (categorização e relatórios). O chat tem o seu próprio formato de eventos. */
public final class AgenteDtos {

    private AgenteDtos() {}

    // ---------- categorização ----------

    public record CategorizarRequest(@NotBlank @Size(max = CategorizacaoService.MAX_TEXTO) String texto,
                                     @Size(max = 60) String solicitante) {
    }

    public record RevisaoRequest(@NotNull CategorizacaoService.Acao acao, CategoriaDado categoriaCorrigida,
                                 @Size(max = 500) String justificativa, @Size(max = 60) String autor) {
    }

    public record CategorizacaoDto(String id, String texto, String categoria, String categoriaRotulo,
                                   List<String> subtemas, double confianca, String justificativa, String origem,
                                   boolean atencaoHumana, String statusRevisao, String categoriaFinal,
                                   String justificativaRevisao, String revisadoPor, LocalDateTime revisadoEm,
                                   LocalDateTime criadoEm, String versaoPrompt, boolean sugestaoDeIA, String aviso) {

        static CategorizacaoDto de(CategorizacaoSugerida s) {
            List<String> subtemas = s.getSubtemas() == null || s.getSubtemas().isBlank() ? List.of()
                    : Arrays.stream(s.getSubtemas().split(", ")).toList();
            return new CategorizacaoDto(s.getId(), s.getTexto(), s.getCategoriaSugerida().name(),
                    s.getCategoriaSugerida().rotulo(), subtemas, s.getConfianca(), s.getJustificativa(),
                    s.getOrigem().name(), s.isAtencaoHumana(), s.getStatusRevisao().name(),
                    s.getCategoriaFinal() == null ? null : s.getCategoriaFinal().name(), s.getJustificativaRevisao(),
                    s.getRevisadoPor(), s.getRevisadoEm(), s.getCriadoEm(), s.getVersaoPrompt(), true,
                    CategorizacaoService.AVISO);
        }
    }

    // ---------- relatórios ----------

    public record RelatorioRequest(@NotNull RelatorioIA.Tipo tipo, @Size(max = 100) String especialidade,
                                   @Size(max = 60) String solicitante) {
    }

    public record RelatorioResumoDto(String id, String tipo, String especialidade, String titulo, String solicitante,
                                     LocalDateTime criadoEm) {

        static RelatorioResumoDto de(RelatorioIA r) {
            return new RelatorioResumoDto(r.getId(), r.getTipo().name(), r.getEspecialidade(), r.getTitulo(),
                    r.getSolicitante(), r.getCriadoEm());
        }
    }

    public record RelatorioDto(String id, String tipo, String especialidade, String titulo, String solicitante,
                               LocalDateTime criadoEm, String texto, String dadosUsados, String versaoPrompt,
                               boolean geradoPorIA) {

        static RelatorioDto de(RelatorioIA r) {
            return new RelatorioDto(r.getId(), r.getTipo().name(), r.getEspecialidade(), r.getTitulo(),
                    r.getSolicitante(), r.getCriadoEm(), r.getTexto(), r.getDadosUsados(), r.getVersaoPrompt(), true);
        }
    }

    public record Especialidades(List<String> especialidades) {
    }
}
