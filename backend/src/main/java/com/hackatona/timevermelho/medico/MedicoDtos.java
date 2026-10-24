package com.hackatona.timevermelho.medico;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.hackatona.timevermelho.api.dto.Dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Contrato da API de médicos. Espelhado em frontend/src/lib/api.ts. */
public final class MedicoDtos {

    private MedicoDtos() {}

    /** As duas listas de um médico. */
    public enum Grupo {
        /** O procedimento cirúrgico pedido é da especialidade: paciente atribuído ao médico. */
        CIRURGICO,
        /** Sem procedimento da especialidade, mas com CID/comorbidade relacionados: não é atribuído, só sinalizado. */
        POSSIVEL_AVALIACAO
    }

    /**
     * pacientesCirurgicos: pacientes da fila operacional com procedimento desta especialidade.
     * pacientesPossivelAvaliacao: relacionados só por CID secundário, comorbidade, CID principal fora do procedimento etc.
     */
    public record EspecialidadeDto(String codigo, String nome, String descricao, long pacientesCirurgicos,
                                   long pacientesPossivelAvaliacao) {}

    public record MedicoDto(Long id, String nome, String crm, String especialidade, String especialidadeNome,
                            LocalDateTime criadoEm) {}

    public record NovoMedico(
            @NotBlank(message = "informe o nome") @Size(max = 120) String nome,
            @NotBlank(message = "informe o CRM") @Size(max = 30) String crm,
            @NotBlank(message = "escolha a especialidade") String especialidade) {}

    /**
     * Um paciente em uma das listas do médico. item é exatamente o da fila geral (posição, classe, score, justificativa).
     * posicaoNaLista: ordem dentro do grupo (1 = mais grave).
     */
    public record ItemMedico(int posicaoNaLista, Grupo grupo, List<String> motivosEspecialidade, Dtos.ItemFila item) {}

    /**
     * O médico muda a prioridade de um paciente da lista dele. tipo: ALTERAR_CLASSE (subir aplica; descer vira
     * rebaixamento sugerido para o regulador), CONFIRMAR_CLASSE ou SUGERIR_REBAIXAMENTO.
     * informacaoForaDoSistema: o médico sabe de algo que ainda não está registrado (contato direto, exame externo...).
     */
    public record AjusteMedico(
            @NotBlank(message = "informe a solicitação") String solicitacaoId,
            @jakarta.validation.constraints.NotNull(message = "informe o tipo") com.hackatona.timevermelho.dominio.TipoAjuste tipo,
            String classeNova,
            @NotBlank @Size(min = 10, max = 2000, message = "a justificativa precisa ter pelo menos 10 caracteres") String justificativa,
            boolean informacaoForaDoSistema) {}

    /** porClasse conta o grupo pedido inteiro (antes da busca e do filtro de classe). */
    public record FilaMedico(MedicoDto medico, EspecialidadeDto especialidade, Grupo grupo, List<ItemMedico> itens,
                             long total, int pagina, int tamanho, long totalFilaGeral, Map<String, Long> porClasse,
                             Dtos.SnapshotDto snapshot) {}
}
