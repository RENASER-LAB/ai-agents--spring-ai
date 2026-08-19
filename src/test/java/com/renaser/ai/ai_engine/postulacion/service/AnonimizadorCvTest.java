package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.postulacion.service.impl.AnonimizadorCv;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

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

    // La edad escrita en mayúsculas con Ñ. Es la forma normal de un encabezado de currículum
    // en español, no un caso raro: «AÑOS DE EXPERIENCIA» aparece en casi todos.
    @Test
    void tapaLaEdadAunqueVengaEnMayusculasConEne() {
        String recortado = anonimizador.anonimizar("Tengo 34 AÑOS");

        assertThat(recortado).doesNotContain("34").doesNotContain("AÑOS");
        assertThat(recortado).contains(AnonimizadorCv.TAPADO);
    }

    /**
     * El caso que decide cuál de las dos banderas Unicode se usa.
     *
     * <p>Un PDF mal extraído pega la cifra a lo que va delante: «Nº34 años». Con
     * {@code UNICODE_CHARACTER_CLASS} la {@code º} pasa a contar como letra, desaparece el
     * límite de palabra antes del número y esto <b>deja de taparse</b>. Con
     * {@code UNICODE_CASE} se tapa igual y las mayúsculas acentuadas también.
     *
     * <p>Está escrito aquí porque es la clase de detalle que alguien deshace de buena fe
     * para callar un aviso de análisis estático, sin ver que a cambio manda una edad fuera.
     */
    @Test
    void tapaLaEdadAunqueLaCifraVengaPegadaAUnSimbolo() {
        assertThat(anonimizador.anonimizar("Nº34 años"))
                .doesNotContain("34")
                .contains(AnonimizadorCv.TAPADO);
    }

    // «GÉNERO» con É mayúscula. Sin la bandera Unicode el rótulo entero sobrevive y viaja.
    @Test
    void tapaElGeneroAunqueVengaEnMayusculasConTilde() {
        assertThat(anonimizador.anonimizar("GÉNERO: Femenino"))
                .doesNotContain("GÉNERO")
                .doesNotContain("Femenino")
                .contains(AnonimizadorCv.TAPADO);

        // Sin valor de diccionario detrás, el rótulo era lo único que podía enganchar:
        // si él falla, la letra sale tal cual hacia el modelo.
        assertThat(anonimizador.anonimizar("GÉNERO: F")).doesNotContain("F");
    }

    // «UNIÓN LIBRE» con Ó mayúscula, y al lado el mismo dato en ASCII puro («VIUDA»), que ya
    // se tapaba antes del arreglo. Los dos tienen que salir igual: la única diferencia entre
    // ellos era el acento.
    @Test
    void tapaElEstadoCivilAunqueVengaEnMayusculasConTilde() {
        assertThat(anonimizador.anonimizar("Estado: UNIÓN LIBRE"))
                .doesNotContain("UNIÓN")
                .doesNotContain("LIBRE")
                .contains(AnonimizadorCv.TAPADO);

        assertThat(anonimizador.anonimizar("Estado civil: VIUDA")).doesNotContain("VIUDA");
    }

    // Un currículum es un PDF que sube cualquiera con cuenta de candidato, así que este texto
    // es alcanzable desde fuera: «Edad» seguida de un montón de espacios y ninguna cifra
    // detrás. Con los cuantificadores normales el motor prueba todas las formas de repartir
    // esos espacios entre los dos huecos y tarda minutos; con los posesivos, milisegundos.
    @Test
    void noSeCuelgaConUnTextoHechoAMala() {
        String cebo = "Edad" + " ".repeat(100_000) + "x";

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> anonimizador.anonimizar(cebo));
    }
}
