package com.renaser.ai.ai_engine.postulacion.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El bot y el portal dicen lo mismo (V73, punto 26 de la spec).
 *
 * <p>Los textos viven aquí y el portal los pinta desde su copia; los dos se comparan con el mismo
 * contrato, {@code docs/contratos/textos-del-momento.json}, que está igual en los dos
 * repositorios (en el portal lo comprueba {@code estados.test.ts}). Si alguien cambia una frase
 * en un solo sitio, esta prueba —o la del portal— falla.
 */
@DisplayName("Los textos de cada momento, iguales al contrato que comparte con el portal")
class TextosDelMomentoTest {

    private static final JsonNode CONTRATO = leer();

    private static JsonNode leer() {
        try {
            return new ObjectMapper().readTree(Files.readString(
                    Path.of("docs/contratos/textos-del-momento.json")));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No se pudo leer el contrato de los textos", e);
        }
    }

    @Test
    @DisplayName("Los dieciocho estados, en su orden, con el mismo título y el mismo texto")
    void losDieciochoEstados() {
        List<String> enElContrato = new ArrayList<>();
        CONTRATO.get("estados").propertyNames().forEach(enElContrato::add);
        assertThat(TextosDelMomento.porEstado().keySet()).containsExactlyElementsOf(enElContrato);

        TextosDelMomento.porEstado().forEach((estado, texto) -> {
            JsonNode suyo = CONTRATO.get("estados").get(estado);
            assertThat(texto.titulo()).as("título de %s", estado).isEqualTo(suyo.get("titulo").asString());
            assertThat(texto.texto()).as("texto de %s", estado).isEqualTo(suyo.get("texto").asString());
        });
    }

    @Test
    @DisplayName("Las variantes: el cuestionario técnico, la prueba sin completar y un estado desconocido")
    void lasVariantes() {
        JsonNode cuestionario = CONTRATO.get("cuestionarioTecnico");
        var listo = TextosDelMomento.de("PRUEBA_TURNO_CANDIDATO", "CUESTIONARIO_TECNICO", false);
        assertThat(listo.titulo()).isEqualTo(cuestionario.get("PRUEBA_TURNO_CANDIDATO").get("titulo").asString());
        assertThat(listo.texto()).isEqualTo(cuestionario.get("PRUEBA_TURNO_CANDIDATO").get("texto").asString());
        var calificando = TextosDelMomento.de("PRUEBA_CALIFICANDO", "CUESTIONARIO_TECNICO", false);
        assertThat(calificando.titulo()).isEqualTo(cuestionario.get("PRUEBA_CALIFICANDO").get("titulo").asString());
        assertThat(calificando.texto()).isEqualTo(cuestionario.get("PRUEBA_CALIFICANDO").get("texto").asString());

        var sinCompletar = TextosDelMomento.de("PRUEBA_TURNO_CANDIDATO", "PRUEBA_PROPIA", true);
        assertThat(sinCompletar.titulo()).isEqualTo(CONTRATO.get("pruebaSinCompletar").get("titulo").asString());
        assertThat(sinCompletar.texto()).isEqualTo(CONTRATO.get("pruebaSinCompletar").get("texto").asString());

        var desconocido = TextosDelMomento.de("NO_EXISTE", null, false);
        assertThat(desconocido.titulo()).isEqualTo(CONTRATO.get("desconocido").get("titulo").asString());
        assertThat(desconocido.texto()).isEqualTo(CONTRATO.get("desconocido").get("texto").asString());
    }

    @Test
    @DisplayName("AC-17 · «Estamos revisando tu postulación» no dice «No tienes que hacer nada.»: lo añade el recuadro")
    void laEntradaNoRepiteLaFrase() {
        // El recuadro de espera del portal ya añade «No tienes que hacer nada.» detrás del texto;
        // con la frase también dentro, salía dos veces.
        assertThat(TextosDelMomento.porEstado().get("POSTULADA").texto())
                .doesNotContain("No tienes que hacer nada");
    }

    @Test
    @DisplayName("Sin instrumento, o con la prueba de siempre, manda el texto del estado")
    void sinInstrumento() {
        assertThat(TextosDelMomento.de("PERFIL_CALIFICANDO", null, false).titulo())
                .isEqualTo("Estamos calificando tus respuestas");
        // Sin completar solo cuenta en el turno de la prueba.
        assertThat(TextosDelMomento.de("PRUEBA_CALIFICANDO", null, true).titulo())
                .isEqualTo("Estamos calificando tu prueba");
    }
}
