package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.InsumoRedactor;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.ResultadoRedactor;

/**
 * Lo que el agente REDACTOR necesita de la base, en las dos direcciones: armar su insumo
 * desde la ficha, y dejar el borrador guardado como banco de la vacante. Existe por lo
 * mismo que {@code PuenteCalificacionIa}: el agente habla con el modelo, el puente con
 * las tablas, y ninguno hace el trabajo del otro.
 */
public interface PuenteRedactor {

    /**
     * La ficha, el nivel y la estructura del cuestionario, listos para el modelo.
     *
     * @throws IllegalStateException si la ficha no existe o no está COMPLETA: media ficha
     *                               no es un insumo, es un borrador del dueño.
     */
    InsumoRedactor insumo(Long vacanteId);

    /**
     * Guarda el borrador como {@code version_banco} de la vacante (tipo VACANTE, método
     * CRITERIOS, estado BORRADOR). Si había un borrador anterior, se archiva: nada se
     * borra, y solo hay un borrador vivo por vacante.
     */
    void guardarBorrador(Long vacanteId, ResultadoRedactor resultado);
}
