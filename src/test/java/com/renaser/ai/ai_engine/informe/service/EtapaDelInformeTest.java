package com.renaser.ai.ai_engine.informe.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Cómo se llama lo que el Perfil Integral deja por verificar, según el recorrido (V95). */
@DisplayName("Lo pendiente tras el Perfil Integral")
class EtapaDelInformeTest {

    @Test
    @DisplayName("nombra la etapa que sigue al Perfil Integral en el recorrido, venga en el orden que venga")
    void laEtapaSiguiente() {
        assertThat(EtapaDelInforme.pendienteTrasElPerfil(List.of("PERFIL_INTEGRAL", "PRUEBA_PUESTO", "DECISION")))
                .isEqualTo("Por verificar en la prueba del puesto")
                .isEqualTo(EtapaDelInforme.EVALUACION.pendiente());
        assertThat(EtapaDelInforme.pendienteTrasElPerfil(List.of("PERFIL_INTEGRAL", "SIMULACION", "PRUEBA_PUESTO")))
                .isEqualTo("Por verificar en la simulación de trabajo");
        assertThat(EtapaDelInforme.pendienteTrasElPerfil(List.of("PERFIL_INTEGRAL", "VALIDACION")))
                .isEqualTo("Por verificar en la validación práctica");
        assertThat(EtapaDelInforme.pendienteTrasElPerfil(List.of("PERFIL_INTEGRAL", "DECISION", "PRUEBA_PUESTO")))
                .isEqualTo("Por verificar en la decisión");
    }

    @Test
    @DisplayName("sin etapa detrás del Perfil Integral, o sin recorrido, lo pendiente es para la entrevista")
    void sinEtapaSiguiente() {
        assertThat(EtapaDelInforme.pendienteTrasElPerfil(List.of("PERFIL_INTEGRAL")))
                .isEqualTo("Por verificar en la entrevista")
                .isEqualTo(EtapaDelInforme.PRUEBA.pendiente());
        assertThat(EtapaDelInforme.pendienteTrasElPerfil(List.of())).isEqualTo("Por verificar en la entrevista");
        assertThat(EtapaDelInforme.pendienteTrasElPerfil(null)).isEqualTo("Por verificar en la entrevista");
    }

    @Test
    @DisplayName("una etapa nueva sin nombre en el informe no se inventa: «la etapa siguiente»")
    void etapaNueva() {
        assertThat(EtapaDelInforme.pendienteTrasElPerfil(List.of("PERFIL_INTEGRAL", "ENTREVISTA_GRUPAL")))
                .isEqualTo("Por verificar en la etapa siguiente");
    }
}
