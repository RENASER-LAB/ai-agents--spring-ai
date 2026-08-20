package com.renaser.ai.ai_engine.perfilintegral.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Comprueba que la respuesta a un ítem del banco v3 tenga la forma que le toca.
 *
 * <p><b>Por qué existe esta clase.</b> El detalle de la respuesta se guarda como {@code jsonb}
 * (ver la migración V21), y eso significa que la base de datos ya no comprueba nada: no hay
 * clave foránea que impida apuntar a una opción de otra pregunta, ni forma de exigir que un
 * SJT-R traiga calificaciones y no una lista. Todo eso pasa a ser código, y este es el código.
 *
 * <p>Mientras el detalle no sea una tabla de verdad, <b>esta clase es la única cosa entre una
 * respuesta con mala forma y una nota calculada sobre ella</b>. Una respuesta mal formada que
 * pase de aquí no da error: da un puntaje.
 *
 * <p>Los mensajes van dirigidos a quien programa, no al candidato: si alguno llega a una
 * pantalla es que el portal está enviando algo que no debía.
 */
public final class ValidadorDetalleV3 {

    private ValidadorDetalleV3() {
    }

    /**
     * @param tipo          el formato del ítem, tal como está en {@code pregunta.tipo}
     * @param opcionesDeLaPregunta los ids de las opciones que esa pregunta tiene de verdad
     * @param detalle       lo que mandó el portal
     * @throws IllegalArgumentException si la forma no corresponde al formato
     */
    public static void validar(String tipo, Set<Long> opcionesDeLaPregunta,
                               Map<String, Object> detalle) {
        if (detalle == null || detalle.isEmpty()) {
            throw new IllegalArgumentException(
                    "Un ítem de tipo " + tipo + " necesita un detalle con su respuesta");
        }
        switch (tipo) {
            case "EF-4" -> ef4(opcionesDeLaPregunta, detalle);
            case "SJT-R" -> sjtR(opcionesDeLaPregunta, detalle);
            case "SEC" -> sec(opcionesDeLaPregunta, detalle);
            case "INV", "DE" -> marcadas(tipo, opcionesDeLaPregunta, detalle);
            case "CD" -> campos(detalle);
            default -> throw new IllegalArgumentException(
                    "El tipo " + tipo + " no se responde con detalle");
        }
    }

    /** Los formatos que necesitan detalle. El resto se sigue respondiendo con opción o texto. */
    public static boolean necesitaDetalle(String tipo) {
        return List.of("EF-4", "SJT-R", "SEC", "INV", "DE", "CD").contains(tipo);
    }

    private static void ef4(Set<Long> suyas, Map<String, Object> detalle) {
        Long mas = comoId(detalle.get("mas"), "mas");
        Long menos = comoId(detalle.get("menos"), "menos");
        if (mas == null || menos == null) {
            throw new IllegalArgumentException(
                    "Un EF-4 se responde marcando la opción más parecida y la menos: "
                            + "faltan «mas» o «menos»");
        }
        // El documento lo dice expreso: no puede ser la misma. Sin esta comprobación el bruto
        // daría siempre 0 y el ítem parecería respondido.
        if (mas.equals(menos)) {
            throw new IllegalArgumentException(
                    "La opción más parecida y la menos no pueden ser la misma");
        }
        exigirQueSean(suyas, List.of(mas, menos));
    }

    private static void sjtR(Set<Long> suyas, Map<String, Object> detalle) {
        Map<?, ?> calificaciones = comoMapa(detalle.get("calificaciones"), "calificaciones");
        if (calificaciones.isEmpty()) {
            throw new IllegalArgumentException("Un SJT-R se responde calificando cada opción");
        }
        for (Map.Entry<?, ?> e : calificaciones.entrySet()) {
            Long opcion = comoId(e.getKey(), "calificaciones");
            Long nota = comoId(e.getValue(), "calificaciones");
            if (opcion == null || nota == null) {
                throw new IllegalArgumentException(
                        "Las calificaciones de un SJT-R son «id de opción»: número");
            }
            exigirQueSean(suyas, List.of(opcion));
            if (nota < 1 || nota > 5) {
                throw new IllegalArgumentException(
                        "Un SJT-R se califica del 1 al 5, y llegó un " + nota);
            }
        }
    }

    private static void sec(Set<Long> suyas, Map<String, Object> detalle) {
        List<Long> orden = comoIds(detalle.get("orden"), "orden");
        // Tiene que ser exactamente sus pasos, todos y sin repetir: si faltara uno o se colara
        // otro, los pares que cuenta la fórmula no significarían nada.
        if (orden.size() != suyas.size() || !Set.copyOf(orden).equals(suyas)) {
            throw new IllegalArgumentException(
                    "Un SEC se responde ordenando todos sus pasos, una vez cada uno: "
                            + "esperaba " + suyas.size() + " y llegaron " + orden.size());
        }
    }

    private static void marcadas(String tipo, Set<Long> suyas, Map<String, Object> detalle) {
        List<Long> marcadas = comoIds(detalle.get("marcadas"), "marcadas");
        if (Set.copyOf(marcadas).size() != marcadas.size()) {
            throw new IllegalArgumentException(
                    "Un " + tipo + " no puede traer el mismo elemento marcado dos veces");
        }
        exigirQueSean(suyas, marcadas);
    }

    private static void campos(Map<String, Object> detalle) {
        Map<?, ?> campos = comoMapa(detalle.get("campos"), "campos");
        if (campos.isEmpty()) {
            throw new IllegalArgumentException("Un CD se responde llenando los campos del caso");
        }
    }

    /** Lo que la clave foránea haría sola si esto fuera una tabla. */
    private static void exigirQueSean(Set<Long> suyas, Collection<Long> usadas) {
        for (Long id : usadas) {
            if (!suyas.contains(id)) {
                throw new IllegalArgumentException(
                        "La opción " + id + " no es de esta pregunta");
            }
        }
    }

    private static Long comoId(Object valor, String campo) {
        if (valor instanceof Number n) {
            return n.longValue();
        }
        if (valor instanceof String s) {
            try {
                return Long.valueOf(s.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("«" + campo + "» esperaba un número y llegó: " + s);
            }
        }
        return null;
    }

    private static List<Long> comoIds(Object valor, String campo) {
        if (!(valor instanceof Collection<?> c)) {
            throw new IllegalArgumentException("«" + campo + "» tiene que ser una lista");
        }
        return c.stream().map(v -> {
            Long id = comoId(v, campo);
            if (id == null) {
                throw new IllegalArgumentException("«" + campo + "» solo admite ids de opción");
            }
            return id;
        }).toList();
    }

    private static Map<?, ?> comoMapa(Object valor, String campo) {
        if (!(valor instanceof Map<?, ?> m)) {
            throw new IllegalArgumentException("«" + campo + "» tiene que ser un objeto");
        }
        return m;
    }
}
