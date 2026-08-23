package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosImportacionBanco.ErrorDeImportacion;

import java.util.List;

/**
 * El archivo subido no sirve para crear un banco, y aquí está el porqué completo.
 *
 * <p>No es una {@code IllegalArgumentException} más porque su valor está en la lista:
 * quien sube un Excel de 190 preguntas necesita todos los problemas de una vez
 * —hoja, fila y mensaje— y no descubrirlos de uno en uno a rechazo por entrega.
 * El manejador de errores la convierte en un 400 con la lista dentro.
 */
public class ImportacionInvalidaException extends RuntimeException {

    private final transient List<ErrorDeImportacion> errores;

    public ImportacionInvalidaException(List<ErrorDeImportacion> errores) {
        super("El archivo tiene " + errores.size()
                + (errores.size() == 1 ? " problema" : " problemas")
                + " y no se importó nada");
        this.errores = List.copyOf(errores);
    }

    public List<ErrorDeImportacion> getErrores() {
        return errores;
    }
}
