package com.renaser.ai.ai_engine.organizacion.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * La presentación no se guarda así (V81): un 400 con TODOS los campos que fallan, cada uno con
 * su frase, para que el panel los marque juntos y no se guarde nada.
 *
 * <p>Las claves son las del cuerpo ({@code paginaWeb}, {@code videoPresentacion},
 * {@code propositoEmpresa}…) y, en las redes, su código ({@code INSTAGRAM}). Con un solo campo el
 * mensaje es su frase —«El propósito admite hasta 1500 caracteres»—; con varios, «No se guardó
 * nada. Corrige los campos marcados.».
 */
public class PresentacionInvalidaException extends RuntimeException {

    public static final String VARIOS = "No se guardó nada. Corrige los campos marcados.";

    private final transient Map<String, String> errores;

    public PresentacionInvalidaException(Map<String, String> errores) {
        super(errores.size() == 1 ? errores.values().iterator().next() : VARIOS);
        this.errores = Collections.unmodifiableMap(new LinkedHashMap<>(errores));
    }

    public Map<String, String> getErrores() {
        return errores;
    }
}
