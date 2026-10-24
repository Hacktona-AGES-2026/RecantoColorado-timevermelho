package com.hackatona.timevermelho.medico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hackatona.timevermelho.api.dto.Conversor;
import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.medico.MedicoDtos.Grupo;
import com.hackatona.timevermelho.motor.GuardRegraDeOuro;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.servico.DecisaoService;
import com.hackatona.timevermelho.servico.Erros;
import com.hackatona.timevermelho.servico.FilaService;

/** Cadastro de médicos e listas por especialidade, sobre o dataset inteiro (H2 em memória). */
@SpringBootTest
class MedicoServiceTest {

    @Autowired MedicoService medicos;
    @Autowired FilaService fila;
    @Autowired DecisaoService decisao;
    @Autowired CatalogoEspecialidades catalogo;

    @AfterEach
    void voltarAoDataset() {
        decisao.resetar();
    }

    private Long id(String especialidade) {
        return medicos.listar().stream().filter(m -> m.especialidade().equals(especialidade)).findFirst()
                .map(MedicoDtos.MedicoDto::id)
                .orElseGet(() -> medicos.cadastrar(new MedicoDtos.NovoMedico("Teste " + especialidade,
                        "CRM-T " + especialidade, especialidade)).id());
    }

    private List<MedicoDtos.ItemMedico> lista(String especialidade, Grupo grupo) {
        return medicos.filaDoMedico(id(especialidade), grupo, null, null, 0, 500).itens();
    }

    private static Set<String> ids(List<MedicoDtos.ItemMedico> l) {
        Set<String> s = new HashSet<>();
        l.forEach(i -> s.add(i.item().solicitacaoId()));
        return s;
    }

    @Test
    void sobeComMedicosDeExemplo() {
        assertThat(medicos.listar()).extracting(MedicoDtos.MedicoDto::especialidade)
                .contains("CARDIOLOGIA", "ONCOLOGIA", "ORTOPEDIA", "PEDIATRIA");
    }

    @Test
    void listaCirurgicaTemExatamenteOsProcedimentosDaEspecialidade() {
        for (Especialidade e : catalogo.todas()) {
            Set<String> esperado = new HashSet<>();
            fila.atual().ativos().stream()
                    .filter(r -> e.procedimentoDaEspecialidade(r.getCaso()))
                    .forEach(r -> esperado.add(r.getCaso().solicitacaoId()));
            List<MedicoDtos.ItemMedico> cirurgica = lista(e.codigo(), Grupo.CIRURGICO);
            assertThat(ids(cirurgica)).as(e.codigo()).isEqualTo(esperado);
            assertThat(cirurgica).allSatisfy(i -> assertThat(i.grupo()).isEqualTo(Grupo.CIRURGICO));
        }
        // cardiologia: os 23 de Cirurgia Cardiaca e ninguém de outra cirurgia
        List<MedicoDtos.ItemMedico> cardio = lista("CARDIOLOGIA", Grupo.CIRURGICO);
        assertThat(cardio).isNotEmpty()
                .allSatisfy(i -> assertThat(i.item().especialidade()).isEqualTo("Cirurgia Cardiaca"));
    }

    @Test
    void possivelAvaliacaoFicaSeparadaENuncaTemProcedimentoDaEspecialidade() {
        List<MedicoDtos.ItemMedico> cirurgica = lista("CARDIOLOGIA", Grupo.CIRURGICO);
        List<MedicoDtos.ItemMedico> avaliacao = lista("CARDIOLOGIA", Grupo.POSSIVEL_AVALIACAO);
        assertThat(avaliacao).isNotEmpty()
                .allSatisfy(i -> {
                    assertThat(i.grupo()).isEqualTo(Grupo.POSSIVEL_AVALIACAO);
                    assertThat(i.item().especialidade()).isNotEqualTo("Cirurgia Cardiaca");
                    assertThat(i.motivosEspecialidade()).isNotEmpty()
                            .noneMatch(m -> m.startsWith("Procedimento de"));
                });
        // pacientes com insuficiência cardíaca em outras cirurgias aparecem aqui, não na lista cirúrgica
        assertThat(avaliacao).anyMatch(i -> i.motivosEspecialidade().stream().anyMatch(m -> m.startsWith("Comorbidade")));
        assertThat(ids(cirurgica)).doesNotContainAnyElementsOf(ids(avaliacao));
        var esp = medicos.filaDoMedico(id("CARDIOLOGIA"), Grupo.CIRURGICO, null, null, 0, 1).especialidade();
        assertThat(esp.pacientesCirurgicos()).isEqualTo(cirurgica.size());
        assertThat(esp.pacientesPossivelAvaliacao()).isEqualTo(avaliacao.size());
    }

    @Test
    void itensSaoIdenticosAosDaFilaGeral() {
        var porId = fila.atual().porId();
        for (String esp : List.of("CARDIOLOGIA", "ONCOLOGIA", "ENDOCRINOLOGIA")) {
            for (Grupo g : Grupo.values()) {
                for (MedicoDtos.ItemMedico i : lista(esp, g)) {
                    ResultadoPaciente r = porId.get(i.item().solicitacaoId());
                    // mesma posição, classe, score e justificativa da fila geral
                    assertThat(i.item()).isEqualTo(Conversor.item(r));
                    assertThat(i.item().posicao()).isEqualTo(r.getPosicao());
                }
            }
        }
    }

    @Test
    void cadaListaSegueAOrdemDaFilaGeralERespeitaARegraDeOuro() {
        var porId = fila.atual().porId();
        for (Especialidade e : catalogo.todas()) {
            for (Grupo g : Grupo.values()) {
                List<MedicoDtos.ItemMedico> l = lista(e.codigo(), g);
                for (int i = 0; i < l.size(); i++) {
                    assertThat(l.get(i).posicaoNaLista()).isEqualTo(i + 1);
                    if (i > 0) assertThat(l.get(i).item().posicao()).isGreaterThan(l.get(i - 1).item().posicao());
                }
                List<ResultadoPaciente> recorte = l.stream().map(i -> porId.get(i.item().solicitacaoId())).toList();
                assertThat(GuardRegraDeOuro.respeitada(recorte)).as(e.codigo() + " " + g).isTrue();
            }
        }
    }

    @Test
    void oncologistaVeTodosOsOncologicosEntreAsDuasListas() {
        long oncologicos = fila.atual().ativos().stream().filter(ResultadoPaciente::oncologico).count();
        List<MedicoDtos.ItemMedico> todas = new java.util.ArrayList<>(lista("ONCOLOGIA", Grupo.CIRURGICO));
        todas.addAll(lista("ONCOLOGIA", Grupo.POSSIVEL_AVALIACAO));
        assertThat(todas.stream().filter(i -> i.item().oncologico()).count()).isEqualTo(oncologicos);
        assertThat(lista("ONCOLOGIA", Grupo.CIRURGICO))
                .allSatisfy(i -> assertThat(i.item().especialidade()).isEqualTo("Cirurgia Oncologica"));
    }

    @Test
    void pediatraNaoTemPacientesNesteDataset() {
        // todos os pacientes do dataset têm 18 anos ou mais
        assertThat(lista("PEDIATRIA", Grupo.CIRURGICO)).isEmpty();
        assertThat(lista("PEDIATRIA", Grupo.POSSIVEL_AVALIACAO)).isEmpty();
    }

    @Test
    void novoEventoReordenaTambemAListaDoMedico() {
        List<MedicoDtos.ItemMedico> antes = lista("CARDIOLOGIA", Grupo.CIRURGICO);
        MedicoDtos.ItemMedico ultimo = antes.get(antes.size() - 1);
        decisao.registrarEvento(new Dtos.NovoEvento(ultimo.item().solicitacaoId(), "internacao", "alta", 5.0,
                "Internação por descompensação", null));
        List<MedicoDtos.ItemMedico> depois = lista("CARDIOLOGIA", Grupo.CIRURGICO);
        MedicoDtos.ItemMedico mesmo = depois.stream()
                .filter(i -> i.item().solicitacaoId().equals(ultimo.item().solicitacaoId())).findFirst().orElseThrow();
        assertThat(mesmo.posicaoNaLista()).isLessThan(ultimo.posicaoNaLista());
        assertThat(mesmo.item().posicao()).isEqualTo(fila.atual().get(mesmo.item().solicitacaoId()).getPosicao());
    }

    @Test
    void cadastroValidaCrmEEspecialidade() {
        var m = medicos.cadastrar(new MedicoDtos.NovoMedico("Dra. Teste", "crm-rs 99999", "OFTALMOLOGIA"));
        assertThat(m.crm()).isEqualTo("CRM-RS 99999");
        assertThat(m.especialidadeNome()).isEqualTo("Oftalmologia");
        assertThatThrownBy(() -> medicos.cadastrar(new MedicoDtos.NovoMedico("Outro", "CRM-RS 99999", "UROLOGIA")))
                .isInstanceOf(Erros.Conflito.class);
        assertThatThrownBy(() -> medicos.cadastrar(new MedicoDtos.NovoMedico("Outro", "CRM-RS 88888", "ASTROLOGIA")))
                .isInstanceOf(Erros.RegraNegocio.class);
        medicos.remover(m.id());
        assertThatThrownBy(() -> medicos.buscar(m.id())).isInstanceOf(Erros.NaoEncontrado.class);
    }

    @Test
    void buscaEFiltroDeClasseNaListaDoMedico() {
        Long id = id("CARDIOLOGIA");
        var tudo = medicos.filaDoMedico(id, Grupo.POSSIVEL_AVALIACAO, null, null, 0, 500);
        String classe = tudo.itens().get(0).item().classe();
        var soClasse = medicos.filaDoMedico(id, Grupo.POSSIVEL_AVALIACAO, null, classe, 0, 500);
        assertThat(soClasse.itens()).allSatisfy(i -> assertThat(i.item().classe()).isEqualTo(classe));
        assertThat(soClasse.total()).isEqualTo(tudo.porClasse().get(classe));
        String nome = tudo.itens().get(0).item().nome();
        assertThat(medicos.filaDoMedico(id, Grupo.POSSIVEL_AVALIACAO, nome, null, 0, 500).itens())
                .anyMatch(i -> i.item().nome().equals(nome));
    }
}
