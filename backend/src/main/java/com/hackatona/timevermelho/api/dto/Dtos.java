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
                           String justificativa, String rebaixamento, String desejoCirurgia, Integer posicaoFixada,
                           String aptidao, String decisaoAptidao, Saude saude) {}

    /**
     * Saúde do paciente na fila (só leitura dos dados existentes; não muda a ordem).
     * diabetes: confirmado por comorbidades, flag do cadastro ou CID secundário E10–E14 (fontesDiabetes diz quais).
     * divergenciaDiabetes: a flag e a lista de comorbidades discordam. hba1c: última medida (exame ou cadastro).
     */
    public record Saude(boolean diabetes, List<String> fontesDiabetes, boolean divergenciaDiabetes, Double hba1c,
                        LocalDate dataHba1c, String origemHba1c, boolean hba1cAlta, List<String> comorbidades) {}

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

    /**
     * Uma decisão na trilha de auditoria. medicoId: médico cadastrado que fez a alteração (null = regulador ou sistema).
     * informacaoForaDoSistema: o médico informou algo que ainda não está registrado no sistema.
     */
    public record AjusteDto(Long id, String solicitacaoId, String tipo, String classeAnterior, String classeNova,
                            String justificativa, String autor, LocalDateTime criadoEm, Long medicoId,
                            boolean informacaoForaDoSistema, Integer posicaoFixada) {}

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
                                  SinalDto sinalSaida, Rebaixamento rebaixamento, DesejoCirurgiaDto desejoCirurgia,
                                  List<DesejoCirurgiaDto> historicoDesejo) {}

    /** O paciente quer ser operado? Uma resposta registrada (a mais recente vale). */
    public record DesejoCirurgiaDto(Long id, String resposta, String rotulo, String observacao, String autor,
                                    Long medicoId, LocalDateTime criadoEm) {}

    public record DesejoCirurgiaRequest(
            @NotNull(message = "informe a resposta do paciente") com.hackatona.timevermelho.dominio.RespostaCirurgia resposta,
            @Size(max = 1000) String observacao,
            @Size(max = 120) String autor,
            Long medicoId) {}

    // ---------- BRAN: benefícios, riscos, alternativas e nada ----------

    /** Rascunho montado pelo sistema (texto geral do procedimento + dados do paciente). O médico revisa antes. */
    public record BranRascunho(String solicitacaoId, String procedimento, String beneficios, String riscos,
                               String alternativas, String nada, List<String> fatoresDeRisco, String aviso) {}

    /** Uma comunicação BRAN registrada (a mais recente vale). */
    public record BranDto(Long id, String beneficios, String riscos, String alternativas, String nada, String forma,
                          String duvidas, boolean compreendeu, String autor, Long medicoId, LocalDateTime criadoEm) {}

    public record BranRequest(
            @NotBlank @Size(min = 10, max = 4000, message = "descreva os benefícios (mínimo 10 caracteres)") String beneficios,
            @NotBlank @Size(min = 10, max = 4000, message = "descreva os riscos (mínimo 10 caracteres)") String riscos,
            @NotBlank @Size(min = 10, max = 4000, message = "descreva as alternativas (mínimo 10 caracteres)") String alternativas,
            @NotBlank @Size(min = 10, max = 4000, message = "descreva o que acontece sem a cirurgia (mínimo 10 caracteres)") String nada,
            @NotNull(message = "informe como foi a conversa") com.hackatona.timevermelho.dominio.FormaComunicacao forma,
            @Size(max = 2000) String duvidas,
            boolean compreendeu,
            @Size(max = 120) String autor,
            Long medicoId) {}

    // ---------- eventos e ajustes ----------

    public record NovoEvento(@NotBlank String solicitacaoId, @NotBlank String tipo, String resultado,
                             Double valorNumerico, @Size(max = 2000) String descricao, LocalDate data) {}

    public record Impacto(String solicitacaoId, String nome, Integer posicaoAntes, Integer posicaoDepois,
                          String classeAntes, String classeDepois, String statusAntes, String statusDepois,
                          String mensagem) {}

    public record AjusteRequest(@NotBlank String solicitacaoId, @NotNull TipoAjuste tipo, String classeNova,
                                @NotBlank @Size(min = 10, max = 2000,
                                        message = "a justificativa precisa ter pelo menos 10 caracteres") String justificativa,
                                String autor, Integer posicao) {

        /** Sem posição (todas as decisões menos FIXAR_POSICAO). */
        public AjusteRequest(String solicitacaoId, TipoAjuste tipo, String classeNova, String justificativa, String autor) {
            this(solicitacaoId, tipo, classeNova, justificativa, autor, null);
        }
    }

    // ---------- simulação ----------

    public record EventoHipotetico(@NotBlank String tipo, String resultado, Double valorNumerico, String descricao) {}

    public record SimulacaoRequest(@NotBlank String solicitacaoId, @Min(0) @Max(10) Integer dor, String limitacao,
                                   @Valid List<EventoHipotetico> eventos, @Size(max = 1000) String textoAdicional) {}

    /** observacoes: por que nada mudou (ou o que valeria na fila real), para a simulação não parecer quebrada. */
    public record Simulacao(ItemFila antes, ItemFila depois, List<SinalDto> sinaisNovos,
                            List<Componente> componentesDepois, String mensagem, List<String> observacoes) {}

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
