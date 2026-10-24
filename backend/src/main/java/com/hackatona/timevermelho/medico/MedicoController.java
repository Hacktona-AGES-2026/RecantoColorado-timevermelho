package com.hackatona.timevermelho.medico;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** Médicos, especialidades e a lista de pacientes de cada médico. */
@RestController
@RequestMapping("/api")
@Validated
public class MedicoController {

    private final MedicoService servico;

    public MedicoController(MedicoService servico) {
        this.servico = servico;
    }

    @GetMapping("/especialidades")
    public List<MedicoDtos.EspecialidadeDto> especialidades() {
        return servico.especialidades();
    }

    @GetMapping("/medicos")
    public List<MedicoDtos.MedicoDto> listar() {
        return servico.listar();
    }

    @GetMapping("/medicos/{id}")
    public MedicoDtos.MedicoDto buscar(@PathVariable Long id) {
        return servico.buscar(id);
    }

    @PostMapping("/medicos")
    @ResponseStatus(HttpStatus.CREATED)
    public MedicoDtos.MedicoDto cadastrar(@Valid @RequestBody MedicoDtos.NovoMedico novo) {
        return servico.cadastrar(novo);
    }

    @DeleteMapping("/medicos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Long id) {
        servico.remover(id);
    }

    /** O médico muda a prioridade de um paciente das listas dele (mesmas regras e auditoria do regulador). */
    @PostMapping("/medicos/{id}/ajustes")
    public com.hackatona.timevermelho.api.dto.Dtos.Impacto ajustar(@PathVariable Long id,
                                                                  @Valid @RequestBody MedicoDtos.AjusteMedico req) {
        return servico.ajustar(id, req);
    }

    /** O médico registra um exame ou evento clínico de um paciente das listas dele (fica na auditoria). */
    @PostMapping("/medicos/{id}/eventos")
    public com.hackatona.timevermelho.api.dto.Dtos.Impacto registrarEvento(@PathVariable Long id,
            @Valid @RequestBody com.hackatona.timevermelho.api.dto.Dtos.NovoEvento req) {
        return servico.registrarEvento(id, req);
    }

    /** Auditoria: o que este médico alterou. */
    @GetMapping("/medicos/{id}/ajustes")
    public List<com.hackatona.timevermelho.api.dto.Dtos.AjusteDto> ajustes(@PathVariable Long id) {
        return servico.ajustesDoMedico(id);
    }

    /**
     * Uma lista do médico, do mais grave para o menos grave.
     * grupo=CIRURGICO (padrão): procedimento da especialidade. grupo=POSSIVEL_AVALIACAO: só CID/comorbidade relacionados.
     */
    @GetMapping("/medicos/{id}/fila")
    public MedicoDtos.FilaMedico fila(@PathVariable Long id,
                                     @RequestParam(defaultValue = "CIRURGICO") String grupo,
                                     @RequestParam(required = false) String busca,
                                     @RequestParam(required = false) String classe,
                                     @RequestParam(defaultValue = "0") int pagina,
                                     @RequestParam(defaultValue = "50") int tamanho) {
        MedicoDtos.Grupo g;
        try {
            g = MedicoDtos.Grupo.valueOf(grupo.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new com.hackatona.timevermelho.servico.Erros.RegraNegocio(
                    "grupo deve ser CIRURGICO ou POSSIVEL_AVALIACAO");
        }
        return servico.filaDoMedico(id, g, busca, classe, pagina, tamanho);
    }
}
