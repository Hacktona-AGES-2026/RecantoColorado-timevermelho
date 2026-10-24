package com.hackatona.timevermelho.agente;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração do agente de IA (prefixo {@code agente.*}). Chaves de API só entram por variável de ambiente
 * (veja backend/.env.example); nunca são gravadas em arquivo versionado nem aparecem em logs e respostas.
 */
@ConfigurationProperties("agente")
public record AgenteProperties(
        @DefaultValue("true") boolean ativo,
        /** Sem regras de negócio preenchidas o agente fica desligado (T34). */
        @DefaultValue("true") boolean exigirRegras,
        @DefaultValue("30") int timeoutSegundos,
        @DefaultValue("1500") int maxTokens,
        /** Quanto tempo um provedor sem crédito (ou com chave recusada) fica fora da rotação. */
        @DefaultValue("60") int cooldownCreditoMinutos,
        /** Quanto tempo um provedor com erro de servidor ou timeout fica fora da rotação. */
        @DefaultValue("30") int cooldownErroSegundos,
        /** Tentativas no mesmo provedor quando o limite de requisições é momentâneo. */
        @DefaultValue("2") int tentativasLimite,
        @DefaultValue("1000") long esperaLimiteMs,
        @DefaultValue("true") boolean moderacaoAtiva,
        /** Em ordem de preferência. Provedores sem chave são ignorados. */
        List<Provedor> provedores,
        @DefaultValue Limites limites) {

    public AgenteProperties {
        provedores = provedores == null ? List.of() : List.copyOf(provedores);
    }

    /** Qualquer API compatível com o formato "chat/completions" da OpenAI (OpenAI, Azure OpenAI, etc.). */
    public record Provedor(
            String nome,
            @DefaultValue("https://api.openai.com/v1") String baseUrl,
            String chave,
            String modelo,
            @DefaultValue("omni-moderation-latest") String modeloModeracao,
            /** Modelos novos da OpenAI usam max_completion_tokens; APIs mais antigas usam max_tokens. */
            @DefaultValue("max_completion_tokens") String campoLimiteTokens,
            @DefaultValue("true") boolean moderacao) {

        public boolean configurado() {
            return chave != null && !chave.isBlank() && modelo != null && !modelo.isBlank();
        }
    }

    public record Limites(
            @DefaultValue("1000") int maxMensagem,
            @DefaultValue("20") int maxHistorico,
            @DefaultValue("10") int mensagensPorMinuto,
            @DefaultValue("30") int sessaoTtlMinutos,
            @DefaultValue("1000") int maxSessoes,
            @DefaultValue("16384") int maxCorpoBytes) {
    }
}
