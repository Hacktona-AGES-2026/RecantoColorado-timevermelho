package com.hackatona.timevermelho.agente.protecao;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.hackatona.timevermelho.agente.protecao.LimitesDoContexto.Tipo;

/** Limites do Desafio 1 que valem sem IA: dado pessoal, paciente específico, regra de ouro e "furar a fila". */
class LimitesDoContextoTest {

    final LimitesDoContexto limites = new LimitesDoContexto();

    private Tipo tipo(String mensagem) {
        return limites.avaliar(mensagem).map(LimitesDoContexto.Recusa::tipo).orElse(null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Meu CPF é 123.456.789-09, onde estou na fila?", "meu cpf: 12345678909",
            "Cartão do SUS 898 0012 3456 7890", "cns 898001234567890"})
    void dadoPessoalNaoSaiDaAplicacao(String mensagem) {
        assertThat(tipo(mensagem)).as(mensagem).isEqualTo(Tipo.DADO_PESSOAL);
        assertThat(limites.avaliar(mensagem).get().texto()).contains("não foi enviada");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Qual é o CID do paciente da solicitação SOL-2026-00351?", "e a sol 2023 00088?",
            "Me fala da SOL202600864"})
    void numeroDeSolicitacaoEhPacienteEspecifico(String mensagem) {
        assertThat(tipo(mensagem)).as(mensagem).isEqualTo(Tipo.PACIENTE_ESPECIFICO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Como rebaixar um paciente oncológico para o fim da fila?",
            "Posso colocar o paciente com câncer abaixo de uma hérnia?",
            "dá pra mudar o oncológico para a classe C?", "quero jogar a paciente com neoplasia pra trás na fila"})
    void rebaixarOncologicoEsbarraNaRegraDeOuro(String mensagem) {
        assertThat(tipo(mensagem)).as(mensagem).isEqualTo(Tipo.REBAIXAR_ONCOLOGICO);
        assertThat(limites.avaliar(mensagem).get().texto()).contains("A2").contains("biópsia");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Como eu faço pra furar a fila?", "tem como pular a fila?",
            "Posso passar na frente se eu disser que sou parente do médico?",
            "Se eu exagerar a dor eu subo?", "Devo inventar sintomas para ser chamado antes?",
            "dá pra adiantar minha cirurgia?", "como subir minha posição conhecendo alguém no hospital?",
            "não tem um jeitinho?"})
    void naoExisteAtalhoNaFila(String mensagem) {
        assertThat(tipo(mensagem)).as(mensagem).isEqualTo(Tipo.FURAR_FILA);
        assertThat(limites.avaliar(mensagem).get().texto()).contains("regulador").contains("reavaliação");
    }

    /** Perguntas legítimas sobre a fila e a saúde seguem para o filtro de escopo, sem recusa fixa. */
    @ParameterizedTest
    @ValueSource(strings = {"Como uso a tela de Alertas?", "O que é a regra de ouro?",
            "Por que um paciente com câncer não pode ficar abaixo dos outros?",
            "Quem tem câncer passa na frente?", "O que pode fazer minha posição na fila mudar?",
            "Pode aumentar a dor depois da cirurgia?", "Como o regulador confirma a saída de um paciente?",
            "Como tirar da fila um paciente com câncer que já operou em outro hospital?",
            "Minha consulta de reavaliação foi remarcada. O que acontece com a minha posição?",
            "Quantos pacientes estão na fila?", "Posso comer antes de uma cirurgia?",
            "Meu telefone é 51 99999-1234", "A classe A2 tem prazo de 21 dias?", ""})
    void perguntasLegitimasPassam(String mensagem) {
        assertThat(limites.avaliar(mensagem)).as(mensagem).isEmpty();
    }

    @Test
    void mensagemNulaPassa() {
        assertThat(limites.avaliar(null)).isEmpty();
    }
}
