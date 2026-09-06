package com.renaser.ai.ai_engine.archivo.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lo que se acepta como foto o portada, que no es lo mismo que se acepta como currículum.
 *
 * <p>Se comprueba <b>extensión y tipo declarado a la vez</b> a propósito: un PDF renombrado a
 * {@code .png} trae la extensión buena y el tipo malo, y quien manda el tipo a mano trae lo
 * contrario. Con una sola de las dos comprobaciones cualquiera de los dos entra.
 *
 * <p>Y el límite son 2 MB, no los 10 del currículum: una foto de perfil se pinta a 96 píxeles.
 */
@DisplayName("La foto y la portada del perfil")
class TiposDeArchivoImagenTest {

    @ParameterizedTest(name = "{0} ({1})")
    @CsvSource({
            "foto.jpg,  image/jpeg",
            "foto.JPEG, image/jpeg",
            "foto.png,  image/png",
            "portada.webp, image/webp"
    })
    @DisplayName("Los cuatro formatos que se aceptan entran, y la extensión no distingue mayúsculas")
    void losCuatroEntran(String nombre, String tipo) {
        assertThatCode(() -> TiposDeArchivo.exigirImagen(nombre, tipo, 500_000L))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} ({1})")
    @CsvSource({
            // El GIF y el SVG se quedan fuera queriendo: uno anima y el otro lleva scripts.
            "animacion.gif, image/gif",
            "dibujo.svg,    image/svg+xml",
            // Un currículum no es una portada, aunque sea un archivo perfectamente válido.
            "curriculum.pdf, application/pdf",
            // La extensión buena con el tipo malo: un PDF al que le cambiaron el nombre.
            "trampa.png,     application/pdf",
            // El tipo bueno con la extensión mala: quien manda la cabecera a mano.
            "trampa.pdf,     image/png",
            "sinextension,   image/png"
    })
    @DisplayName("Lo que no es JPG, PNG o WebP se rechaza, y da igual por cuál de los dos lados falle")
    void loDemasSeRechaza(String nombre, String tipo) {
        assertThatThrownBy(() -> TiposDeArchivo.exigirImagen(nombre, tipo, 500_000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JPG, PNG o WebP");
    }

    @Test
    @DisplayName("Por encima de 2 MB se rechaza, y el mensaje dice qué hacer")
    void masDeDosMegasNo() {
        assertThatThrownBy(() -> TiposDeArchivo.exigirImagen(
                "foto.jpg", "image/jpeg", TiposDeArchivo.MAXIMO_IMAGEN + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 MB");
    }

    @Test
    @DisplayName("Justo 2 MB entra: el límite es «más de», no «desde»")
    void justoDosMegasSi() {
        assertThatCode(() -> TiposDeArchivo.exigirImagen(
                "foto.jpg", "image/jpeg", TiposDeArchivo.MAXIMO_IMAGEN))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("El formato se mira antes que el peso: un GIF de 5 MB se queja del formato")
    void elFormatoManda() {
        // No es cosmético. Si se quejara del peso, quien sube un GIF lo comprimiría una y
        // otra vez sin que entrara nunca.
        assertThatThrownBy(() -> TiposDeArchivo.exigirImagen(
                "animacion.gif", "image/gif", 5L * 1024 * 1024))
                .hasMessageContaining("JPG, PNG o WebP");
    }
}
