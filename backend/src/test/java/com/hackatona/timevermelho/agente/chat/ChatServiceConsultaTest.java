package com.hackatona.timevermelho.agente.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hackatona.timevermelho.agente.AgenteProperties;
import com.hackatona.timevermelho.agente.PromptsAgente;
import com.hackatona.timevermelho.agente.contexto.ConsultaFila;
import com.hackatona.timevermelho.agente.contexto.ContextoDados;
import com.hackatona.timevermelho.agente.contexto.LeituraAplicacao;
import com.hackatona.timevermelho.agente.contexto.PlanejadorConsulta;
import com.hackatona.timevermelho.agente.protecao.FiltroEscopo;
import com.hackatona.timevermelho.agente.protecao.ProtecaoEntrada;
import com.hackatona.timevermelho.agente.provedores.Mensagem;
import com.hackatona.timevermelho.agente.provedores.ProvedorFalso;
import com.hackatona.timevermelho.agente.provedores.ProvedoresIA;
import com.hackatona.timevermelho.agente.provedores.RelogioFalso;

/**
 * Modelo híbrido (D5): a IA escolhe os filtros, o código consulta, a IA recebe só números e a lista de pacientes vai
 * direto para a tela num evento "tabela". O nome de um paciente nunca aparece em nada enviado ao provedor.
 */
class ChatServiceConsultaTest {

    static final String SISTEMA = "Você é o assistente virtual do FilaVIVA, uma plataforma de apoio à decisão para a fila "
            + "de cirurgias eletivas do SUS. Responda sempre em português do Brasil, com linguagem simples.";
    static final String NOME = "Eliane Lopes Ramos";
    static final String PLANO_LISTAR = "{\"consultar\":true,\"listar\":true,\"filtros\":{\"procedimentos\":[\"histerectomia\"]}}";

    RelogioFalso relogio;
    ProvedorFalso provedor;
    String plano;
    List<ConsultaFila> consultasFeitas;
    ChatService chat;

    @BeforeEach
    void montar() {
        relogio = new RelogioFalso();
        provedor = new ProvedorFalso("a");
        plano = PLANO_LISTAR;
        consultasFeitas = new ArrayList<>();
        provedor.aoGerar = m -> m.getFirst().conteudo().contains("CATÁLOGO") ? plano
                : "{\"categoria\":\"DADOS_DA_FILA\",\"motivo\":\"grupo de pacientes\"}";
        provedor.aoStreamar = m -> List.of("A tabela abaixo traz os 2 pacientes ", "de Histerectomia na fila ativa.");

        var props = new AgenteProperties(true, true, 30, 1500, 60, 30, 2, 0, true, List.of(),
                new AgenteProperties.Limites(1000, 20, 1000, 30, 1000, 16384));
        var prompts = PromptsAgente.montar(SISTEMA, "Regra de negócio um: quem decide a classe é o regulador.", "FILTRO", true);
        var roteador = new ProvedoresIA(List.of(provedor), Duration.ofMinutes(60), Duration.ofSeconds(30), 2, 0, relogio);
        LeituraAplicacao leitura = new LeituraAplicacao() {
            @Override
            public ResumoFila resumoFila() {
                return new ResumoFila(919, 22, new LinkedHashMap<>(Map.of("A1", 146L)), Map.of("Ginecologia", 40L));
            }

            @Override
            public List<Indicador> indicadores() {
                return List.of();
            }

            @Override
            public List<GrupoReavaliacao> reavaliacoes() {
                return List.of();
            }

            @Override
            public List<String> especialidades() {
                return List.of("Ginecologia");
            }

            @Override
            public ResumoEspecialidade resumoEspecialidade(String especialidade) {
                return null;
            }

            @Override
            public Catalogo catalogo() {
                return new Catalogo(List.of("Colectomia", "Histerectomia"), List.of("Ginecologia"), List.of("Porto Alegre"));
            }

            @Override
            public ResultadoConsulta consultar(ConsultaFila c) {
                consultasFeitas.add(c);
                var linhas = List.of(
                        new LinhaPaciente(1, "SOL-2026-00489", NOME, 50, "F", "Porto Alegre", "Histerectomia", "Ginecologia",
                                "C54.1", "Neoplasia maligna do endometrio", true, "A1", 86.5, 138, 15, "ATIVA"),
                        new LinhaPaciente(40, "SOL-2025-00010", "Maria Souza Lima", 44, "F", "Canoas", "Histerectomia",
                                "Ginecologia", "D25", "Leiomioma do utero", false, "C", 40.0, 90, 180, "ATIVA"));
                return new ResultadoConsulta(2, "Filtros aplicados: " + c.descricaoParaIA() + ".\nPacientes encontrados: 2.\n"
                        + "Por classe: A1 1; C 1.", c.listar() ? linhas : List.of());
            }
        };
        chat = new ChatService(props, prompts, roteador, new ProtecaoEntrada(), new FiltroEscopo(roteador, prompts),
                new SessoesChat(Duration.ofMinutes(30), 1000, 20, relogio), new LimitadorUso(1000, relogio),
                ContextoDados.padrao(leitura), new BloqueioTemporario(3, Duration.ofMinutes(10), Duration.ofMinutes(5), relogio),
                new MonitorUso(100, 1_000_000, relogio), new PlanejadorConsulta(roteador, leitura, "PLANEJADOR DE TESTE"));
    }

    List<ChatService.Evento> conversar(String sessao, String mensagem) {
        List<ChatService.Evento> recebidos = new ArrayList<>();
        chat.executar(chat.iniciar(sessao, mensagem, "1.1.1.1"), recebidos::add);
        return recebidos;
    }

    /** Tudo o que saiu para o provedor: filtro, planejador, geração e moderação. */
    String tudoQueFoiParaAIA() {
        StringBuilder sb = new StringBuilder();
        provedor.chamadasGerar.forEach(l -> l.forEach(m -> sb.append(m.conteudo()).append('\n')));
        provedor.chamadasStream.forEach(l -> l.forEach(m -> sb.append(m.conteudo()).append('\n')));
        provedor.textosModerados.forEach(t -> sb.append(t).append('\n'));
        return sb.toString();
    }

    @Test
    void pedidoDeListaMostraATabelaDoDatasetEAIARecebeSoOsNumeros() {
        var eventos = conversar(null, "Gere um relatório com todos os pacientes que estão no procedimento Histerectomia");

        var tabela = eventos.stream().filter(e -> e.nome().equals("tabela")).findFirst().orElseThrow();
        Map<?, ?> dados = (Map<?, ?>) tabela.dados();
        assertThat(dados.get("total")).isEqualTo(2);
        assertThat((List<?>) dados.get("linhas")).hasSize(2);
        assertThat(((LeituraAplicacao.LinhaPaciente) ((List<?>) dados.get("linhas")).getFirst()).nome()).isEqualTo(NOME);
        assertThat(dados.get("filtros").toString()).contains("procedimento: Histerectomia");

        // o termo da IA ("histerectomia") virou o nome exato do catálogo antes de consultar
        assertThat(consultasFeitas.getFirst().procedimentos()).containsExactly("Histerectomia");
        assertThat(consultasFeitas.getFirst().listar()).isTrue();

        Mensagem pergunta = provedor.chamadasStream.getFirst().getLast();
        assertThat(pergunta.conteudo()).contains("### Fonte: consulta_fila").contains("Pacientes encontrados: 2")
                .contains("Não escreva a lista nem nomes");
        assertThat(tudoQueFoiParaAIA()).doesNotContain(NOME).doesNotContain("Maria Souza Lima")
                .doesNotContain("SOL-2026-00489");
        // a tabela chega antes do "fim"
        assertThat(eventos.getLast().nome()).isEqualTo("fim");
    }

    @Test
    void contagemNaoMostraTabelaMasLevaOsNumerosDaConsulta() {
        plano = "{\"consultar\":true,\"listar\":false,\"filtros\":{\"procedimentos\":[\"Histerectomia\"]}}";

        var eventos = conversar(null, "Quantos pacientes tem com o procedimento Histerectomia?");

        assertThat(eventos).extracting(ChatService.Evento::nome).doesNotContain("tabela");
        assertThat(provedor.chamadasStream.getFirst().getLast().conteudo()).contains("Pacientes encontrados: 2")
                .doesNotContain("logo abaixo da sua resposta");
    }

    @Test
    void planoInvalidoNaoConsultaNadaESegueComOsDadosGerais() {
        plano = "isto não é JSON";

        var eventos = conversar(null, "quantos pacientes de histerectomia há na fila?");

        assertThat(consultasFeitas).isEmpty();
        assertThat(eventos).extracting(ChatService.Evento::nome).doesNotContain("tabela");
        assertThat(provedor.chamadasStream.getFirst().getLast().conteudo()).contains("### Fonte: resumo_fila");
    }

    @Test
    void oHistoricoLembraDaTabelaSemGuardarNomes() {
        var eventos = conversar(null, "liste os pacientes de histerectomia");
        String sessao = ((Map<?, ?>) eventos.getFirst().dados()).get("sessaoId").toString();

        conversar(sessao, "e quantos são oncológicos?");

        List<Mensagem> segunda = provedor.chamadasStream.get(1);
        assertThat(segunda).extracting(Mensagem::conteudo).anyMatch(c -> c.contains("O sistema mostrou uma tabela com 2 de 2"));
        assertThat(tudoQueFoiParaAIA()).doesNotContain(NOME);
    }

    @Test
    void outraCategoriaNaoChamaOPlanejador() {
        provedor.aoGerar = m -> "{\"categoria\":\"APLICACAO\",\"motivo\":\"ok\"}";

        conversar(null, "como uso a tela de alertas?");

        assertThat(consultasFeitas).isEmpty();
        assertThat(provedor.chamadasGerar).hasSize(1); // só o filtro de escopo
    }

    @Test
    void pacienteEspecificoPeloNumeroDeSolicitacaoChegaAConsultaEMostraALinha() {
        plano = "{\"consultar\":true,\"listar\":true,\"filtros\":{\"solicitacoes\":[\"SOL-2026-00489\"]}}";

        var eventos = conversar(null, "Qual a classe e a posição da solicitação SOL-2026-00489?");

        // antes do conserto, o número de solicitação era recusado sem consultar nada
        assertThat(eventos).extracting(ChatService.Evento::nome).doesNotContain("recusa").contains("tabela");
        assertThat(consultasFeitas.getFirst().solicitacoes()).containsExactly("SOL-2026-00489");
        assertThat(consultasFeitas.getFirst().status()).isEqualTo(ConsultaFila.Status.TODOS);
        // o histórico guarda que houve uma tabela, sem o número pedido
        assertThat(provedor.chamadasStream.getFirst().getLast().conteudo()).contains("### Fonte: consulta_fila")
                .contains("1 número(s) de solicitação informado(s)");
    }
}
