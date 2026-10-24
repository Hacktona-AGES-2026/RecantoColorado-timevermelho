package com.hackatona.timevermelho.agente.categorizacao;

/**
 * Categorias de assunto de um texto. São só organização: nenhuma delas diz gravidade, urgência, prioridade nem
 * posição na fila (isso é decisão clínica e continua com o profissional de saúde e com o motor de priorização).
 */
public enum CategoriaDado {
    DUVIDA_CONSULTA("Dúvida sobre consulta"),
    USO_DA_APLICACAO("Uso da aplicação"),
    SAUDE_GERAL("Saúde em geral"),
    ALIMENTACAO("Alimentação"),
    SINTOMA_RELATADO("Sintoma relatado"),
    ASSUNTO_ADMINISTRATIVO("Assunto administrativo"),
    OUTRO("Outro");

    private final String rotulo;

    CategoriaDado(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
