package com.renaser.ai.ai_engine.colaborador.service;

import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.ErrorDeCarga;

import java.util.List;

/**
 * El Excel de colaboradores no se cargó, y aquí están todos los porqués.
 *
 * <p>Todo o nada: se valida el archivo entero antes de guardar la primera fila, y quien lo
 * subió necesita la lista completa —fila, columna, valor y qué pasa— para corregirlo de una
 * sola pasada. El manejador de errores la devuelve como 400 con la lista dentro.
 */
public class CargaInvalidaException extends RuntimeException {

    private final transient List<ErrorDeCarga> errores;

    public CargaInvalidaException(List<ErrorDeCarga> errores) {
        super(errores.size() == 1
                ? "El archivo tiene 1 error. No se guardó nada."
                : "El archivo tiene " + errores.size() + " errores. No se guardó nada.");
        this.errores = List.copyOf(errores);
    }

    public List<ErrorDeCarga> getErrores() {
        return errores;
    }
}
