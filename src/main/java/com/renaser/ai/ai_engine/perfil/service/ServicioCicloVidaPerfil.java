package com.renaser.ai.ai_engine.perfil.service;

/**
 * El final de un perfil: el borrado que pide la persona (ley 29733) y el que aplica el
 * paso del tiempo (parámetro {@code meses_conservar_perfil}).
 */
public interface ServicioCicloVidaPerfil {

    /**
     * Borra el perfil de la persona con todo lo que cuelga de él. Borrado de verdad, no
     * anonimizado: el perfil no sostiene ninguna decisión —no puntúa—, así que no hay
     * nada que conservar. {@code dato_cv} no se toca: eso sí sostiene evaluaciones.
     */
    void borrarPorPersona(Long personaId);
}
