package com.hackatona.timevermelho.medico;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hackatona.timevermelho.api.dto.Conversor;
import com.hackatona.timevermelho.importacao.Normalizacao;
import com.hackatona.timevermelho.motor.ClasseSwalis;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.servico.Erros;
import com.hackatona.timevermelho.servico.FilaService;

/**
 * Cadastro de médicos e a lista de cada um.
 * A lista do médico é a fila operacional filtrada pela especialidade, na MESMA ordem (classe → score → prazo):
 * um recorte de uma fila que respeita a regra de ouro também a respeita, e ninguém é reordenado.
 */
@Service
public class MedicoService {

    private final MedicoRepository medicos;
    private final CatalogoEspecialidades catalogo;
    private final FilaService fila;
    private final com.hackatona.timevermelho.servico.DecisaoService decisoes;
    private final com.hackatona.timevermelho.dominio.AjusteReguladorRepository ajustes;

    public MedicoService(MedicoRepository medicos, CatalogoEspecialidades catalogo, FilaService fila,
                         com.hackatona.timevermelho.servico.DecisaoService decisoes,
                         com.hackatona.timevermelho.dominio.AjusteReguladorRepository ajustes) {
        this.medicos = medicos;
        this.catalogo = catalogo;
        this.fila = fila;
        this.decisoes = decisoes;
        this.ajustes = ajustes;
    }

    public List<MedicoDtos.EspecialidadeDto> especialidades() {
        List<ResultadoPaciente> ativos = fila.atual().ativos();
        return catalogo.todas().stream().map(e -> dto(e, ativos)).toList();
    }

    static MedicoDtos.Grupo grupo(Especialidade.Correspondencia c) {
        return c.cirurgica() ? MedicoDtos.Grupo.CIRURGICO : MedicoDtos.Grupo.POSSIVEL_AVALIACAO;
    }

    public List<MedicoDtos.MedicoDto> listar() {
        return medicos.findAllByOrderByNomeAsc().stream().map(this::dto).toList();
    }

    public MedicoDtos.MedicoDto buscar(Long id) {
        return dto(medico(id));
    }

    @Transactional
    public MedicoDtos.MedicoDto cadastrar(MedicoDtos.NovoMedico novo) {
        Especialidade e = catalogo.buscar(novo.especialidade())
                .orElseThrow(() -> new Erros.RegraNegocio("Especialidade desconhecida: " + novo.especialidade()));
        String crm = novo.crm().trim().toUpperCase(Locale.ROOT);
        if (medicos.existsByCrmIgnoreCase(crm)) throw new Erros.Conflito("Já existe um médico com o CRM " + crm);
        Medico m = new Medico();
        m.setNome(novo.nome().trim());
        m.setCrm(crm);
        m.setEspecialidade(e.codigo());
        m.setCriadoEm(LocalDateTime.now());
        return dto(medicos.save(m));
    }

    @Transactional
    public void remover(Long id) {
        medicos.delete(medico(id));
    }

    /**
     * Uma das listas do médico. CIRURGICO: procedimento da especialidade (atribuídos a ele).
     * POSSIVEL_AVALIACAO: relacionados só por CID/comorbidade/idade, sinalizados à parte e nunca misturados aos atribuídos.
     */
    public MedicoDtos.FilaMedico filaDoMedico(Long id, MedicoDtos.Grupo grupo, String busca, String classe, int pagina,
                                              int tamanho) {
        Medico m = medico(id);
        Especialidade e = especialidade(m);
        var snap = fila.atual();
        MedicoDtos.Grupo pedido = grupo == null ? MedicoDtos.Grupo.CIRURGICO : grupo;

        // a fila ativa já vem ordenada do mais grave para o menos grave; o item é o da fila geral, sem recálculo
        List<MedicoDtos.ItemMedico> lista = new ArrayList<>();
        Map<String, Long> porClasse = new LinkedHashMap<>();
        for (ClasseSwalis c : ClasseSwalis.values()) porClasse.put(c.name(), 0L);
        for (ResultadoPaciente r : snap.ativos()) {
            Especialidade.Correspondencia corr = e.avaliar(r.getCaso(), r.oncologico());
            if (!corr.existe() || grupo(corr) != pedido) continue;
            porClasse.merge(r.classe().name(), 1L, Long::sum);
            lista.add(new MedicoDtos.ItemMedico(lista.size() + 1, pedido, corr.motivos(), Conversor.item(r)));
        }

        var filtrada = lista.stream();
        if (classe != null && !classe.isBlank()) filtrada = filtrada.filter(i -> i.item().classe().equalsIgnoreCase(classe));
        if (busca != null && !busca.isBlank()) {
            String b = Normalizacao.semAcento(busca.trim()).toLowerCase(Locale.ROOT);
            filtrada = filtrada.filter(i -> contem(i.item().nome(), b) || contem(i.item().solicitacaoId(), b)
                    || contem(i.item().procedimento(), b) || contem(i.item().cid(), b));
        }
        List<MedicoDtos.ItemMedico> filtrados = filtrada.toList();
        int tam = Math.max(1, Math.min(tamanho, 500));
        int pag = Math.max(0, pagina);
        long totalGeral = snap.ativos().size();
        return new MedicoDtos.FilaMedico(dto(m), dto(e, snap.ativos()), pedido,
                filtrados.stream().skip((long) pag * tam).limit(tam).toList(), filtrados.size(), pag, tam, totalGeral,
                porClasse, Conversor.snapshot(snap.info()));
    }

    /** O que um médico pode fazer na prioridade. Confirmar rebaixamento e decidir saída continuam com o regulador. */
    private static final java.util.Set<com.hackatona.timevermelho.dominio.TipoAjuste> PERMITIDOS_AO_MEDICO = java.util.EnumSet.of(
            com.hackatona.timevermelho.dominio.TipoAjuste.ALTERAR_CLASSE,
            com.hackatona.timevermelho.dominio.TipoAjuste.CONFIRMAR_CLASSE,
            com.hackatona.timevermelho.dominio.TipoAjuste.SUGERIR_REBAIXAMENTO,
            com.hackatona.timevermelho.dominio.TipoAjuste.FIXAR_POSICAO,
            com.hackatona.timevermelho.dominio.TipoAjuste.LIBERAR_POSICAO);

    /**
     * O médico muda a prioridade de um paciente das listas dele (cirúrgicos ou possível avaliação), por exemplo
     * quando sabe de algo que ainda não está no sistema. Passa pelo mesmo caminho das decisões do regulador: a fila é
     * recalculada pelo motor (sem atalho na ordenação), subir a classe vale na hora, descer vira rebaixamento sugerido
     * para o regulador confirmar, oncológico nunca perde classe e a regra de ouro continua valendo. Fica na auditoria
     * com o nome, o CRM e o id do médico.
     */
    public com.hackatona.timevermelho.api.dto.Dtos.Impacto ajustar(Long medicoId, MedicoDtos.AjusteMedico req) {
        Medico m = medico(medicoId);
        if (!PERMITIDOS_AO_MEDICO.contains(req.tipo())) {
            throw new Erros.RegraNegocio("O médico pode mudar a classe, confirmá-la, sugerir rebaixamento ou escolher a "
                    + "posição na fila. Confirmar "
                    + "rebaixamento e decidir saída da fila continuam com o regulador.");
        }
        Especialidade e = especialidade(m);
        String id = pacienteDasListas(e, req.solicitacaoId());
        String autor = m.getNome() + " (" + m.getCrm() + ", " + e.nome() + ")";
        return decisoes.ajustar(new com.hackatona.timevermelho.api.dto.Dtos.AjusteRequest(id, req.tipo(), req.classeNova(),
                        req.justificativa(), autor, req.posicao()),
                new com.hackatona.timevermelho.servico.DecisaoService.Origem(m.getId(), req.informacaoForaDoSistema()));
    }

    /**
     * O médico registra um exame ou evento clínico de um paciente das listas dele. A fila é recalculada pelo motor,
     * como quando o regulador registra, e fica na auditoria com o nome, o CRM e o id do médico.
     */
    public com.hackatona.timevermelho.api.dto.Dtos.Impacto registrarEvento(Long medicoId,
                                                                          com.hackatona.timevermelho.api.dto.Dtos.NovoEvento req) {
        Medico m = medico(medicoId);
        Especialidade e = especialidade(m);
        String id = pacienteDasListas(e, req.solicitacaoId());
        String autor = m.getNome() + " (" + m.getCrm() + ", " + e.nome() + ")";
        return decisoes.registrarEvento(new com.hackatona.timevermelho.api.dto.Dtos.NovoEvento(id, req.tipo(), req.resultado(),
                        req.valorNumerico(), req.descricao(), req.data(), autor),
                new com.hackatona.timevermelho.servico.DecisaoService.Origem(m.getId(), false));
    }

    /** O paciente precisa estar na fila e nas listas da especialidade do médico (cirúrgicos ou possível avaliação). */
    private String pacienteDasListas(Especialidade e, String solicitacaoId) {
        String id = decisoes.resolverId(solicitacaoId);
        ResultadoPaciente r = fila.atual().get(id);
        if (r == null || !r.getStatus().naFila()) throw new Erros.NaoEncontrado("Solicitação " + solicitacaoId + " não está na fila");
        if (!e.avaliar(r.getCaso(), r.oncologico()).existe()) {
            throw new Erros.RegraNegocio("Este paciente não está nas listas de " + e.nome() + " (procedimento de "
                    + r.getCaso().especialidade() + "). Peça ao regulador ou a um médico da especialidade.");
        }
        return id;
    }

    /** Auditoria: tudo o que o médico alterou, do mais recente para o mais antigo. */
    public List<com.hackatona.timevermelho.api.dto.Dtos.AjusteDto> ajustesDoMedico(Long medicoId) {
        medico(medicoId);
        return ajustes.findByMedicoIdOrderByCriadoEmDescIdDesc(medicoId).stream()
                .map(Conversor::ajuste).toList();
    }

    /** Cria os médicos de exemplo da demo. Pula o CRM que já existir (a API já aceita cadastros durante a subida). */
    @Transactional
    public void semearExemplos() {
        String[][] exemplos = {
                {"Dra. Helena Prado", "CRM-RS 10001", "CARDIOLOGIA"},
                {"Dr. Marcos Teixeira", "CRM-RS 10002", "ONCOLOGIA"},
                {"Dra. Juliana Ribas", "CRM-RS 10003", "ORTOPEDIA"},
                {"Dr. Rafael Moura", "CRM-RS 10004", "PEDIATRIA"},
        };
        for (String[] x : exemplos) {
            if (!medicos.existsByCrmIgnoreCase(x[1])) cadastrar(new MedicoDtos.NovoMedico(x[0], x[1], x[2]));
        }
    }

    private Medico medico(Long id) {
        return medicos.findById(id).orElseThrow(() -> new Erros.NaoEncontrado("Médico " + id + " não encontrado"));
    }

    private Especialidade especialidade(Medico m) {
        return catalogo.buscar(m.getEspecialidade())
                .orElseThrow(() -> new Erros.RegraNegocio("Especialidade " + m.getEspecialidade() + " não existe mais"));
    }

    private MedicoDtos.MedicoDto dto(Medico m) {
        String nome = catalogo.buscar(m.getEspecialidade()).map(Especialidade::nome).orElse(m.getEspecialidade());
        return new MedicoDtos.MedicoDto(m.getId(), m.getNome(), m.getCrm(), m.getEspecialidade(), nome, m.getCriadoEm());
    }

    private static MedicoDtos.EspecialidadeDto dto(Especialidade e, List<ResultadoPaciente> ativos) {
        long cirurgicos = 0, avaliacao = 0;
        for (ResultadoPaciente r : ativos) {
            Especialidade.Correspondencia c = e.avaliar(r.getCaso(), r.oncologico());
            if (!c.existe()) continue;
            if (c.cirurgica()) cirurgicos++;
            else avaliacao++;
        }
        return new MedicoDtos.EspecialidadeDto(e.codigo(), e.nome(), e.descricao(), cirurgicos, avaliacao);
    }

    private static boolean contem(String campo, String busca) {
        return campo != null && Normalizacao.semAcento(campo).toLowerCase(Locale.ROOT).contains(busca);
    }
}
