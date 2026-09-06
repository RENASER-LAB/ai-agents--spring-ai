package com.renaser.ai.ai_engine.perfil.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoDatos;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoDatos;

/**
 * Lo que el agente {@code DATOS_CV} necesita cuando el currículum viene <b>del perfil</b> y
 * no de una postulación.
 *
 * <p>Es el hermano de {@code PuenteCalificacionIa} para este caso, y existe por lo mismo que
 * aquel: el agente no habla con repositorios, habla con un puente. Aquí no hay puesto ni
 * vacante — solo una persona que subió su currículum —, así que el insumo se arma distinto
 * aunque el prompt sea idéntico.
 */
public interface PuenteLecturaCvPerfil {

    /**
     * El texto del currículum, ya recortado.
     *
     * <p>⚠️ <b>Recortado igual que para los demás agentes</b> (RF-41): sin foto, edad, sexo
     * ni estado civil. Que el currículum venga del perfil en vez de una postulación no
     * cambia nada de eso — el modelo lee lo mismo que leería en el otro camino.
     *
     * @throws IllegalStateException si no hay archivo, si ya se borró, o si del archivo no
     *                               se pudo sacar texto. Nunca devuelve vacío para salir del
     *                               paso: sin texto no hay nada que leer, y eso se cuenta
     *                               como {@code NO_LEGIBLE}, no como una ficha vacía.
     */
    InsumoDatos insumo(Long lecturaId);

    /**
     * Lleva lo leído al perfil y cierra la lectura.
     *
     * <p>⚠️ <b>Una ficha de la que no salió nada se cierra {@code NO_LEGIBLE}, no
     * {@code LISTA}.</b> La pantalla dice «ya está: revisa lo que encontramos», y decir eso
     * sobre un perfil que sigue vacío manda al candidato a buscar algo que no existe.
     */
    void guardar(Long lecturaId, Long ejecucionIaId, ResultadoDatos resultado);

    /** Cierra la lectura como fallida, con el motivo, cuando el agente se agotó. */
    void marcarNoLegible(Long lecturaId, String motivo);
}
