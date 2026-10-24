package com.hackatona.timevermelho.motor;

import static com.hackatona.timevermelho.motor.CategoriaSinal.*;

/** Tudo o que o motor sabe enxergar num paciente. Cada sinal carrega a evidência de onde veio. */
public enum TipoSinal {
    ONCO_CID("Neoplasia maligna pelo CID", DIAGNOSTICO),
    BIOPSIA_POSITIVA("Biópsia positiva para malignidade", DIAGNOSTICO),
    CID_URGENTE("CID de urgência", DIAGNOSTICO),
    CID_INCERTO("Lesão de comportamento incerto / achado suspeito", DIAGNOSTICO),
    SUSPEITA_MALIGNIDADE("Sinal de malignidade ou progressão no texto", DIAGNOSTICO),
    ALARME_GRAVE("Sinal de alarme grave no texto", PIORA),
    ALARME_FUNCIONAL("Perda funcional importante no texto", PIORA),
    PROGRESSAO_IMAGEM("Progressão em exame de imagem", PIORA),
    PS_RECENTE("Pronto-socorro nos últimos 180 dias", PIORA),
    INTERNACAO_RECENTE("Internação nos últimos 180 dias", PIORA),
    PS_TEXTO("Idas ao PS ou internação citadas no texto", PIORA),
    REAVALIACAO_PIORA("Reavaliação ambulatorial com piora", PIORA),
    RELATA_PIORA("Paciente relata piora", PIORA),
    HBA1C_ALTA("HbA1c acima de 8,5%", PIORA),
    HBA1C_SUBINDO("HbA1c subindo durante a espera", PIORA),
    MELHORA_RELATADA("Paciente relata melhora", SINTOMA),
    DOR_INTENSA("Dor intensa (EVA ≥ 7)", SINTOMA),
    DOR_MODERADA("Dor moderada (EVA 4 a 6)", SINTOMA),
    LIMITACAO_GRAVE("Limitação funcional grave", SINTOMA),
    LIMITACAO_MODERADA("Limitação funcional moderada", SINTOMA),
    SAIDA_DESISTENCIA("Paciente desistiu da cirurgia", SAIDA),
    SAIDA_OUTRO_SERVICO("Paciente operou em outro serviço", SAIDA),
    SEM_CONTATO("Sem contato com o paciente", QUALIDADE_DO_DADO),
    DADO_INCOMPLETO("Dado clínico incompleto", QUALIDADE_DO_DADO),
    FLAG_ONCO_DIVERGENTE("Flag oncológico do cadastro diverge do CID", QUALIDADE_DO_DADO),
    FLAG_PIORA_DESATUALIZADA("Flag de piora do cadastro desatualizada", QUALIDADE_DO_DADO);

    private final String rotulo;
    private final CategoriaSinal categoria;

    TipoSinal(String rotulo, CategoriaSinal categoria) {
        this.rotulo = rotulo;
        this.categoria = categoria;
    }

    public String rotulo() { return rotulo; }

    public CategoriaSinal categoria() { return categoria; }
}
