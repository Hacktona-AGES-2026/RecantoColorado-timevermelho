package com.hackatona.timevermelho.servico;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;

import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.motor.ClasseSwalis;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.motor.TipoSinal;

/**
 * Gera eventos sintéticos para a demo ao vivo: "chegou agora uma biópsia positiva", etc.
 * Sempre escolhe pacientes reais da fila; os eventos ficam marcados como sintéticos.
 */
@Service
public class DemoService {

    private final FilaService fila;
    private final DecisaoService decisoes;

    public DemoService(FilaService fila, DecisaoService decisoes) {
        this.fila = fila;
        this.decisoes = decisoes;
    }

    public List<Dtos.Impacto> cenario() {
        var ativos = fila.atual().ativos();
        List<Dtos.Impacto> impactos = new ArrayList<>();

        // 1. lesão incerta lá no fim da fila recebe biópsia positiva
        ultimo(ativos, r -> r.temSinal(TipoSinal.CID_INCERTO) && !r.oncologico()).ifPresent(r ->
                impactos.add(decisoes.registrarEvento(new Dtos.NovoEvento(id(r), "biopsia", "positivo", null,
                        "AP: carcinoma invasivo. Laudo liberado hoje.", null))));

        // 2. hérnia "sem urgência" vai ao pronto-socorro
        ultimo(fila.atual().ativos(), r -> r.classe() == ClasseSwalis.D && r.getCaso().cid() != null
                && r.getCaso().cid().startsWith("K4")).ifPresent(r ->
                impactos.add(decisoes.registrarEvento(new Dtos.NovoEvento(id(r), "pronto_socorro", "observacao", null,
                        "Dor e abaulamento inguinal, reduzido com dificuldade no PS.", null))));

        // 3. oncológico estável mostra progressão na imagem
        ultimo(fila.atual().ativos(), r -> r.classe() == ClasseSwalis.A2 && r.getCaso().oncologicoPeloCid()).ifPresent(r ->
                impactos.add(decisoes.registrarEvento(new Dtos.NovoEvento(id(r), "exame_imagem", "progressao", null,
                        "TC de controle: aumento das dimensões da lesão e novo linfonodo suspeito.", null))));

        // 4. diabético descompensa
        ultimo(fila.atual().ativos(), r -> "sim".equals(r.getCaso().flagDiabetico()) && !r.temSinal(TipoSinal.HBA1C_ALTA))
                .ifPresent(r -> impactos.add(decisoes.registrarEvento(new Dtos.NovoEvento(id(r), "hba1c", "resultado",
                        9.8, "HbA1c 9,8%.", null))));
        return impactos;
    }

    private static Optional<ResultadoPaciente> ultimo(List<ResultadoPaciente> ativos, Predicate<ResultadoPaciente> filtro) {
        return ativos.stream().filter(filtro).max(Comparator.comparing(ResultadoPaciente::getPosicao));
    }

    private static String id(ResultadoPaciente r) {
        return r.getCaso().solicitacaoId();
    }
}
