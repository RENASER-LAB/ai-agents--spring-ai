package com.renaser.ai.ai_engine.simulacion.service;

import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.InsumoConversacion;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.ResultadoConversacion;

/**
 * La única puerta entre el motor de agentes y la conversación final.
 *
 * <p>Misma frontera y mismo motivo que {@code PuentePruebaIa}: el agente vive bajo
 * {@code ai/} y {@code pregunta_generada} es una tabla de selección.
 *
 * <p><b>Reglas que se cumplen aquí y no en el agente:</b>
 * <ul>
 *   <li>Una pregunta ya respondida no se borra ni se reescribe. Lo que dijo el candidato en
 *       la sala es un hecho, y un segundo intento del agente no puede hacerlo desaparecer.
 *   <li>Un {@code alertaId} que no existe se descarta en vez de guardarse roto.
 *   <li>Como mucho cinco preguntas: la conversación dura quince minutos.
 * </ul>
 */
public interface PuenteSimulacionIa {

    /**
     * Todo lo que el candidato mostró antes, que es de donde salen las preguntas.
     *
     * @throws IllegalStateException si no hay nada de lo que preguntar: sin notas, sin
     *                               hallazgos y sin línea de tiempo, el modelo solo podría
     *                               inventar preguntas genéricas, y para eso no hace falta
     *                               pagar una llamada
     */
    InsumoConversacion insumoConversacion(Long postulacionId);

    /** Guarda las preguntas, respetando las que ya se hicieron y se contestaron. */
    void guardarPreguntas(Long postulacionId, Long ejecucionIaId, ResultadoConversacion resultado);
}
