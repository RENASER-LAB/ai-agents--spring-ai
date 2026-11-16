package com.renaser.ai.ai_engine.organizacion.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.renaser.ai.ai_engine.organizacion.service.EnlacesDeLaEmpresa.MENSAJE_ENLACE;
import static com.renaser.ai.ai_engine.organizacion.service.EnlacesDeLaEmpresa.MENSAJE_VIDEO;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las reglas de los enlaces de la presentación (V81). Son las mismas, con las mismas palabras,
 * que {@code src/dominio/presentacionEmpresa.ts} del panel: un caso nuevo aquí va también allí.
 */
@DisplayName("Los enlaces de la presentación de una empresa")
class EnlacesDeLaEmpresaTest {

    @Nested
    @DisplayName("Normalizar")
    class Normalizar {

        @Test
        @DisplayName("sin http:// ni https:// se le pone https:// delante (AC-02: «renaser.pe»)")
        void poneHttps() {
            assertThat(EnlacesDeLaEmpresa.normalizar("renaser.pe")).isEqualTo("https://renaser.pe");
            assertThat(EnlacesDeLaEmpresa.normalizar("  www.renaser.pe/empleos  "))
                    .isEqualTo("https://www.renaser.pe/empleos");
            assertThat(EnlacesDeLaEmpresa.normalizar("renaser.pe:8080/x"))
                    .isEqualTo("https://renaser.pe:8080/x");
        }

        @Test
        @DisplayName("vacío o solo espacios es «sin dato»")
        void vacioEsNulo() {
            assertThat(EnlacesDeLaEmpresa.normalizar(null)).isNull();
            assertThat(EnlacesDeLaEmpresa.normalizar("   ")).isNull();
        }

        @Test
        @DisplayName("otro esquema se deja como está, para que la comprobación lo rechace")
        void otroEsquemaNoSeEsconde() {
            assertThat(EnlacesDeLaEmpresa.normalizar("javascript:alert(1)"))
                    .isEqualTo("javascript:alert(1)");
            assertThat(EnlacesDeLaEmpresa.normalizar("http://renaser.pe")).isEqualTo("http://renaser.pe");
        }
    }

    @Nested
    @DisplayName("La página web")
    class PaginaWeb {

        @ParameterizedTest
        @ValueSource(strings = {"javascript:alert(1)", "data:text/html,hola", "ftp://renaser.pe",
                "https://localhost", "https://usuario:clave@renaser.pe", "https://renaser .pe",
                "mailto:hola@renaser.pe", "https://.pe", "https://"})
        @DisplayName("lo que no es http o https con dominio no vale (AC-03)")
        void noValen(String crudo) {
            assertThat(EnlacesDeLaEmpresa.errorDelEnlace(EnlacesDeLaEmpresa.normalizar(crudo)))
                    .isEqualTo(MENSAJE_ENLACE);
        }

        @Test
        @DisplayName("más de 500 caracteres no vale")
        void demasiadoLargo() {
            String largo = "https://renaser.pe/" + "a".repeat(482);
            assertThat(largo).hasSize(501);
            assertThat(EnlacesDeLaEmpresa.errorDelEnlace(largo)).isEqualTo(MENSAJE_ENLACE);
            assertThat(EnlacesDeLaEmpresa.errorDelEnlace(largo.substring(0, 500))).isNull();
        }

        @Test
        @DisplayName("http y https con dominio valen; vacío también")
        void valen() {
            assertThat(EnlacesDeLaEmpresa.errorDelEnlace("https://renaser.pe")).isNull();
            assertThat(EnlacesDeLaEmpresa.errorDelEnlace("http://www.renaser.pe/a?b=1#c")).isNull();
            assertThat(EnlacesDeLaEmpresa.errorDelEnlace(null)).isNull();
        }
    }

    @Nested
    @DisplayName("Las redes")
    class Redes {

        @Test
        @DisplayName("un Facebook en «Instagram» dice que no es de Instagram (AC-05)")
        void otraRed() {
            assertThat(EnlacesDeLaEmpresa.errorDeLaRed(RedSocial.INSTAGRAM,
                    "https://www.facebook.com/renaser")).isEqualTo("Este enlace no es de Instagram");
        }

        @Test
        @DisplayName("cada red con su dominio, con www. o m. delante")
        void suDominio() {
            assertThat(EnlacesDeLaEmpresa.errorDeLaRed(RedSocial.INSTAGRAM,
                    "https://www.instagram.com/renaser")).isNull();
            assertThat(EnlacesDeLaEmpresa.errorDeLaRed(RedSocial.FACEBOOK, "https://m.facebook.com/x")).isNull();
            assertThat(EnlacesDeLaEmpresa.errorDeLaRed(RedSocial.FACEBOOK, "https://fb.com/x")).isNull();
            assertThat(EnlacesDeLaEmpresa.errorDeLaRed(RedSocial.LINKEDIN,
                    "https://www.linkedin.com/company/renaser")).isNull();
            assertThat(EnlacesDeLaEmpresa.errorDeLaRed(RedSocial.TIKTOK, "https://www.tiktok.com/@renaser")).isNull();
            assertThat(EnlacesDeLaEmpresa.errorDeLaRed(RedSocial.YOUTUBE, "https://youtu.be/abc123DEF45")).isNull();
        }

        @Test
        @DisplayName("un dominio que solo se parece no cuela")
        void parecido() {
            assertThat(EnlacesDeLaEmpresa.errorDeLaRed(RedSocial.INSTAGRAM, "https://noinstagram.com/x"))
                    .isEqualTo("Este enlace no es de Instagram");
            assertThat(EnlacesDeLaEmpresa.errorDeLaRed(RedSocial.TIKTOK, "javascript:alert(1)"))
                    .isEqualTo(MENSAJE_ENLACE);
        }
    }

    @Nested
    @DisplayName("Los videos")
    class Videos {

        @Test
        @DisplayName("un video de Facebook no vale (AC-04)")
        void facebook() {
            assertThat(EnlacesDeLaEmpresa.errorDelVideo("https://www.facebook.com/renaser/videos/123"))
                    .isEqualTo(MENSAJE_VIDEO);
        }

        @Test
        @DisplayName("un canal de YouTube no es un video")
        void canal() {
            assertThat(EnlacesDeLaEmpresa.errorDelVideo("https://www.youtube.com/@renaser"))
                    .isEqualTo(MENSAJE_VIDEO);
        }

        @Test
        @DisplayName("lo que ni siquiera es un enlace dice lo de la dirección web")
        void noEsEnlace() {
            assertThat(EnlacesDeLaEmpresa.errorDelVideo("javascript:alert(1)")).isEqualTo(MENSAJE_ENLACE);
        }

        @Test
        @DisplayName("YouTube y Vimeo valen, también sin https:// delante")
        void valen() {
            assertThat(EnlacesDeLaEmpresa.errorDelVideo(
                    EnlacesDeLaEmpresa.normalizar("youtu.be/abc123DEF45"))).isNull();
            assertThat(EnlacesDeLaEmpresa.errorDelVideo("https://vimeo.com/123456789/abcdef1234")).isNull();
        }
    }
}
