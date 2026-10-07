package com.renaser.ai.ai_engine.prueba.service;

import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarEntregable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un entregable que llega por la API sin «obligatorio» o sin «toda la prueba» (o con ellos en
 * null) se lee con su valor por defecto en vez de reventar en un 500: Jackson 3 no deja un null
 * en un primitivo.
 */
@DisplayName("El entregable que llega sin sus sí/no")
class EntregableQueLlegaSinSiNoTest {

    private final JsonMapper json = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    @Test
    @DisplayName("Omitidos: es obligatorio y no cubre toda la prueba (cae en «Elige qué cubre»)")
    void omitidos() {
        GuardarEntregable e = json.readValue("{\"nombre\":\"Suelto\",\"formato\":\"ARCHIVO\"}",
                GuardarEntregable.class);

        assertThat(e.obligatorio()).isTrue();
        assertThat(e.todaLaPrueba()).isFalse();
        assertThat(ReglasDeLaPrueba.formaDelAlcance(e.preguntaId(), e.todaLaPrueba(), e.cubre()))
                .singleElement().asString().contains("Elige qué cubre");
    }

    @Test
    @DisplayName("En null: lo mismo que omitidos")
    void enNull() {
        GuardarEntregable e = json.readValue("""
                {"nombre":"Sin formato","obligatorio":null,"todaLaPrueba":null}""", GuardarEntregable.class);

        assertThat(e.obligatorio()).isTrue();
        assertThat(e.todaLaPrueba()).isFalse();
        assertThat(ReglasDeLaPrueba.formaDelEntregable(e.nombre(), e.formato())).isNotEmpty();
    }

    @Test
    @DisplayName("Dichos: se respetan tal cual")
    void dichos() {
        GuardarEntregable e = json.readValue("""
                {"nombre":"Video","formato":"ENLACE","obligatorio":false,"todaLaPrueba":true,"cubre":[]}""",
                GuardarEntregable.class);

        assertThat(e.obligatorio()).isFalse();
        assertThat(e.todaLaPrueba()).isTrue();
        assertThat(e.cubre()).isEmpty();
    }

    @Test
    @DisplayName("El atajo de un general de toda la prueba sigue igual")
    void elAtajo() {
        GuardarEntregable e = new GuardarEntregable("Tablero", null, "ARCHIVO", false, null);

        assertThat(e.obligatorio()).isFalse();
        assertThat(e.todaLaPrueba()).isTrue();
        assertThat(e.cubre()).isEqualTo(List.of());
    }
}
