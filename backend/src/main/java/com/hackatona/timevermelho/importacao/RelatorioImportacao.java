package com.hackatona.timevermelho.importacao;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/** Contagem dos defeitos de dados encontrados e tratados na importação (aparece no painel de indicadores). */
@Component
public class RelatorioImportacao {

    private final Map<String, Long> itens = new LinkedHashMap<>();

    public synchronized void limpar() {
        itens.clear();
    }

    public synchronized void somar(String item, long quantidade) {
        itens.merge(item, quantidade, Long::sum);
    }

    public synchronized void definir(String item, long quantidade) {
        itens.put(item, quantidade);
    }

    public synchronized Map<String, Long> itens() {
        return new LinkedHashMap<>(itens);
    }
}
