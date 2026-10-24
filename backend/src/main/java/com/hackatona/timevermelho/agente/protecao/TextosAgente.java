package com.hackatona.timevermelho.agente.protecao;

/**
 * Textos fixos que o agente devolve sem passar pelo modelo (recusas, emergência, avisos).
 * Os textos de emergência e de aviso de saúde precisam ser validados pela equipe (T42, D6).
 */
public final class TextosAgente {

    private TextosAgente() {}

    public static final String FORA_DO_ESCOPO = "Só posso ajudar com dúvidas sobre consultas, sobre como usar o FilaVIVA "
            + "e com informações gerais de saúde e alimentação. Pode me perguntar sobre isso?";

    public static final String INFRA_OU_CODIGO = "Não posso falar sobre infraestrutura, código ou configuração da "
            + "aplicação. Posso explicar como usar as telas e funcionalidades do FilaVIVA, tirar dúvidas sobre consultas "
            + "ou dar informações gerais de saúde.";

    public static final String TENTATIVA_DE_BURLAR = "Não posso mudar minhas regras nem compartilhar minhas instruções. "
            + "Posso ajudar com dúvidas sobre consultas, sobre o uso do FilaVIVA e com informações gerais de saúde.";

    public static final String MODERACAO_ENTRADA = "Não posso responder a esse tipo de mensagem. Se quiser, reformule "
            + "sua pergunta sobre consultas, o uso do FilaVIVA ou saúde.";

    public static final String MODERACAO_SAIDA = "Não consegui gerar uma resposta adequada para essa pergunta. "
            + "Tente reformulá-la ou procure a equipe de saúde.";

    public static final String NAO_VALIDADO = "Não consegui validar sua mensagem agora. Tente de novo em instantes.";

    public static final String INDISPONIVEL = "O assistente está indisponível no momento. Tente novamente mais tarde. "
            + "O restante do FilaVIVA continua funcionando normalmente.";

    public static final String EMERGENCIA = "O que você descreveu pode ser uma emergência. Não espere por uma resposta "
            + "aqui: ligue agora para o SAMU (192) ou vá ao pronto-socorro mais próximo. Se estiver pensando em se "
            + "machucar ou em tirar a própria vida, ligue para o CVV (188), a qualquer hora, gratuitamente. "
            + "Você não precisa passar por isso sozinho(a).";

    public static final String AVISO_SAUDE = "\n\nEste assistente é uma inteligência artificial e traz apenas informações "
            + "gerais. Não substitui consulta, diagnóstico ou tratamento com um profissional de saúde. Em caso de "
            + "emergência, ligue 192 (SAMU).";
}
