package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.InsumoRecomendador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosRecomendador.ResultadoRecomendador;

import java.util.List;

/**
 * La puerta entre el RECOMENDADOR y las preguntas propias de una vacante (V66). Como
 * {@code PuenteRedactor}: el agente pide los datos y entrega el resultado; qué tabla se
 * escribe se decide de este lado.
 *
 * <p><b>Nada de lo que entrega toca el borrador.</b> Se guarda como propuesta y una persona
 * agrega lo que quiera.
 */
public interface PuenteRecomendador {

    /**
     * Los datos de la vacante, cuántos puntos faltan, la indicación y lo que ya hay en el
     * borrador. Nulo si no hay ninguna propuesta pedida esperando.
     */
    InsumoRecomendador insumo(Long vacanteId);

    /** Guarda la propuesta ya validada: queda LISTA para que el panel la enseñe. */
    void guardarPropuesta(Long vacanteId, ResultadoRecomendador resultado);

    /** La propuesta no pasó la aduana tras la corrección: queda FALLIDA y se dice por qué. */
    void marcarFallida(Long vacanteId, List<String> errores);
}
