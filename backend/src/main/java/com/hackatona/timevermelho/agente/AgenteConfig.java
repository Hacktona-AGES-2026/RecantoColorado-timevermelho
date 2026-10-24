package com.hackatona.timevermelho.agente;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hackatona.timevermelho.agente.chat.AbusoProperties;
import com.hackatona.timevermelho.agente.chat.BloqueioTemporario;
import com.hackatona.timevermelho.agente.chat.ChatService;
import com.hackatona.timevermelho.agente.chat.LimitadorUso;
import com.hackatona.timevermelho.agente.chat.MonitorUso;
import com.hackatona.timevermelho.agente.chat.SessoesChat;
import com.hackatona.timevermelho.agente.categorizacao.CategorizacaoRepository;
import com.hackatona.timevermelho.agente.categorizacao.CategorizacaoService;
import com.hackatona.timevermelho.agente.relatorios.RelatorioRepository;
import com.hackatona.timevermelho.agente.relatorios.RelatorioService;
import com.hackatona.timevermelho.agente.contexto.ContextoDados;
import com.hackatona.timevermelho.agente.contexto.LeituraAplicacao;
import com.hackatona.timevermelho.agente.protecao.FiltroEscopo;
import com.hackatona.timevermelho.agente.protecao.ProtecaoEntrada;
import com.hackatona.timevermelho.agente.provedores.ProvedorIA;
import com.hackatona.timevermelho.agente.provedores.ProvedorOpenAiCompativel;
import com.hackatona.timevermelho.agente.provedores.ProvedoresIA;

@Configuration
@EnableConfigurationProperties({AgenteProperties.class, AbusoProperties.class})
public class AgenteConfig {

    private static final Logger log = LoggerFactory.getLogger(AgenteConfig.class);

    @Bean
    Clock agenteRelogio() {
        return Clock.systemUTC();
    }

    @Bean
    ProvedoresIA provedoresIA(AgenteProperties p, Clock agenteRelogio) {
        List<ProvedorIA> lista = new ArrayList<>();
        int n = 0;
        for (AgenteProperties.Provedor cfg : p.provedores()) {
            n++;
            if (!cfg.configurado()) continue; // sem chave ou sem modelo: ignorado
            if (!urlSegura(cfg.baseUrl())) {
                // Nunca mandar a chave por http puro para fora da máquina.
                log.warn("agente: provedor #{} ignorado: baseUrl precisa ser https (ou http em localhost)", n);
                continue;
            }
            String nome = cfg.nome() == null || cfg.nome().isBlank() ? "provedor-" + n : cfg.nome();
            lista.add(new ProvedorOpenAiCompativel(
                    new AgenteProperties.Provedor(nome, cfg.baseUrl(), cfg.chave(), cfg.modelo(),
                            cfg.modeloModeracao(), cfg.campoLimiteTokens(), cfg.moderacao(), cfg.naoArmazenar(),
                            cfg.esforcoRaciocinio()),
                    p.timeoutSegundos()));
        }
        log.info("agente: {} provedor(es) de IA configurado(s): {}", lista.size(),
                lista.stream().map(ProvedorIA::nome).toList());
        return new ProvedoresIA(lista, Duration.ofMinutes(p.cooldownCreditoMinutos()),
                Duration.ofSeconds(p.cooldownErroSegundos()), p.tentativasLimite(), p.esperaLimiteMs(), agenteRelogio);
    }

    static boolean urlSegura(String url) {
        if (url == null) return false;
        String u = url.trim().toLowerCase();
        return u.startsWith("https://") || u.startsWith("http://localhost") || u.startsWith("http://127.0.0.1");
    }

    @Bean
    PromptsAgente promptsAgente(AgenteProperties p) {
        return PromptsAgente.carregar(p.exigirRegras());
    }

    @Bean
    ProtecaoEntrada protecaoEntrada() {
        return new ProtecaoEntrada();
    }

    @Bean
    FiltroEscopo filtroEscopo(ProvedoresIA provedores, PromptsAgente prompts) {
        return new FiltroEscopo(provedores, prompts);
    }

    @Bean
    SessoesChat sessoesChat(AgenteProperties p, Clock agenteRelogio) {
        AgenteProperties.Limites l = p.limites();
        return new SessoesChat(Duration.ofMinutes(l.sessaoTtlMinutos()), l.maxSessoes(), l.maxHistorico(), agenteRelogio);
    }

    @Bean
    LimitadorUso limitadorUso(AgenteProperties p, Clock agenteRelogio) {
        return new LimitadorUso(p.limites().mensagensPorMinuto(), agenteRelogio);
    }

    @Bean
    ContextoDados contextoDados(LeituraAplicacao leitura) {
        return ContextoDados.padrao(leitura);
    }

    @Bean
    BloqueioTemporario bloqueioTemporario(AbusoProperties a, Clock agenteRelogio) {
        return new BloqueioTemporario(a.recusasParaBloquear(), Duration.ofMinutes(a.janelaMinutos()),
                Duration.ofMinutes(a.bloqueioMinutos()), agenteRelogio);
    }

    @Bean
    MonitorUso monitorUso(AbusoProperties a, Clock agenteRelogio) {
        return new MonitorUso(a.alertaRecusas10Min(), a.alertaTokensHora(), agenteRelogio);
    }

    @Bean
    ChatService chatService(AgenteProperties p, PromptsAgente prompts, ProvedoresIA provedores,
                            ProtecaoEntrada protecao, FiltroEscopo filtro, SessoesChat sessoes, LimitadorUso limitador,
                            ContextoDados contexto, BloqueioTemporario bloqueio, MonitorUso monitor) {
        provedores.aoGastarTokens(monitor::registrarTokens); // todo gasto de tokens (chat, filtro, relatórios) entra no monitor
        return new ChatService(p, prompts, provedores, protecao, filtro, sessoes, limitador, contexto, bloqueio, monitor);
    }

    @Bean
    PromptsTarefas promptsTarefas() {
        return PromptsTarefas.carregar();
    }

    @Bean
    CategorizacaoService categorizacaoService(ProvedoresIA provedores, PromptsAgente prompts, PromptsTarefas tarefas,
                                              ProtecaoEntrada protecao, CategorizacaoRepository repositorio,
                                              AbusoProperties a, ChatService chat, Clock agenteRelogio) {
        return new CategorizacaoService(provedores, prompts, tarefas, protecao, repositorio,
                new LimitadorUso(a.categorizacoesPorMinuto(), agenteRelogio), chat::motivoDesligado, agenteRelogio);
    }

    @Bean
    RelatorioService relatorioService(ProvedoresIA provedores, PromptsAgente prompts, PromptsTarefas tarefas,
                                      LeituraAplicacao leitura, RelatorioRepository repositorio, AbusoProperties a,
                                      ChatService chat, Clock agenteRelogio) {
        return new RelatorioService(provedores, prompts, tarefas, leitura, repositorio,
                new LimitadorUso(a.relatoriosPorMinuto(), agenteRelogio), chat::motivoDesligado, agenteRelogio);
    }

    /** Uma thread virtual por conversa em andamento. */
    @Bean(destroyMethod = "close")
    ExecutorService agenteExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
