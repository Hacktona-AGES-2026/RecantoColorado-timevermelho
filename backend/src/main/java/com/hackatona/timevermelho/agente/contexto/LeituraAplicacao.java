package com.hackatona.timevermelho.agente.contexto;

import java.util.List;
import java.util.Map;

/**
 * Única porta de entrada do agente para os dados da aplicação, e só de leitura. Não há método para gravar nada:
 * o agente só conversa.
 *
 * <p>Decisão D5 (revista para o modelo híbrido): a IA recebe só números agregados. A consulta filtrada
 * ({@link #consultar}) também devolve as linhas dos pacientes, mas elas vão direto do código para a tela, sem passar
 * pela IA: a lista é sempre a do dataset e nenhum nome sai para o provedor externo.
 */
public interface LeituraAplicacao {

    ResumoFila resumoFila();

    /** Os indicadores "antes × depois" da tela de mesmo nome. */
    List<Indicador> indicadores();

    /** Quantos pacientes há em cada motivo de reavaliação (só títulos e totais). */
    List<GrupoReavaliacao> reavaliacoes();

    /** Nomes das especialidades que existem na fila (para validar o que alguém pede num relatório). */
    List<String> especialidades();

    /** Números de uma especialidade; {@code null} se ela não existir. */
    ResumoEspecialidade resumoEspecialidade(String especialidade);

    /** Os valores que existem na fila, para validar os filtros pedidos pela IA. Padrão: nada (leitura sem consulta). */
    default Catalogo catalogo() {
        return new Catalogo(List.of(), List.of(), List.of());
    }

    /** Roda uma consulta já validada: números para a IA e linhas para a tela. Padrão: nenhum paciente. */
    default ResultadoConsulta consultar(ConsultaFila consulta) {
        return new ResultadoConsulta(0, "Pacientes encontrados: 0.", List.of());
    }

    record Catalogo(List<String> procedimentos, List<String> especialidades, List<String> municipios) {
    }

    /** Uma linha da tabela, como na tela Fila. Nunca vai para a IA. */
    record LinhaPaciente(Integer posicao, String solicitacaoId, String nome, Integer idade, String sexo,
                         String municipio, String procedimento, String especialidade, String cid, String cidDescricao,
                         boolean oncologico, String classe, double score, int diasEmFila, int prazoDias,
                         String status) {
    }

    /**
     * @param resumo números agregados, em texto, que vão para a IA (sem nome, identificador nem CID individual)
     * @param linhas os pacientes encontrados, até o limite pedido, que vão só para a tela
     */
    record ResultadoConsulta(int total, String resumo, List<LinhaPaciente> linhas) {

        public boolean truncado() {
            return linhas.size() < total;
        }
    }

    record ResumoFila(int naFila, int foraDaFila, Map<String, Long> porClasse, Map<String, Long> porEspecialidade) {
    }

    record Indicador(String nome, String antes, String depois) {
    }

    record GrupoReavaliacao(String titulo, int total) {
    }

    record ResumoEspecialidade(String especialidade, int naFila, Map<String, Long> porClasse, int oncologicos,
                               int comMotivoDeReavaliacao, int saidasSugeridas) {
    }
}
