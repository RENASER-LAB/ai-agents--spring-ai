package com.renaser.ai.ai_engine.perfil.service.impl;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Los logros clave del perfil (V71): hasta tres frases cortas que escribe el candidato con lo
 * que ha conseguido. Aquí vive cómo se limpian al guardar y cómo pasan de la lista que ve la
 * pantalla al texto JSON de la columna, y de vuelta.
 *
 * <p>Los escribe solo su dueño. Ni la lectura del currículum ni ninguna IA los proponen ni los
 * leen: no entran en ningún prompt y no puntúan.
 */
@Slf4j
final class LogrosClave {

    /** Cuántos caben. La columna lo impone también con un CHECK. */
    static final int MAXIMO = 3;
    /** Cuántos caracteres tiene cada uno como mucho, ya limpio. */
    static final int LARGO_MAXIMO = 100;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> LISTA = new TypeReference<>() { };

    private LogrosClave() {
    }

    /**
     * Lo que llegó del formulario, listo para guardar: cada uno recortado y con los saltos de
     * línea hechos espacios, sin los vacíos y en el mismo orden en que llegaron. Si se llenó
     * la caja 1 y la 3, quedan como primero y segundo. Los repetidos se quedan tal cual.
     *
     * <p>El tope de largo se mide sobre el texto limpio, que es lo que se guarda: unos espacios
     * de más alrededor no hacen que un logro de 100 caracteres rebote.
     *
     * @throws IllegalArgumentException (400) si llegan más de tres, o uno de más de cien
     *                                  caracteres
     */
    static List<String> limpiar(List<String> crudos) {
        if (crudos.size() > MAXIMO) {
            throw new IllegalArgumentException("Puedes poner como mucho " + MAXIMO
                    + " logros clave");
        }
        List<String> limpios = new ArrayList<>();
        for (String crudo : crudos) {
            String limpio = crudo == null ? "" : enUnaLinea(crudo).strip();
            if (limpio.length() > LARGO_MAXIMO) {
                throw new IllegalArgumentException("Cada logro clave tiene como mucho "
                        + LARGO_MAXIMO + " caracteres");
            }
            if (!limpio.isEmpty()) {
                limpios.add(limpio);
            }
        }
        return List.copyOf(limpios);
    }

    /** La lista como va a la columna. Sin ninguno, {@code null}: es lo que dice «no tiene». */
    static String aJson(List<String> logros) {
        return logros.isEmpty() ? null : JSON.writeValueAsString(logros);
    }

    /**
     * La columna como la pinta la pantalla: nunca {@code null}. Un valor que no se pueda leer
     * se pinta como ninguno en vez de tumbar el perfil entero.
     */
    static List<String> deJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> leidos = JSON.readValue(json, LISTA);
            return leidos == null ? List.of()
                    : leidos.stream().filter(Objects::nonNull).toList();
        } catch (RuntimeException e) {
            log.warn("Los logros clave guardados no se pudieron leer: {}", e.getMessage());
            return List.of();
        }
    }

    /** Un logro es una línea: lo que se pegue con saltos se queda con espacios. */
    private static String enUnaLinea(String texto) {
        return texto.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ');
    }
}
