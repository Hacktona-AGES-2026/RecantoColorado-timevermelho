package com.hackatona.timevermelho.importacao;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hackatona.timevermelho.dominio.EventoClinico;
import com.hackatona.timevermelho.dominio.EventoClinicoRepository;
import com.hackatona.timevermelho.dominio.Procedimento;
import com.hackatona.timevermelho.dominio.ProcedimentoRepository;
import com.hackatona.timevermelho.dominio.Solicitacao;
import com.hackatona.timevermelho.dominio.SolicitacaoRepository;

import static com.hackatona.timevermelho.importacao.Normalizacao.*;

/**
 * Lê os CSVs do desafio e grava no banco, já tratando os defeitos dos dados:
 * CID sem ponto, prioridade com grafia diferente, município em caixa errada, solicitações
 * duplicadas (mesmo paciente + procedimento), eventos duplicados e eventos órfãos.
 */
@Service
public class ImportadorDataset {

    private static final Logger log = LoggerFactory.getLogger(ImportadorDataset.class);

    private final Path pasta;
    private final ProcedimentoRepository procedimentos;
    private final SolicitacaoRepository solicitacoes;
    private final EventoClinicoRepository eventos;
    private final RelatorioImportacao relatorio;

    public ImportadorDataset(@Value("${app.dataset.dir}") String pasta, ProcedimentoRepository procedimentos,
                             SolicitacaoRepository solicitacoes, EventoClinicoRepository eventos,
                             RelatorioImportacao relatorio) {
        this.pasta = Path.of(pasta);
        this.procedimentos = procedimentos;
        this.solicitacoes = solicitacoes;
        this.eventos = eventos;
        this.relatorio = relatorio;
    }

    @Transactional
    public void importar() {
        if (solicitacoes.count() > 0) {
            log.info("Banco já tem dados; importação ignorada");
            return;
        }
        relatorio.limpar();
        importarProcedimentos();
        Map<String, String> principalDe = importarSolicitacoes();
        importarEventos(principalDe);
        log.info("Dataset importado de {}: {}", pasta.toAbsolutePath(), relatorio.itens());
    }

    private void importarProcedimentos() {
        List<Procedimento> lista = new ArrayList<>();
        ler("procedimentos.csv", r -> {
            Procedimento p = new Procedimento();
            p.setId(texto(r.get("procedimento_id")));
            p.setNome(texto(r.get("procedimento")));
            p.setEspecialidade(texto(r.get("especialidade")));
            p.setPorte(texto(r.get("porte")));
            p.setRiscoCirurgico(inteiro(r.get("risco_cirurgico")));
            p.setDuracaoMediaMin(inteiro(r.get("duracao_media_min")));
            p.setNecessitaUti(texto(r.get("necessita_uti")));
            lista.add(p);
        });
        procedimentos.saveAll(lista);
    }

    /** Devolve, para cada solicitação, qual é a principal (ela mesma, ou a mais antiga do grupo duplicado). */
    private Map<String, String> importarSolicitacoes() {
        List<Solicitacao> lista = new ArrayList<>();
        ler("fila_cirurgica.csv", r -> {
            Solicitacao s = new Solicitacao();
            s.setId(texto(r.get("solicitacao_id")));
            s.setPacienteId(texto(r.get("paciente_id")));
            s.setNome(texto(r.get("nome_paciente")));
            s.setSexo(texto(r.get("sexo")));
            s.setIdade(inteiro(r.get("idade")));
            s.setMunicipioOriginal(texto(r.get("municipio")));
            s.setMunicipio(municipio(r.get("municipio")));
            s.setUf(texto(r.get("uf")));
            s.setDistanciaKm(inteiro(r.get("distancia_hospital_km")));
            s.setProcedimentoId(texto(r.get("procedimento_id")));
            s.setProcedimento(texto(r.get("procedimento")));
            s.setEspecialidade(texto(r.get("especialidade")));
            s.setCidOriginal(texto(r.get("cid_principal")));
            s.setCid(cid(r.get("cid_principal")));
            s.setCidDescricao(texto(r.get("cid_descricao")));
            s.setCidSecundario(cid(r.get("cid_secundario")));
            s.setAsa(inteiro(r.get("asa")));
            s.setComorbidades(texto(r.get("comorbidades")));
            s.setDor(inteiro(r.get("intensidade_dor_eva")));
            s.setLimitacao(minusculo(r.get("limitacao_funcional")));
            s.setJustificativa(texto(r.get("justificativa_clinica")));
            s.setObservacoes(texto(r.get("observacoes")));
            s.setDataEntrada(data(r.get("data_entrada_fila")));
            s.setDiasEmFilaOriginal(inteiro(r.get("dias_em_fila")));
            s.setPrioridadeOriginal(texto(r.get("prioridade_regulacao")));
            s.setPrioridadeManual(prioridade(r.get("prioridade_regulacao")));
            s.setDataUltimaRevisao(data(r.get("data_ultima_revisao_prioridade")));
            s.setFlagOncologico(minusculo(r.get("flag_oncologico")));
            s.setFlagDiabetico(minusculo(r.get("flag_diabetico")));
            s.setFlagPiora(minusculo(r.get("flag_piora_clinica")));
            s.setHba1cUltima(decimal(r.get("hba1c_ultima")));
            s.setAvaliacaoPreAnestesica(texto(r.get("avaliacao_pre_anestesica")));
            s.setExamesPreOpValidos(texto(r.get("exames_pre_op_validos")));
            lista.add(s);
        });

        relatorio.definir("Solicitações lidas", lista.size());
        relatorio.definir("CID sem ponto (ex.: K409) normalizado", lista.stream()
                .filter(s -> s.getCidOriginal() != null && !s.getCidOriginal().contains(".") && s.getCid().length() > 3).count());
        relatorio.definir("Prioridade manual com grafia fora do padrão corrigida", lista.stream()
                .filter(s -> s.getPrioridadeOriginal() != null && !s.getPrioridadeOriginal().equals(s.getPrioridadeManual())).count());
        relatorio.definir("Prioridade manual vazia", lista.stream().filter(s -> s.getPrioridadeManual() == null).count());
        relatorio.definir("Município com grafia corrigida", lista.stream()
                .filter(s -> s.getMunicipioOriginal() != null && !s.getMunicipioOriginal().equals(s.getMunicipio())).count());
        relatorio.definir("Dor (EVA) vazia", lista.stream().filter(s -> s.getDor() == null).count());
        relatorio.definir("Limitação funcional vazia", lista.stream().filter(s -> s.getLimitacao() == null).count());
        relatorio.definir("ASA vazio", lista.stream().filter(s -> s.getAsa() == null).count());
        relatorio.definir("Flag oncológico diverge do CID", lista.stream()
                .filter(s -> s.getCid().startsWith("C") != "sim".equals(s.getFlagOncologico())).count());

        // duplicadas: mesmo paciente + mesmo procedimento. Fica a de entrada mais antiga (o paciente espera desde lá).
        Map<String, List<Solicitacao>> grupos = new HashMap<>();
        for (Solicitacao s : lista) {
            grupos.computeIfAbsent(s.getPacienteId() + "|" + s.getProcedimentoId(), k -> new ArrayList<>()).add(s);
        }
        Map<String, String> principalDe = new HashMap<>();
        int duplicadas = 0;
        for (List<Solicitacao> g : grupos.values()) {
            g.sort(Comparator.comparing(Solicitacao::getDataEntrada).thenComparing(Solicitacao::getId));
            Solicitacao principal = g.getFirst();
            for (Solicitacao s : g) {
                principalDe.put(s.getId(), principal.getId());
                if (s != principal) {
                    s.setSituacao(Solicitacao.SituacaoCadastro.DUPLICADA);
                    s.setDuplicadaDe(principal.getId());
                    mesclarTexto(principal, s);
                    duplicadas++;
                }
            }
        }
        relatorio.definir("Solicitações duplicadas mescladas (mesmo paciente + procedimento)", duplicadas);
        solicitacoes.saveAll(lista);
        return principalDe;
    }

    /** Observações da duplicata não podem se perder: são juntadas às da principal. */
    private static void mesclarTexto(Solicitacao principal, Solicitacao duplicada) {
        String obs = duplicada.getObservacoes();
        if (obs != null && (principal.getObservacoes() == null || !principal.getObservacoes().contains(obs))) {
            principal.setObservacoes(principal.getObservacoes() == null ? obs : principal.getObservacoes() + " | " + obs);
        }
        // segurança: se a duplicata diz câncer e a principal não, o câncer não pode sumir na mescla
        if (duplicada.getCid() != null && duplicada.getCid().startsWith("C")
                && (principal.getCid() == null || !principal.getCid().startsWith("C"))) {
            principal.setCid(duplicada.getCid());
            principal.setCidDescricao(duplicada.getCidDescricao());
        }
        if (principal.getDor() == null) principal.setDor(duplicada.getDor());
        if (principal.getLimitacao() == null) principal.setLimitacao(duplicada.getLimitacao());
    }

    private void importarEventos(Map<String, String> principalDe) {
        List<EventoClinico> lista = new ArrayList<>();
        Set<String> conteudos = new HashSet<>();
        int[] orfaos = {0};
        int[] duplicados = {0};
        LocalDateTime agora = LocalDateTime.now();
        ler("eventos_clinicos.csv", r -> {
            EventoClinico e = new EventoClinico();
            e.setId(texto(r.get("evento_id")));
            String original = texto(r.get("solicitacao_id"));
            e.setSolicitacaoOriginal(original);
            e.setPacienteId(texto(r.get("paciente_id")));
            e.setData(data(r.get("data_evento")));
            e.setTipo(minusculo(r.get("tipo_evento")));
            e.setResultado(minusculo(r.get("resultado")));
            e.setValor(decimal(r.get("valor_numerico")));
            e.setDescricao(texto(r.get("descricao")));
            e.setOrigem(texto(r.get("origem")));
            e.setCriadoEm(agora);
            String principal = principalDe.get(original);
            if (principal == null) {
                e.setSolicitacaoId(original);
                e.setOrfao(true);
                orfaos[0]++;
            } else {
                e.setSolicitacaoId(principal);
                String chave = String.join("|", principal, Objects.toString(e.getData()), e.getTipo(),
                        Objects.toString(e.getResultado()), Objects.toString(e.getValor()), Objects.toString(e.getDescricao()));
                if (!conteudos.add(chave)) {
                    e.setDuplicado(true);
                    duplicados[0]++;
                }
            }
            lista.add(e);
        });
        eventos.saveAll(lista);
        relatorio.definir("Eventos lidos", lista.size());
        relatorio.definir("Eventos duplicados ignorados (mesmo conteúdo, id diferente)", duplicados[0]);
        relatorio.definir("Eventos órfãos ignorados (solicitação fora da fila)", orfaos[0]);
    }

    private void ler(String arquivo, Consumer<CSVRecord> porLinha) {
        Path caminho = pasta.resolve(arquivo);
        CSVFormat formato = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).get();
        try (Reader in = Files.newBufferedReader(caminho, StandardCharsets.UTF_8);
             CSVParser parser = formato.parse(in)) {
            for (CSVRecord r : parser) porLinha.accept(r);
        } catch (IOException e) {
            throw new UncheckedIOException("Não consegui ler " + caminho.toAbsolutePath(), e);
        }
    }
}
