package com.renaser.ai.ai_engine.colaborador.service;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Que nadie acabe siendo jefe de su propio jefe.
 *
 * <p>A reporta a B y B a A deja un organigrama sin cabeza, y ningún informe que suba por la
 * cadena termina. Se comprueba subiendo desde el jefe propuesto: si la subida vuelve a la
 * persona, el cambio cierra un círculo.
 */
public final class JefesSinCirculo {

    private JefesSinCirculo() {}

    /**
     * @param persona quien recibe el jefe
     * @param jefe    el jefe propuesto
     * @param jefeDe  de cada persona a su jefe actual, sin contar el cambio que se prueba
     * @return si asignarlo cerraría un círculo (incluido ser jefe de sí mismo)
     */
    public static <K> boolean creaCirculo(K persona, K jefe, Map<K, K> jefeDe) {
        if (persona == null || jefe == null) {
            return false;
        }
        Set<K> vistos = new HashSet<>();
        K actual = jefe;
        while (actual != null && vistos.add(actual)) {
            if (Objects.equals(actual, persona)) {
                return true;
            }
            actual = jefeDe.get(actual);
        }
        // Un círculo que ya existía entre otros no es culpa de este cambio: se sale sin él.
        return false;
    }
}
