package com.hackatona.timevermelho.motor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.hackatona.timevermelho.motor.texto.RegrasClinicas;

class MotorPriorizacaoTest {

    static final LocalDate REF = LocalDate.of(2026, 10, 23);
    final MotorPriorizacao motor = new MotorPriorizacao(RegrasClinicas.carregarPadrao(), REF);

    static Caso.CasoBuilder caso(String id, String cid) {
        return Caso.builder().solicitacaoId(id).pacienteId("PAC-" + id).nome("Paciente " + id)
                .cid(cid).cidFormatado(cid).procedimento("Proc").riscoCirurgico(2).asa(2)
                .dor(1).limitacao("nenhuma").justificativa("Paciente com indicacao cirurgica eletiva, estavel.")
                .dataEntrada(REF.minusDays(100)).diasEmFila(100).prioridadeManual("media")
                .flagOncologico(cid.startsWith("C") ? "sim" : "nao").flagPiora("nao").eventos(List.of());
    }

    static Evento evento(String sol, String tipo, String resultado, int diasAtras) {
        return new Evento("EVT-" + sol + tipo, sol, REF.minusDays(diasAtras), tipo, resultado, null, "laudo", "lab", false);
    }

    ResultadoPaciente avaliar(Caso c) {
        return motor.avaliar(c, Pesos.PADRAO, DecisaoRegulador.NENHUMA);
    }

    @Test
    void oncologicoComPrioridadeBaixaFicaNoMinimoA2() {
        var r = avaliar(caso("1", "C504").prioridadeManual("baixa").build());
        assertThat(r.classe()).isEqualTo(ClasseSwalis.A2);
    }

    @Test
    void biopsiaPositivaComCidBenignoViraA2() {
        var c = caso("2", "D440").eventos(List.of(evento("2", "biopsia", "positivo", 40))).build();
        var r = avaliar(c);
        assertThat(r.classe()).isEqualTo(ClasseSwalis.A2);
        assertThat(r.getJustificativa()).isNull(); // justificativa só é gerada em priorizar()
        assertThat(r.oncologico()).isTrue();
    }

    @Test
    void oncologicoComProgressaoViraA1() {
        var c = caso("3", "C189").eventos(List.of(evento("3", "exame_imagem", "progressao", 30))).build();
        assertThat(avaliar(c).classe()).isEqualTo(ClasseSwalis.A1);
    }

    @Test
    void negacaoNoTextoNaoDisparaAlarme() {
        var c = caso("4", "K409").justificativa("Hernia inguinal redutivel. Sem sinais de encarceramento.").build();
        var r = avaliar(c);
        assertThat(r.temSinal(TipoSinal.ALARME_GRAVE)).isFalse();
        assertThat(r.classe()).isEqualTo(ClasseSwalis.D);
    }

    @Test
    void necroseAssepticaNaoEhSepse() {
        var c = caso("11", "M169").justificativa("Necrose asseptica de cabeca femoral direita, dor intensa.").build();
        assertThat(avaliar(c).temSinal(TipoSinal.ALARME_GRAVE)).isFalse();
        var sepse = caso("12", "N201").justificativa("ITU de repeticao com sepse urinaria ha 1 mes.").build();
        assertThat(avaliar(sepse).classe()).isEqualTo(ClasseSwalis.A1);
    }

    @Test
    void doencaCoronariaGraveNaoFicaEmD() {
        // dor baixa e limitação leve, mas o texto descreve doença grave
        var tronco = caso("13", "I251").justificativa("DOENCA ARTERIAL CORONARIANA MULTIARTERIAL. ANGINA ESTAVEL CCS II. CATE: TRONCO 80%.").build();
        assertThat(avaliar(tronco).classe()).isEqualTo(ClasseSwalis.A1);
        var tri = caso("14", "I251").justificativa("DAC triarterial, angina CCS III, FE 36%. Cateterismo: lesoes >70% em DA, CX e CD.").build();
        assertThat(avaliar(tri).classe()).isEqualTo(ClasseSwalis.B);
        var feNormal = caso("15", "I251").justificativa("Cardiopatia isquemica estavel, FE 61%, angina CCS I com medicacao.").build();
        assertThat(avaliar(feNormal).classe()).isEqualTo(ClasseSwalis.D);
    }

    @Test
    void alarmeNoTextoViraA1() {
        var c = caso("5", "K409").justificativa("Hernia inguinal volumosa com episodio de encarceramento ha 2 semanas.").build();
        assertThat(avaliar(c).classe()).isEqualTo(ClasseSwalis.A1);
    }

    @Test
    void dadoFaltandoNaoRebaixaParaD() {
        var c = caso("6", "H251").dor(null).limitacao(null).build();
        var r = avaliar(c);
        assertThat(r.classe()).isEqualTo(ClasseSwalis.C);
        assertThat(r.temSinal(TipoSinal.DADO_INCOMPLETO)).isTrue();
    }

    @Test
    void psRecenteViraB() {
        var c = caso("7", "K801").eventos(List.of(evento("7", "pronto_socorro", "atendido_liberado", 20))).build();
        assertThat(avaliar(c).classe()).isEqualTo(ClasseSwalis.B);
    }

    @Test
    void reguladorNaoConsegueRebaixarOncologicoAbaixoDeA2() {
        var c = caso("8", "C509").build();
        var r = motor.avaliar(c, Pesos.PADRAO, new DecisaoRegulador(ClasseSwalis.D, "teste", null, null));
        assertThat(r.classe()).isEqualTo(ClasseSwalis.A2);
    }

    @Test
    void saidaSugeridaContinuaNaFilaAteORegularConfirmar() {
        String desistiu = "PCT DESISTIU DO PROCEDIMENTO. SOLICITA RETIRADA DA FILA.";
        var naoOnco = caso("9", "K409").observacoes(desistiu).build();
        var onco = caso("10", "C509").observacoes(desistiu).build();
        var fila = motor.priorizar(List.of(naoOnco, onco), Pesos.PADRAO, Map.of());
        // ninguém sai sem decisão humana: os dois têm posição e aviso de reavaliação
        assertThat(fila).allSatisfy(r -> {
            assertThat(r.getStatus()).isEqualTo(StatusFila.SAIDA_SUGERIDA);
            assertThat(r.getPosicao()).isNotNull();
            assertThat(r.getJustificativa()).contains("saída sugerida aguardando o regulador");
            assertThat(r.getReavaliacoes()).extracting(Reavaliacao::tipo).contains(TipoReavaliacao.CONFIRMAR_SAIDA);
        });
        // confirmou: sai da fila; recusou: volta a ser paciente comum da fila
        var confirmada = motor.priorizar(List.of(naoOnco), Pesos.PADRAO,
                Map.of("9", new DecisaoRegulador(null, null, true, "confirmado por telefone")));
        assertThat(confirmada.getFirst().getStatus()).isEqualTo(StatusFila.SAIDA_CONFIRMADA);
        assertThat(confirmada.getFirst().getPosicao()).isNull();
        var recusada = motor.priorizar(List.of(naoOnco), Pesos.PADRAO,
                Map.of("9", new DecisaoRegulador(null, null, false, "paciente quer operar")));
        assertThat(recusada.getFirst().getStatus()).isEqualTo(StatusFila.ATIVA);
        assertThat(recusada.getFirst().getPosicao()).isEqualTo(1);
    }

    @Test
    void regraDeOuroRespeitadaMesmoComAjusteManualQuePromoveNaoUrgente() {
        List<Caso> casos = new ArrayList<>();
        for (int i = 0; i < 30; i++) casos.add(caso("N" + i, "K409").build());
        for (int i = 0; i < 10; i++) casos.add(caso("C" + i, "C509").build());
        // regulador promove um paciente sem urgência para A1: ele sobe, mas os oncológicos sobem junto
        var decisoes = Map.of("N0", new DecisaoRegulador(ClasseSwalis.A1, "pedido do cirurgiao", null, null));
        var fila = motor.priorizar(casos, Pesos.PADRAO, decisoes).stream()
                .filter(r -> r.getStatus() == StatusFila.ATIVA).toList();
        assertThat(GuardRegraDeOuro.respeitada(fila)).isTrue();
        assertThat(fila.getFirst().getCaso().oncologicoPeloCid()).isTrue();
        assertThat(fila.get(10).getCaso().solicitacaoId()).isEqualTo("N0");
    }

    // ---------- regra de ouro: fila operacional (ATUAL) × fila de referência (OFICIAL) ----------

    static Evento novo(String sol, String tipo, String resultado) {
        return new Evento("EVT-NOVO-" + sol + tipo, sol, REF, tipo, resultado, null, "registrado agora", "FilaVIVA", true);
    }

    List<Caso> comCinco(Caso c) {
        List<Caso> casos = new ArrayList<>();
        casos.add(c);
        for (int i = 0; i < 5; i++) casos.add(caso("C" + i, "C509").build()); // cânceres estáveis, A2
        return casos;
    }

    static ResultadoPaciente de(List<ResultadoPaciente> fila, String id) {
        return fila.stream().filter(r -> r.getCaso().solicitacaoId().equals(id)).findFirst().orElseThrow();
    }

    static final Map<String, DecisaoRegulador> PROMOVE_N0 =
            Map.of("N0", new DecisaoRegulador(ClasseSwalis.A1, "cirurgiao pediu prioridade", null, null));

    @Test
    void testeA_semUrgenciaNaoUltrapassaCancer() {
        // paciente comprovadamente sem urgência (dor 1, limitação nenhuma, nenhum evento) promovido pelo regulador
        var casos = comCinco(caso("N0", "H250").build());
        for (Referencia ref : Referencia.values()) {
            var fila = motor.priorizar(casos, Pesos.PADRAO, PROMOVE_N0, ref);
            var n0 = de(fila, "N0");
            assertThat(n0.isSemUrgencia()).as(ref.name()).isTrue();
            assertThat(n0.getPosicao()).as(ref.name()).isEqualTo(6); // abaixo dos 5 cânceres
            assertThat(GuardRegraDeOuro.respeitada(fila)).isTrue();
        }
    }

    @Test
    void testeB_progressaoNovaEhConsideradaERecalcula() {
        var antes = motor.priorizar(comCinco(caso("N0", "H250").build()), Pesos.PADRAO, Map.of());
        var depois = motor.priorizar(comCinco(caso("N0", "H250").eventos(List.of(novo("N0", "exame_imagem", "progressao")))
                .build()), Pesos.PADRAO, Map.of());
        var a = de(antes, "N0");
        var d = de(depois, "N0");
        assertThat(d.temSinal(TipoSinal.PROGRESSAO_IMAGEM)).isTrue();
        assertThat(d.classe()).isEqualTo(ClasseSwalis.B);
        assertThat(d.getScore()).isGreaterThan(a.getScore());
        assertThat(d.isSemUrgencia()).isFalse();
        assertThat(GuardRegraDeOuro.respeitada(depois)).isTrue();
        // câncer estável que progride: A2 → A1 e sobe acima dos outros cânceres
        var onco = motor.priorizar(comCinco(caso("C9", "C509").eventos(List.of(novo("C9", "exame_imagem", "progressao")))
                .build()), Pesos.PADRAO, Map.of());
        assertThat(de(onco, "C9").classe()).isEqualTo(ClasseSwalis.A1);
        assertThat(de(onco, "C9").getPosicao()).isEqualTo(1);
    }

    @Test
    void testeD_urgenciaNovaMaisPromocaoPassaNaFilaOperacionalMasNaoNaDeReferencia() {
        var casos = comCinco(caso("N0", "H250").eventos(List.of(novo("N0", "exame_imagem", "progressao"))).build());
        var operacional = motor.priorizar(casos, Pesos.PADRAO, PROMOVE_N0, Referencia.ATUAL);
        var n0 = de(operacional, "N0");
        assertThat(n0.classe()).isEqualTo(ClasseSwalis.A1);
        assertThat(n0.isSemUrgencia()).isFalse();       // a progressão nova é evidência clínica
        assertThat(n0.isMovidoPelaRegraDeOuro()).isFalse();
        assertThat(n0.getPosicao()).isEqualTo(1);        // A1 com evidência passa os A2
        assertThat(GuardRegraDeOuro.respeitada(operacional)).isTrue();
        // a referência oficial (verificador) não enxerga eventos novos: lá ele fica abaixo dos cânceres
        var oficial = motor.priorizar(casos, Pesos.PADRAO, PROMOVE_N0, Referencia.OFICIAL);
        assertThat(de(oficial, "N0").getPosicao()).isEqualTo(6);
        assertThat(GuardRegraDeOuro.respeitada(oficial)).isTrue();
    }

    @Test
    void testeF_promocaoSemEvidenciaNaoContornaARegra() {
        var fila = motor.priorizar(comCinco(caso("N0", "H250").build()), Pesos.PADRAO, PROMOVE_N0, Referencia.ATUAL);
        var n0 = de(fila, "N0");
        assertThat(n0.classe()).isEqualTo(ClasseSwalis.A1);                   // a decisão do regulador fica registrada
        assertThat(n0.getClassificacao().classeCalculada()).isEqualTo(ClasseSwalis.D);
        assertThat(n0.isMovidoPelaRegraDeOuro()).isTrue();
        assertThat(n0.getPosicao()).isEqualTo(6);
        assertThat(n0.getReavaliacoes()).extracting(Reavaliacao::tipo).contains(TipoReavaliacao.REGRA_DE_OURO);
        assertThat(n0.getJustificativa()).contains("sem evidência clínica de urgência registrada");
    }

    @Test
    void dadosInconclusivosNaoContamComoSemUrgenciaNaFilaOperacional() {
        var casos = comCinco(caso("N0", "H250").dor(null).limitacao(null).build());
        var operacional = motor.priorizar(casos, Pesos.PADRAO, PROMOVE_N0, Referencia.ATUAL);
        var n0 = de(operacional, "N0");
        assertThat(n0.isSemUrgencia()).isFalse(); // dado vazio não é "sem urgência"
        assertThat(n0.getReavaliacoes()).extracting(Reavaliacao::tipo).contains(TipoReavaliacao.DADO_INCOMPLETO);
        // mas a promoção para A1 não tem evidência de agravamento: não ultrapassa os cânceres
        assertThat(n0.isPromocaoSemEvidencia()).isTrue();
        assertThat(n0.getPosicao()).isEqualTo(6);
        assertThat(GuardRegraDeOuro.respeitada(operacional)).isTrue();
        // o verificador oficial trata vazio como "nenhum": na referência ele é calmo e fica abaixo
        var oficial = motor.priorizar(casos, Pesos.PADRAO, PROMOVE_N0, Referencia.OFICIAL);
        assertThat(de(oficial, "N0").isSemUrgencia()).isTrue();
        assertThat(de(oficial, "N0").getPosicao()).isEqualTo(6);
        // sem promoção, dado faltando fica em C (não rebaixamos para D sem informação)
        assertThat(de(motor.priorizar(casos, Pesos.PADRAO, Map.of()), "N0").classe()).isEqualTo(ClasseSwalis.C);
    }

    // ---------- lacuna 1: promoção manual precisa de evidência para passar à frente de protegidos ----------

    @Test
    void promocaoDeDorModeradaSemEvidenciaNaoUltrapassaCancer() {
        var casos = comCinco(caso("N0", "K409").dor(6).build()); // classe C pelas regras
        var n0 = de(motor.priorizar(casos, Pesos.PADRAO, PROMOVE_N0), "N0");
        assertThat(n0.classe()).isEqualTo(ClasseSwalis.A1);
        assertThat(n0.getClassificacao().classeCalculada()).isEqualTo(ClasseSwalis.C);
        assertThat(n0.isSemUrgencia()).isFalse();
        assertThat(n0.isPromocaoSemEvidencia()).isTrue();
        assertThat(n0.getPosicao()).isEqualTo(6);
        assertThat(n0.getJustificativa()).contains("promoção do regulador sem evidência de agravamento registrada");
    }

    @Test
    void promocaoComEvidenciaRegistradaUltrapassaEFicaRastreavel() {
        var casos = comCinco(caso("N0", "K409").dor(6).eventos(List.of(novo("N0", "pronto_socorro", "observacao"))).build());
        var fila = motor.priorizar(casos, Pesos.PADRAO, PROMOVE_N0);
        var n0 = de(fila, "N0");
        assertThat(n0.isPromocaoSemEvidencia()).isFalse();
        assertThat(n0.getEvidenciaDaPromocao().tipo()).isEqualTo(TipoSinal.PS_RECENTE);
        assertThat(n0.getPosicao()).isEqualTo(1);
        assertThat(n0.getJustificativa()).contains("classe definida pelo regulador: cirurgiao pediu prioridade")
                .contains("evidência da promoção: Pronto-socorro em");
        assertThat(GuardRegraDeOuro.respeitada(fila)).isTrue();
    }

    // ---------- lacuna 2: texto dos eventos novos ----------

    static Evento novoComTexto(String sol, String tipo, String resultado, String texto) {
        return new Evento("EVT-NOVO-" + sol + tipo, sol, REF, tipo, resultado, null, texto, "FilaVIVA", true);
    }

    @Test
    void textoDeEventoNuncaPromoveSozinhoSoPedeRevisaoUrgente() {
        // a classe vem do evento estruturado (internação → B); o texto "sepse"/"encarcerada" só gera alerta
        for (String texto : List.of("Internado em UTI por sepse urinaria.", "Hernia encarcerada, reduzida com dificuldade no PS.")) {
            var casos = comCinco(caso("N0", "K409").eventos(List.of(novoComTexto("N0", "internacao", "alta", texto))).build());
            var fila = motor.priorizar(casos, Pesos.PADRAO, Map.of());
            var n0 = de(fila, "N0");
            assertThat(n0.temSinal(TipoSinal.ALARME_GRAVE)).as(texto).isFalse();
            assertThat(n0.temSinal(TipoSinal.ALARME_A_CONFIRMAR)).as(texto).isTrue();
            assertThat(n0.classe()).as(texto).isEqualTo(ClasseSwalis.B);
            assertThat(n0.getReavaliacoes()).as(texto).extracting(Reavaliacao::tipo).contains(TipoReavaliacao.REVISAO_URGENTE);
            assertThat(n0.getPosicao()).as(texto).isEqualTo(6); // não passa os cânceres sozinho
            assertThat(GuardRegraDeOuro.respeitada(fila)).isTrue();
        }
    }

    @Test
    void textoDeEventoAfirmativoNegadoHipoteticoEHistorico() {
        Map<String, Boolean> textos = new java.util.LinkedHashMap<>();
        textos.put("Paciente com sepse urinaria, em antibiotico.", true);          // afirmativo → alerta
        textos.put("Sem sinais de sepse.", false);                                  // negado
        textos.put("Afastada sepse.", false);                                       // negado
        textos.put("Orientado sobre risco de estrangulamento da hernia.", false);   // hipotético
        textos.put("Internacao para descartar sepse.", false);                      // hipotético
        textos.put("Historia de sepse em 2019, sem intercorrencias.", false);       // histórico
        textos.put("Sepse ha 5 anos, tratada.", false);                             // histórico
        for (var t : textos.entrySet()) {
            var casos = comCinco(caso("N0", "K409").eventos(List.of(novoComTexto("N0", "consulta_reavaliacao", "estavel", t.getKey()))).build());
            var n0 = de(motor.priorizar(casos, Pesos.PADRAO, Map.of()), "N0");
            assertThat(n0.temSinal(TipoSinal.ALARME_A_CONFIRMAR)).as(t.getKey()).isEqualTo(t.getValue());
            assertThat(n0.temSinal(TipoSinal.ALARME_GRAVE)).as(t.getKey()).isFalse();
            assertThat(n0.classe()).as(t.getKey()).isEqualTo(ClasseSwalis.D); // nenhum texto muda a classe
        }
    }

    @Test
    void textoDoCadastroSoComUrgenciaFicaProvisorioComRevisaoUrgenteSemRebaixar() {
        // dor 0, nenhum evento: a única urgência é o texto do médico
        var casos = comCinco(caso("N0", "K439").dor(0)
                .justificativa("Hernia encarcerada com reducao manual no PS, risco de estrangulamento.").build());
        var n0 = de(motor.priorizar(casos, Pesos.PADRAO, Map.of()), "N0");
        assertThat(n0.classe()).isEqualTo(ClasseSwalis.A1);              // posição/classe mantidas, sem rebaixar
        assertThat(n0.getClasseEstruturada()).isEqualTo(ClasseSwalis.D);
        assertThat(n0.isClassePorTexto()).isTrue();
        assertThat(n0.getJustificativa()).contains("classe provisória: urgência descrita só no texto");
        assertThat(n0.getReavaliacoes()).filteredOn(x -> x.tipo() == TipoReavaliacao.REVISAO_URGENTE).singleElement()
                .satisfies(x -> assertThat(x.mensagem()).contains("Hernia encarcerada").contains("não é confirmação médica"));
        // com dado estruturado que sustenta a classe, não é provisória
        var comEvento = comCinco(caso("N1", "K439").dor(0).eventos(List.of(evento("N1", "pronto_socorro", "observacao", 10)))
                .justificativa("Hernia encarcerada com reducao manual no PS.").build());
        var n1 = de(motor.priorizar(comEvento, Pesos.PADRAO, Map.of()), "N1");
        assertThat(n1.getClasseEstruturada()).isEqualTo(ClasseSwalis.B);
        assertThat(n1.isClassePorTexto()).isTrue(); // A1 continua vindo do texto; B viria do PS
    }

    @Test
    void textoDoCadastroHipoteticoOuHistoricoFicaMarcadoComoTal() {
        var hip = de(motor.priorizar(comCinco(caso("N0", "H251").justificativa("Catarata hipermadura com risco de glaucoma facomorfico, PIO 28.")
                .build()), Pesos.PADRAO, Map.of()), "N0");
        assertThat(hip.getSinais()).anySatisfy(s -> assertThat(s.descricao()).contains("(menção hipotética)"));
        var hist = de(motor.priorizar(comCinco(caso("N1", "N201").justificativa("Litiase renal. Historia de sepse urinaria.")
                .build()), Pesos.PADRAO, Map.of()), "N1");
        assertThat(hist.getSinais()).anySatisfy(s -> assertThat(s.descricao()).contains("(menção a histórico)"));
    }

    @Test
    void negacaoNoTextoDoEventoNaoDisparaAlarme() {
        var casos = comCinco(caso("N0", "K409").eventos(List.of(novoComTexto("N0", "pronto_socorro", "atendido_liberado",
                "Dor inguinal. Sem sinais de encarceramento. Afastada sepse."))).build());
        var n0 = de(motor.priorizar(casos, Pesos.PADRAO, Map.of()), "N0");
        assertThat(n0.temSinal(TipoSinal.ALARME_GRAVE)).isFalse();
        assertThat(n0.classe()).isEqualTo(ClasseSwalis.B); // o PS em si conta como piora
    }

    @Test
    void relatoPorTelefoneDeSepsePedeRevisaoUrgenteSemMudarAClasse() {
        var casos = comCinco(caso("N0", "H250").eventos(List.of(novoComTexto("N0", "contato_telefonico", "atendeu",
                "Filha refere que paciente esteve internado por sepse em outra cidade."))).build());
        var fila = motor.priorizar(casos, Pesos.PADRAO, Map.of());
        var n0 = de(fila, "N0");
        assertThat(n0.temSinal(TipoSinal.ALARME_A_CONFIRMAR)).isTrue();
        assertThat(n0.temSinal(TipoSinal.ALARME_GRAVE)).isFalse();
        assertThat(n0.classe()).isEqualTo(ClasseSwalis.D);
        assertThat(n0.getReavaliacoes()).extracting(Reavaliacao::tipo).contains(TipoReavaliacao.REVISAO_URGENTE);
        assertThat(n0.getPosicao()).isEqualTo(6);
        // e o regulador não consegue usar só esse relato para passar à frente dos cânceres
        var promovido = de(motor.priorizar(casos, Pesos.PADRAO, PROMOVE_N0), "N0");
        assertThat(promovido.getPosicao()).isEqualTo(6);
    }

    @Test
    void textoDosEventosDoDatasetNaoEhRelidoParaNaoMudarAFilaDeReferencia() {
        var doDataset = new Evento("EVT-000999", "N0", REF, "internacao", "alta", 5.0, "Internacao por sepse.", "UPA", false);
        var n0 = de(motor.priorizar(comCinco(caso("N0", "K409").eventos(List.of(doDataset)).build()), Pesos.PADRAO, Map.of()), "N0");
        assertThat(n0.temSinal(TipoSinal.ALARME_GRAVE)).isFalse();
        assertThat(n0.classe()).isEqualTo(ClasseSwalis.B); // a internação conta; o texto do dataset não é relido
    }

    // ---------- regra de ouro sob muitas combinações ----------

    @Test
    void regraDeOuroNuncaQuebraEmCombinacoesAleatoriasNasDuasFilas() {
        var rnd = new java.util.Random(42);
        String[] cids = {"K409", "H250", "M170", "N40", "C509", "C189", "D440", "K801", "I200"};
        String[][] eventos = {{"pronto_socorro", "observacao", "PS"}, {"internacao", "alta", "Internado por sepse"},
                {"exame_imagem", "progressao", "TC com progressao"}, {"biopsia", "positivo", "AP positivo"},
                {"contato_telefonico", "relata_piora", "refere encarceramento"}, {"consulta_reavaliacao", "piora", "sem sinais de sepse"},
                {"hba1c", "resultado", "HbA1c alta"}};
        for (int rodada = 0; rodada < 300; rodada++) {
            List<Caso> casos = new ArrayList<>();
            Map<String, DecisaoRegulador> decisoes = new java.util.HashMap<>();
            for (int i = 0; i < 25; i++) {
                String id = "P" + i;
                var b = caso(id, cids[rnd.nextInt(cids.length)]).dor(rnd.nextInt(5) == 0 ? null : rnd.nextInt(11))
                        .limitacao(new String[]{"nenhuma", "leve", "moderada", "grave", null}[rnd.nextInt(5)]);
                List<Evento> evs = new ArrayList<>();
                for (int k = rnd.nextInt(3); k > 0; k--) {
                    String[] e = eventos[rnd.nextInt(eventos.length)];
                    evs.add(new Evento("E" + i + "-" + k, id, REF, e[0], e[1], null, e[2], "teste", rnd.nextBoolean()));
                }
                casos.add(b.eventos(evs).build());
                if (rnd.nextInt(4) == 0) {
                    decisoes.put(id, new DecisaoRegulador(ClasseSwalis.values()[rnd.nextInt(5)], "aleatorio", null, null));
                }
            }
            for (Referencia ref : Referencia.values()) {
                var fila = motor.priorizar(casos, Pesos.PADRAO, ref == Referencia.OFICIAL ? Map.of() : decisoes, ref).stream()
                        .filter(r -> r.getStatus().naFila()).toList();
                assertThat(GuardRegraDeOuro.respeitada(fila)).as("rodada " + rodada + " " + ref).isTrue();
                // nenhum paciente sem urgência acima de um CID C
                int ultimoCidC = -1;
                for (int i = 0; i < fila.size(); i++) if (fila.get(i).getCaso().oncologicoPeloCid()) ultimoCidC = i;
                for (int i = 0; i < ultimoCidC; i++) {
                    assertThat(fila.get(i).isSemUrgencia()).as("rodada " + rodada + " " + ref + " pos " + (i + 1)).isFalse();
                    assertThat(fila.get(i).isPromocaoSemEvidencia()).as("rodada " + rodada + " " + ref).isFalse();
                }
            }
        }
    }

    @Test
    void biopsiaNovaVirouCancerEEhProtegidaNaFilaOperacional() {
        var casos = comCinco(caso("N0", "H250").eventos(List.of(novo("N0", "biopsia", "positivo"))).build());
        var fila = motor.priorizar(casos, Pesos.PADRAO, Map.of(), Referencia.ATUAL);
        var n0 = de(fila, "N0");
        assertThat(n0.classe()).isEqualTo(ClasseSwalis.A2);
        assertThat(n0.oncologico()).isTrue();
        assertThat(n0.isProtegido()).isTrue();
        assertThat(n0.isSemUrgencia()).isFalse();
        assertThat(n0.isMovidoPelaRegraDeOuro()).isFalse();
    }

    @Test
    void ordemTemPosicoesSequenciaisEJustificativa() {
        var fila = motor.priorizar(List.of(caso("A", "K409").build(), caso("B", "C509").build()), Pesos.PADRAO, Map.of());
        assertThat(fila).extracting(ResultadoPaciente::getPosicao).containsExactly(1, 2);
        assertThat(fila.getFirst().getCaso().solicitacaoId()).isEqualTo("B");
        assertThat(fila.getFirst().getJustificativa()).startsWith("A2");
    }
}
