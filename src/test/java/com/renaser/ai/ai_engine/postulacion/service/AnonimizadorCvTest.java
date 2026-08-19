package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.postulacion.service.impl.AnonimizadorCv;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que la IA no puede ver del currículum (RF-41).
 *
 * <p>Es la única regla del hito 2 que no se puede comprobar mirando la base: o el texto sale
 * limpio, o el dato viaja al modelo y nadie se entera.
 */
class AnonimizadorCvTest {

    private final AnonimizadorCv anonimizador = new AnonimizadorCv();

    @Test
    void quitaEdadSexoYEstadoCivilEnSusFormasNormales() {
        String recortado = anonimizador.anonimizar("""
                Camila Rojas
                Edad: 34 años
                Sexo: Femenino
                Estado civil: Casada
                Fecha de nacimiento: 12/03/1992
                Hijos: 2

                EXPERIENCIA
                Automaticé el cierre mensual y pasó de 3 días a 4 horas.
                """);

        assertThat(recortado)
                .doesNotContain("34")
                .doesNotContain("Femenino")
                .doesNotContain("Casada")
                .doesNotContain("1992")
                .contains(AnonimizadorCv.TAPADO);

        // Y lo que sí puntúa se queda entero: si se llevara por delante la experiencia, la
        // nota saldría baja por un motivo que no tiene nada que ver con el candidato.
        assertThat(recortado).contains("Automaticé el cierre mensual");
        assertThat(recortado).contains("Camila Rojas");
    }

    @Test
    void tambienEnLasFormasSueltas() {
        String recortado = anonimizador.anonimizar(
                "Ingeniera de 29 años, soltera, con 6 años en logística.");

        assertThat(recortado).doesNotContain("29 años").doesNotContain("soltera");
        // «6 años en logística» habla de experiencia, no de edad... pero está escrito igual
        // que una edad. Se tapa, y es la decisión correcta: es preferible perder un dato de
        // antigüedad —que por regla no da puntos sola (RF-44)— que dejar pasar una edad.
        assertThat(recortado).contains("logística");
    }

    @Test
    void noSeRompeConTextoVacio() {
        assertThat(anonimizador.anonimizar(null)).isNull();
        assertThat(anonimizador.anonimizar("   ")).isEqualTo("   ");
    }
}
