package com.hackatona.timevermelho.servico;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.motor.ResultadoPaciente;
import com.hackatona.timevermelho.motor.Sinal;
import com.hackatona.timevermelho.motor.StatusFila;

/** Compara o paciente antes e depois de uma mudança e escreve a frase do impacto. */
final class ImpactoCalculadora {

    private ImpactoCalculadora() {}

    static List<Sinal> sinaisNovos(ResultadoPaciente antes, ResultadoPaciente depois) {
        Set<String> eram = antes.getSinais().stream().map(s -> s.tipo() + "|" + s.descricao()).collect(Collectors.toSet());
        return depois.getSinais().stream().filter(s -> !eram.contains(s.tipo() + "|" + s.descricao())).toList();
    }

    static Dtos.Impacto impacto(ResultadoPaciente antes, ResultadoPaciente depois, String motivo) {
        return new Dtos.Impacto(depois.getCaso().solicitacaoId(), depois.getCaso().nome(), antes.getPosicao(),
                depois.getPosicao(), antes.classe().name(), depois.classe().name(), antes.getStatus().name(),
                depois.getStatus().name(), mensagem(antes, depois, motivo));
    }

    static String mensagem(ResultadoPaciente antes, ResultadoPaciente depois, String motivo) {
        StringBuilder sb = new StringBuilder();
        Integer pa = antes.getPosicao();
        Integer pd = depois.getPosicao();
        if (depois.getStatus() == StatusFila.SAIDA_CONFIRMADA && antes.getStatus().naFila()) {
            sb.append("Saiu da fila (estava na posição ").append(pa).append(")");
        } else if (pa == null && pd != null) {
            sb.append("Voltou para a fila na posição ").append(pd);
        } else if (pa != null && pd != null && !pa.equals(pd)) {
            sb.append(pd < pa ? "Subiu" : "Desceu").append(" da posição ").append(pa).append(" para ").append(pd);
        } else {
            sb.append("Posição mantida").append(pd == null ? "" : " (" + pd + ")");
        }
        if (antes.classe() != depois.classe()) {
            sb.append(", classe ").append(antes.classe()).append(" → ").append(depois.classe());
        }
        if (motivo != null && !motivo.isBlank()) sb.append(": ").append(motivo);
        if (depois.isMovidoPelaRegraDeOuro()) {
            sb.append(depois.isPromocaoSemEvidencia() && !depois.isSemUrgencia()
                    ? " — posição limitada pela regra de ouro: sem evidência de agravamento registrada, não ultrapassa oncológicos"
                    : " — posição limitada pela regra de ouro: não ultrapassa oncológicos");
        }
        if (depois.temSinal(com.hackatona.timevermelho.motor.TipoSinal.ALARME_A_CONFIRMAR)
                && !antes.temSinal(com.hackatona.timevermelho.motor.TipoSinal.ALARME_A_CONFIRMAR)) {
            sb.append(" — possível agravamento grave relatado: revisão urgente");
        }
        return sb.toString();
    }
}
