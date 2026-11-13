package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.InsumoBusquedaCv;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCalificacionIa.ResultadoDeLaBusquedaIa;

/**
 * La puerta del agente de «Qué buscar en el CV» (V80) a la selección, como
 * {@link PuenteCalificacionIa} lo es para los demás: le da lo que necesita y guarda lo que
 * devuelve, con la red de seguridad de este lado.
 */
public interface PuenteBusquedaCv {

    /**
     * El puesto, el CV anonimizado y las líneas vigentes de la vacante.
     *
     * @return nulo si la vacante no busca nada: no hay que llamar a la IA
     * @throws com.renaser.ai.ai_engine.postulacion.service.CvSinTexto si del CV no sale texto;
     *         quien llama lo marca con {@link #marcarSinTexto} y no reintenta
     */
    InsumoBusquedaCv insumo(Long postulacionId);

    /**
     * Deja la búsqueda de esa persona como «CV sin texto», con su motivo, para las líneas
     * vigentes. Sin llamar a la IA: el archivo no va a cambiar solo.
     */
    void marcarSinTexto(Long postulacionId, String motivo);

    /**
     * Guarda lo encontrado, comprobando cada frase en el texto del CV.
     *
     * <ul>
     *   <li>Una línea TIENE cuya frase no está en el CV (sin mirar mayúsculas, tildes ni espacios
     *       repetidos) se guarda como MENCIONA y sin frase.
     *   <li>Una línea que falte en la respuesta se guarda como NO_APARECE; una que sobre se
     *       ignora.
     *   <li>En una rebúsqueda, se recalculan la nota del CV, la del Perfil Integral y el grupo de
     *       quien ya los tenía, sin publicar eventos: nadie cambia de etapa por esto.
     * </ul>
     *
     * @return false si las líneas cambiaron mientras se buscaba: el resultado se descarta y no se
     *         guarda nada
     * @throws IllegalStateException si la respuesta no se puede leer: el intento cuenta como
     *         fallido y la cola lo reintenta
     */
    boolean guardar(Long postulacionId, Long ejecucionIaId, ResultadoDeLaBusquedaIa resultado,
                    int versionLineas, boolean rebusqueda);
}
