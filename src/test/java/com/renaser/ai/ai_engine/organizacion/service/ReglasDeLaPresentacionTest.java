package com.renaser.ai.ai_engine.organizacion.service;

import com.renaser.ai.ai_engine.organizacion.dto.DtosPresentacion.GuardarPresentacion;
import com.renaser.ai.ai_engine.organizacion.service.ReglasDeLaPresentacion.PresentacionLimpia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@DisplayName("Qué se puede guardar en la presentación de una empresa")
class ReglasDeLaPresentacionTest {

    private static final int ESTE_ANIO = 2026;

    private static GuardarPresentacion con(String proposito, String video, String web,
                                           Map<String, String> redes) {
        return new GuardarPresentacion(null, null, null, null, proposito, null, video, web, redes);
    }

    @Test
    @DisplayName("AC-02: lo guardado sale limpio, con la web completada con https://")
    void loDeAc02() {
        PresentacionLimpia limpia = ReglasDeLaPresentacion.limpiar(con("  Que nadie se quede sin pan.  ",
                "https://www.youtube.com/watch?v=abc123DEF45", "renaser.pe",
                Map.of("INSTAGRAM", "https://www.instagram.com/renaser")), ESTE_ANIO);

        assertThat(limpia.propositoEmpresa()).isEqualTo("Que nadie se quede sin pan.");
        assertThat(limpia.videoPresentacion()).isEqualTo("https://www.youtube.com/watch?v=abc123DEF45");
        assertThat(limpia.paginaWeb()).isEqualTo("https://renaser.pe");
        assertThat(limpia.redes()).containsEntry(RedSocial.INSTAGRAM, "https://www.instagram.com/renaser");
    }

    @Test
    @DisplayName("todo vacío es válido y se guarda como «sin dato»")
    void todoVacio() {
        PresentacionLimpia limpia = ReglasDeLaPresentacion.limpiar(new GuardarPresentacion(
                "  ", "", null, " ", "", List.of(" ", ""), "  ", "", Map.of("FACEBOOK", " ")), ESTE_ANIO);

        assertThat(limpia.sector()).isNull();
        assertThat(limpia.tamano()).isNull();
        assertThat(limpia.quienesSomos()).isNull();
        assertThat(limpia.propositoEmpresa()).isNull();
        assertThat(limpia.videoPresentacion()).isNull();
        assertThat(limpia.paginaWeb()).isNull();
        assertThat(limpia.beneficios()).isEmpty();
        assertThat(limpia.redes()).isEmpty();
    }

    @Test
    @DisplayName("AC-06: un propósito de 1500 vale; de 1501, «El propósito admite hasta 1500 caracteres»")
    void propositoDe1500() {
        assertThat(ReglasDeLaPresentacion.limpiar(con("a".repeat(1500), null, null, null), ESTE_ANIO)
                .propositoEmpresa()).hasSize(1500);

        assertThatThrownBy(() -> ReglasDeLaPresentacion.limpiar(con("a".repeat(1501), null, null, null),
                ESTE_ANIO))
                .isInstanceOf(PresentacionInvalidaException.class)
                .hasMessage("El propósito admite hasta 1500 caracteres");
    }

    @Test
    @DisplayName("varios errores a la vez salen todos, cada uno en su campo, y no se guarda nada")
    void variosErrores() {
        Map<String, String> redes = new LinkedHashMap<>();
        redes.put("INSTAGRAM", "https://www.facebook.com/renaser");
        redes.put("X", "https://x.com/renaser");
        PresentacionInvalidaException error = catchThrowableOfType(PresentacionInvalidaException.class,
                () -> ReglasDeLaPresentacion.limpiar(con(null,
                        "https://www.facebook.com/renaser/videos/123", "javascript:alert(1)", redes),
                        ESTE_ANIO));

        assertThat(error.getMessage()).isEqualTo(PresentacionInvalidaException.VARIOS);
        assertThat(error.getErrores())
                .containsEntry("paginaWeb", "Escribe una dirección web que empiece por https://")
                .containsEntry("videoPresentacion", "Pega el enlace de un video de YouTube o de Vimeo")
                .containsEntry("INSTAGRAM", "Este enlace no es de Instagram")
                .containsEntry("X", ReglasDeLaPresentacion.MENSAJE_RED_DESCONOCIDA);
    }

    @Test
    @DisplayName("el año, entre 1800 y el año actual")
    void anio() {
        assertThat(ReglasDeLaPresentacion.limpiar(new GuardarPresentacion(null, null, 1800, null, null,
                null, null, null, null), ESTE_ANIO).anioFundacion()).isEqualTo(1800);
        assertThat(ReglasDeLaPresentacion.limpiar(new GuardarPresentacion(null, null, ESTE_ANIO, null,
                null, null, null, null, null), ESTE_ANIO).anioFundacion()).isEqualTo(ESTE_ANIO);
        for (int fuera : new int[] {1799, ESTE_ANIO + 1}) {
            PresentacionInvalidaException error = catchThrowableOfType(PresentacionInvalidaException.class,
                    () -> ReglasDeLaPresentacion.limpiar(new GuardarPresentacion(null, null, fuera,
                            null, null, null, null, null, null), ESTE_ANIO));
            assertThat(error.getErrores()).containsEntry("anioFundacion",
                    "Escribe un año entre 1800 y 2026");
        }
    }

    @Test
    @DisplayName("el tamaño es uno de los seis rangos")
    void tamano() {
        assertThat(ReglasDeLaPresentacion.limpiar(new GuardarPresentacion(null, "DE_51_A_200", null,
                null, null, null, null, null, null), ESTE_ANIO).tamano()).isEqualTo("DE_51_A_200");
        PresentacionInvalidaException error = catchThrowableOfType(PresentacionInvalidaException.class,
                () -> ReglasDeLaPresentacion.limpiar(new GuardarPresentacion(null, "MUCHOS", null, null,
                        null, null, null, null, null), ESTE_ANIO));
        assertThat(error.getErrores()).containsKey("tamano");
    }

    @Test
    @DisplayName("el sector, hasta 60; «Quiénes somos», hasta 1500")
    void textos() {
        PresentacionInvalidaException error = catchThrowableOfType(PresentacionInvalidaException.class,
                () -> ReglasDeLaPresentacion.limpiar(new GuardarPresentacion("s".repeat(61), null, null,
                        "q".repeat(1501), null, null, null, null, null), ESTE_ANIO));
        assertThat(error.getErrores())
                .containsEntry("sector", ReglasDeLaPresentacion.MENSAJE_SECTOR)
                .containsEntry("quienesSomos", ReglasDeLaPresentacion.MENSAJE_QUIENES);
    }

    @Test
    @DisplayName("los beneficios: sin vacíos ni repetidos, hasta 12 y de 40 caracteres")
    void beneficios() {
        assertThat(ReglasDeLaPresentacion.limpiar(new GuardarPresentacion(null, null, null, null, null,
                List.of(" Seguro EPS ", "seguro eps", "", "Comedor"), null, null, null), ESTE_ANIO)
                .beneficios()).containsExactly("Seguro EPS", "Comedor");

        List<String> trece = new ArrayList<>();
        for (int i = 1; i <= 13; i++) {
            trece.add("Beneficio " + i);
        }
        assertThat(catchThrowableOfType(PresentacionInvalidaException.class,
                () -> ReglasDeLaPresentacion.limpiar(new GuardarPresentacion(null, null, null, null, null,
                        trece, null, null, null), ESTE_ANIO)).getErrores())
                .containsEntry("beneficios", ReglasDeLaPresentacion.MENSAJE_BENEFICIOS);
        assertThat(catchThrowableOfType(PresentacionInvalidaException.class,
                () -> ReglasDeLaPresentacion.limpiar(new GuardarPresentacion(null, null, null, null, null,
                        List.of("b".repeat(41)), null, null, null), ESTE_ANIO)).getErrores())
                .containsEntry("beneficios", ReglasDeLaPresentacion.MENSAJE_BENEFICIO);
    }

    @Test
    @DisplayName("las etiquetas se guardan como texto: nada se interpreta")
    void etiquetasTalCual() {
        assertThat(ReglasDeLaPresentacion.limpiar(con("<script>alert(1)</script>", null, null, null),
                ESTE_ANIO).propositoEmpresa()).isEqualTo("<script>alert(1)</script>");
    }
}
