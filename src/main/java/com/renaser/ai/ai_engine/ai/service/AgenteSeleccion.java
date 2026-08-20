package com.renaser.ai.ai_engine.ai.service;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;

/**
 * Uno de los agentes que trabajan sobre una postulación.
 *
 * <p><b>Cuatro corren en fila</b> para armar el retrato del candidato: {@code DATOS_CV} saca
 * quién es, {@code EVIDENCIA_CV} lee el currículum, {@code EVALUADOR} califica las respuestas
 * abiertas y {@code POTENCIAL_RIESGO} arma el Perfil de Talento con lo que dejaron los tres
 * anteriores.
 *
 * <p><b>Los otros dos corren solos y se piden a mano</b>, porque atienden etapas posteriores
 * que ocurren semanas después: {@code PRUEBA_PUESTO} califica la prueba entregada contra su
 * rúbrica, y {@code SIMULACION} prepara las preguntas de la conversación final.
 *
 * <p>Existe la interfaz para que la cola no tenga un {@code switch} con seis nombres dentro:
 * cada agente se registra solo, y añadir el séptimo es escribir una clase, no editar la cola.
 *
 * <p><b>Un agente que falla lanza excepción.</b> No devuelve un resultado a medias ni un
 * cero: quien lo llamó decide si reintenta, y la postulación se queda donde está.
 */
public interface AgenteSeleccion {

    /** El código de {@code agente}: DATOS_CV, EVIDENCIA_CV, EVALUADOR, POTENCIAL_RIESGO,
     *  PRUEBA_PUESTO o SIMULACION. */
    String codigo();

    void ejecutar(TrabajoIa trabajo);
}
