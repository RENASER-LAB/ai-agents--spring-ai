package com.renaser.ai.ai_engine.ai.service;

import com.renaser.ai.ai_engine.ai.model.TrabajoIa;

/**
 * Uno de los agentes que califican una postulación.
 *
 * <p>Son tres y corren en fila: {@code EVIDENCIA_CV} lee el currículum, {@code EVALUADOR}
 * califica las respuestas abiertas y {@code POTENCIAL_RIESGO} arma el Perfil de Talento con
 * lo que dejaron los dos anteriores.
 *
 * <p>Existe la interfaz para que la cola no tenga un {@code switch} con tres nombres dentro:
 * cada agente se registra solo y añadir el cuarto —la prueba del puesto del hito 3— será
 * escribir una clase, no editar la cola.
 *
 * <p><b>Un agente que falla lanza excepción.</b> No devuelve un resultado a medias ni un
 * cero: quien lo llamó decide si reintenta, y la postulación se queda donde está.
 */
public interface AgenteSeleccion {

    /** El código de {@code agente}: EVIDENCIA_CV, EVALUADOR o POTENCIAL_RIESGO. */
    String codigo();

    void ejecutar(TrabajoIa trabajo);
}
