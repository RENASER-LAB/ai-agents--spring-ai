package com.renaser.ai.ai_engine.postulacion.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Las etapas que recorre una postulación, en orden, y por qué momento se entra a cada una (V95).
 *
 * <p>Es lo que necesita el cálculo puro de {@link MaquinaEstados#calcularSiguiente}: la lista sale
 * del recorrido de la vacante ({@code RecorridoDeLaVacante}) y los momentos de entrada, de las
 * piezas que cumplen {@link DefinicionDeEtapa}. Una etapa sin momento propio se entra por el
 * primero que tenga en el catálogo.
 *
 * @param etapas   los códigos de etapa, en el orden de la vacante
 * @param entradas el momento de entrada de las etapas que no entran por el primero
 */
public record Recorrido(List<String> etapas, Map<String, String> entradas) {

    public Recorrido {
        etapas = List.copyOf(etapas);
        entradas = Map.copyOf(entradas);
    }

    /** El momento por el que se entra a la etapa, si no es el primero. */
    public Optional<String> momentoDeEntrada(String etapa) {
        return Optional.ofNullable(entradas.get(etapa));
    }

    /** La etapa que sigue a esta en el recorrido, o vacío si es la última o no está. */
    public Optional<String> despuesDe(String etapa) {
        int donde = etapas.indexOf(etapa);
        return donde < 0 || donde == etapas.size() - 1
                ? Optional.empty() : Optional.of(etapas.get(donde + 1));
    }

    /** Si la etapa está en el recorrido. */
    public boolean tiene(String etapa) {
        return etapas.contains(etapa);
    }

    /** Si la etapa es la última del recorrido. */
    public boolean esLaUltima(String etapa) {
        return !etapas.isEmpty() && etapas.get(etapas.size() - 1).equals(etapa);
    }
}
