package com.renaser.ai.ai_engine.organizacion.service;

import java.util.Optional;

/**
 * Cuánta gente trabaja en la empresa, en los seis rangos de la presentación (V85). El código es
 * lo que se guarda (y lo que exige el CHECK de la columna); el texto, lo que se lee.
 */
public enum TamanoDeEmpresa {
    DE_1_A_10("1–10 colaboradores"),
    DE_11_A_50("11–50 colaboradores"),
    DE_51_A_200("51–200 colaboradores"),
    DE_201_A_500("201–500 colaboradores"),
    DE_501_A_1000("501–1000 colaboradores"),
    MAS_DE_1000("Más de 1000 colaboradores");

    private final String texto;

    TamanoDeEmpresa(String texto) {
        this.texto = texto;
    }

    public String texto() {
        return texto;
    }

    public static Optional<TamanoDeEmpresa> deCodigo(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        for (TamanoDeEmpresa tamano : values()) {
            if (tamano.name().equals(codigo.strip())) {
                return Optional.of(tamano);
            }
        }
        return Optional.empty();
    }

    /** El texto de un código guardado, o nulo si no hay o no se reconoce. */
    public static String textoDe(String codigo) {
        return deCodigo(codigo).map(TamanoDeEmpresa::texto).orElse(null);
    }
}
