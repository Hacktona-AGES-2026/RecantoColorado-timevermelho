package com.hackatona.timevermelho.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.hackatona.timevermelho.dominio.TipoAjuste;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Contrato da API. Cada record aqui tem um tipo com o mesmo nome em frontend/src/lib/api.ts.
 * Se mudar um, mude o outro.
 */
public final class Dtos {

    private Dtos() {}

    // ---------- fila ----------

    public record Motivo(String texto, String evidencia, String fonte) {}

    public record ItemFila(String solicitacaoId, String pacienteId, String nome, Integer idade, String sexo,
                           String municipio, String procedimento, String especialidade, String cid,
                           String cidDescricao, boolean oncologico, Integer posicao, Integer posicaoManual,
                           Integer delta, String classe, String classeCalculada, boolean ajustada, int prazoDias,
                           int diasEmFila, double percentualPrazo, double score, String prioridadeManual,
                           String status, boolean movidoPelaRegraDeOuro, int reavaliacoes, List<Motivo> motivos,
                           String justificativa, String rebaixamento) {}

    /** Qual fila uma resposta representa. */
    public record SnapshotDto(String tipo, String versao, java.time.Instant calculadoEm, int eventosRegistrados,
                              int decisoesRegulador, String descricao) {}

    public record Pagina<T>(List<T> itens, long total, int pagina, int tamanho, Map<String, Long> porClasse,
                            List<String> especialidades, SnapshotDto snapshot) {}

    // ---------- paciente ----------

    public record SinalDto(String tipo, String rotulo, String categoria, String descricao, String evidencia,
                           String fonte, LocalDate data) {}

    public record Componente(String chave, String nome, double pontos, double maximo, String explicacao) {}

    public record ClassificacaoDto(String classe, String classeCalculada, String descricaoClasse, int prazoDias,
                                   String regra, boolean ajustada, String justificativaAjuste,
                                   List<SinalDto> determinantes) {}

    public record EventoDto(String id, LocalDate data, String tipo, String resultado, Double valor,
                            String descricao, String origem, boolean sintetico, String solicitacaoOriginal) {}

    public record AjusteDto(Long id, String tipo, String classeAnterior, String classeNova, String justificativa,
                            String autor, LocalDateTime criadoEm) {}

    public record ReavaliacaoDto(String tipo, String titulo, String mensagem) {}

    public record DadosClinicos(String sexo, Integer idade, String municipio, String uf, Integer distanciaKm,
                                String procedimentoId, String procedimento, String especialidade, String porte,
                                Integer riscoCirurgico, String cid, String cidDescricao, String cidSecundario,
                                Integer asa, String comorbidades, Integer dor, String limitacao,
                                String justificativa, String observacoes, LocalDate dataEntrada,
                                String prioridadeManual, LocalDate dataUltimaRevisao, String flagOncologico,
                                String flagDiabetico, String flagPiora) {}

    public record PacienteDetalhe(ItemFila item, DadosClinicos dados, ClassificacaoDto classificacao,
                                  List<Componente> componentes, List<SinalDto> sinais, List<EventoDto> eventos,
                                  List<AjusteDto> ajustes, List<ReavaliacaoDto> reavaliacoes, List<String> duplicatas,
                                  SinalDto sinalSaida, Rebaixamento rebaixamento) {}

    // ---------- eventos e ajustes ----------

    public record NovoEvento(@NotBlank String solicitacaoId, @NotBlank String tipo, String resultado,
                             Double valorNumerico, @Size(max = 2000) String descricao, LocalDate data) {}

    public record Impacto(String solicitacaoId, String nome, Integer posicaoAntes, Integer posicaoDepois,
                          String classeAntes, String classeDepois, String statusAntes, String statusDepois,
                          String mensagem) {}

    public record AjusteRequest(@NotBlank String solicitacaoId, @NotNull TipoAjuste tipo, String classeNova,
                                @NotBlank @Size(min = 10, max = 2000,
                                        message = "a justificativa precisa ter pelo menos 10 caracteres") String justificativa,
                                String autor) {}

    // ---------- simulação ----------

    public record EventoHipotetico(@NotBlank String tipo, String resultado, Double valorNumerico, String descricao) {}

    public record SimulacaoRequest(@NotBlank String solicitacaoId, @Min(0) @Max(10) Integer dor, String limitacao,
                                   @Valid List<EventoHipotetico> eventos, @Size(max = 1000) String textoAdicional) {}

    public record Simulacao(ItemFila antes, ItemFila depois, List<SinalDto> sinaisNovos,
                            List<Componente> componentesDepois, String mensagem) {}

    public record PesosDto(@PositiveOrZero double diagnostico, @PositiveOrZero double piora,
                           @PositiveOrZero double dor, @PositiveOrZero double limitacao,
                           @PositiveOrZero double espera, @PositiveOrZero double risco) {}

    public record Movimento(String solicitacaoId, String nome, String classe, int posicaoAntes, int posicaoDepois,
                            int delta) {}

    public record SimulacaoPesos(PesosDto pesos, List<Movimento> maioresSubidas, List<Movimento> maioresQuedas,
                                 List<ItemFila> topo, boolean regraDeOuroRespeitada, int pacientesQueMudaram) {}

    // ---------- alertas ----------

    public record AlertaDto(Long id, String solicitacaoId, String nome, String tipo, String mensagem,
                            String classeAntes, String classeDepois, Integer posicaoAntes, Integer posicaoDepois,
                            LocalDateTime criadoEm) {}

    public record ItemReavaliacao(String solicitacaoId, String nome, String classe, Integer posicao, String status,
                                  String mensagem) {}

    public record GrupoReavaliacao(String tipo, String titulo, int total, List<ItemReavaliacao> itens) {}

    public record Saida(String solicitacaoId, String nome, String cid, boolean oncologico, String status,
                        String motivo, String evidencia, String fonte) {}

    /**
     * Um rebaixamento (sugerido, confirmado, rejeitado ou bloqueado), com o que o regulador precisa para decidir.
     * estado: PENDENTE, CONFIRMADO, REJEITADO ou BLOQUEADO.
     */
    public record Rebaixamento(String solicitacaoId, String nome, boolean oncologico, Integer posicao, String estado,
                               String classeVigente, String classeProposta, String classeAtual, String fatores,
                               String evidencias, boolean automatico, String sugeridoPor, LocalDateTime sugeridoEm,
                               String decisao, String decididoPor, LocalDateTime decididoEm) {}

    public record Alertas(List<AlertaDto> recentes, List<GrupoReavaliacao> reavaliacao, List<Saida> saidas,
                          List<Rebaixamento> rebaixamentos) {}

    // ---------- indicadores ----------

    public record Comparacao(String indicador, String antes, String depois, String descricao) {}

    public record Faixa(String faixa, int oncoAntes, int oncoDepois, int semUrgenciaAntes, int semUrgenciaDepois) {}

    public record LinhaMatriz(String prioridadeManual, Map<String, Long> porClasse) {}

    public record GrupoPosicao(String grupo, int pacientes, double posicaoMediaAntes, double posicaoMediaDepois) {}

    public record QualidadeDado(String item, long quantidade) {}

    public record Indicadores(int totalAtivos, int totalForaDaFila, Map<String, Long> porClasse,
                              List<Comparacao> comparacoes, List<Faixa> faixas, List<LinhaMatriz> matriz,
                              List<GrupoPosicao> porRegiao, List<QualidadeDado> qualidadeDados) {}

    public record Erro(String mensagem) {}
}
