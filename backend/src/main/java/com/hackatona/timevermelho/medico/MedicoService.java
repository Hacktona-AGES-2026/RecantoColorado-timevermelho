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

    public MedicoService(MedicoRepository medicos, CatalogoEspecialidades catalogo, FilaService fila) {
        this.medicos = medicos;
        this.catalogo = catalogo;
        this.fila = fila;
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
