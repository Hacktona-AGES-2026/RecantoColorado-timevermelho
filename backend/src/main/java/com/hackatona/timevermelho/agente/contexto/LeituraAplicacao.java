package com.hackatona.timevermelho.agente.contexto;

import java.util.List;
import java.util.Map;

/**
 * Única porta de entrada do agente para os dados da aplicação, e só de leitura: devolve números agregados,
 * nunca nome, identificador, CID, texto de médico nem qualquer dado de um paciente específico (decisão D5, opção a).
 * Não há método para gravar nada: o agente só conversa.
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
