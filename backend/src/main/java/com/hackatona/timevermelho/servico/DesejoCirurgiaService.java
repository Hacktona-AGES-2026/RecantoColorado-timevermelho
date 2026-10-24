package com.hackatona.timevermelho.servico;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hackatona.timevermelho.api.dto.Dtos;
import com.hackatona.timevermelho.dominio.Alerta;
import com.hackatona.timevermelho.dominio.AlertaRepository;
import com.hackatona.timevermelho.dominio.DesejoCirurgia;
import com.hackatona.timevermelho.dominio.DesejoCirurgiaRepository;
import com.hackatona.timevermelho.medico.Medico;
import com.hackatona.timevermelho.medico.MedicoRepository;
import com.hackatona.timevermelho.motor.ResultadoPaciente;

/**
 * Guarda se o paciente quer ser operado. Só informa: não muda classe, score nem posição, e não tira ninguém da fila.
 * "Não quer" e "em dúvida" viram pendências para o regulador conversar (benefícios, riscos, alternativas e não fazer
 * nada) e, se for o caso, confirmar a saída.
 */
@Service
public class DesejoCirurgiaService {

    private final DesejoCirurgiaRepository desejos;
    private final MedicoRepository medicos;
    private final DecisaoService decisoes;
    private final FilaService fila;
    private final AlertaRepository alertas;

    public DesejoCirurgiaService(DesejoCirurgiaRepository desejos, MedicoRepository medicos, DecisaoService decisoes,
                                 FilaService fila, AlertaRepository alertas) {
        this.desejos = desejos;
        this.medicos = medicos;
        this.decisoes = decisoes;
        this.fila = fila;
        this.alertas = alertas;
    }

    @Transactional
    public Dtos.DesejoCirurgiaDto registrar(String solicitacaoId, Dtos.DesejoCirurgiaRequest req) {
        String id = decisoes.resolverId(solicitacaoId);
        ResultadoPaciente r = fila.atual().get(id);
        if (r == null) throw new Erros.NaoEncontrado("Solicitação " + solicitacaoId + " não está na fila");
        String autor = req.autor() == null || req.autor().isBlank() ? "regulador" : req.autor().trim();
        if (req.medicoId() != null) {
            Medico m = medicos.findById(req.medicoId())
                    .orElseThrow(() -> new Erros.NaoEncontrado("Médico " + req.medicoId() + " não encontrado"));
            autor = m.getNome() + " (" + m.getCrm() + ")";
        }
        DesejoCirurgia d = new DesejoCirurgia();
        d.setSolicitacaoId(id);
        d.setResposta(req.resposta());
        d.setObservacao(req.observacao() == null || req.observacao().isBlank() ? null : req.observacao().trim());
        d.setAutor(autor);
        d.setMedicoId(req.medicoId());
        d.setCriadoEm(LocalDateTime.now());
        desejos.save(d);
        fila.recalcular(); // só atualiza a marcação na fila; a ordem não muda

        Alerta a = new Alerta();
        a.setSolicitacaoId(id);
        a.setNome(r.getCaso().nome());
        a.setTipo(Alerta.Tipo.AJUSTE_REGULADOR);
        a.setMensagem("Desejo do paciente registrado por " + autor + ": " + req.resposta().rotulo().toLowerCase()
                + (d.getObservacao() == null ? "" : " (\"" + d.getObservacao() + "\")") + ". A posição na fila não muda.");
        a.setClasseAntes(r.classe());
        a.setClasseDepois(r.classe());
        a.setPosicaoAntes(r.getPosicao());
        a.setPosicaoDepois(r.getPosicao());
        a.setCriadoEm(LocalDateTime.now());
        alertas.save(a);
        return dto(d);
    }

    public Dtos.DesejoCirurgiaDto atual(String solicitacaoId) {
        return desejos.findBySolicitacaoIdOrderByCriadoEmDescIdDesc(solicitacaoId).stream().findFirst().map(DesejoCirurgiaService::dto)
                .orElse(null);
    }

    public List<Dtos.DesejoCirurgiaDto> historico(String solicitacaoId) {
        return desejos.findBySolicitacaoIdOrderByCriadoEmDescIdDesc(solicitacaoId).stream().map(DesejoCirurgiaService::dto).toList();
    }

    /** Resposta mais recente de cada paciente. */
    public Map<String, DesejoCirurgia> atuais() {
        Map<String, DesejoCirurgia> m = new HashMap<>();
        for (DesejoCirurgia d : desejos.findAllByOrderByCriadoEmAscIdAsc()) m.put(d.getSolicitacaoId(), d);
        return m;
    }

    static Dtos.DesejoCirurgiaDto dto(DesejoCirurgia d) {
        return new Dtos.DesejoCirurgiaDto(d.getId(), d.getResposta().name(), d.getResposta().rotulo(), d.getObservacao(),
                d.getAutor(), d.getMedicoId(), d.getCriadoEm());
    }
}
