package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.postulacion.service.impl.AnonimizadorCv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Lo que la IA no puede ver del currículum (RF-41).
 *
 * <p>Es la única regla del hito 2 que no se puede comprobar mirando la base: o el texto sale
 * limpio, o el dato viaja al modelo y nadie se entera.
 */
@DisplayName("El anonimizador del currículum")
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
        // «6 años en logística» habla de experiencia, no de edad: desde noviembre de 2026 llega
        // entera. Antes se tapaba y la IA no veía los años de experiencia, que es justo lo que
        // más pide la clienta.
        assertThat(recortado).contains("6 años en logística");
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

    // ============================================================ la edad y la experiencia

    /**
     * La batería de la spec la-ia-sabe-que-dia-es-hoy (AC-05 a AC-07): la edad se tapa y los años
     * de experiencia no. Ante la duda se tapa; la duda se resuelve aquí, no ampliando la regla a
     * ojo.
     */
    @Nested
    @DisplayName("La edad se tapa; los años de experiencia no")
    class EdadYExperiencia {

        @Test
        @DisplayName("AC-05 · «Tengo más de 5 años de experiencia en Recursos Humanos» llega entera")
        void laExperienciaLlegaEntera() {
            String frase = "Tengo más de 5 años de experiencia en Recursos Humanos";
            assertThat(anonimizador.anonimizar(frase)).isEqualTo(frase);
        }

        @ParameterizedTest(name = "«{0}» se tapa")
        @ValueSource(strings = {"Edad: 34", "34 años de edad", "Tengo 34 años", "Juan Pérez, 34 años",
                "34 AÑOS", "Nº34 años", "DNI 12345678 · 34 años · Lima", "(34 años)", "34 años y dos hijos",
                "34 años, en Lima", "Juan Pérez\n34 años\nLima", "Edad 34 años", "34 años cuando empecé"})
        @DisplayName("AC-06 · la edad, en sus formas normales, se tapa")
        void laEdadSeTapa(String texto) {
            String tapado = anonimizador.anonimizar(texto);
            // La edad suelta, no un 34 dentro de otra cifra («DNI 12345678»).
            assertThat(tapado).contains(AnonimizadorCv.TAPADO).doesNotContainPattern("(?<!\\d)34(?!\\d)");
        }

        /**
         * «3 años a cargo del almacén» y «7 años de antigüedad en la empresa» salieron de esta lista
         * con D-3: «a cargo» y «de antigüedad» ya no están en la lista blanca y se tapan (ver
         * {@code ReglaD3#loQueD3QuitoDeLaListaSeTapa}).
         */
        @ParameterizedTest(name = "«{0}» no se tapa")
        @ValueSource(strings = {"10 años en ventas", "más de 10 años en ventas", "3 años en el cargo",
                "2 años como supervisor", "durante 6 años", "Durante  6 años", "6 AÑOS DE EXPERIENCIA",
                "4 años trabajando en logística", "6 años de trayectoria", "12 años de servicio",
                "años de servicio", "5 años liderando equipos", "8 AÑOS EN EL SECTOR"})
        @DisplayName("AC-07 · una cifra con «años» seguida de lo que hizo la persona no se tapa")
        void laExperienciaNoSeTapa(String texto) {
            assertThat(anonimizador.anonimizar(texto)).isEqualTo(texto);
        }

        /**
         * QA-FECHA-01: lo que decide si «N años» es experiencia va en la misma línea que «años».
         * La primera palabra de la línea siguiente no salva a la edad, ni un «durante» que cierra
         * la línea de arriba.
         */
        @ParameterizedTest(name = "«{0}» se tapa")
        @ValueSource(strings = {"Peruano, 34 años\nTrabajando actualmente en Ripley",
                "34 años\n\nEn el 2020 ingresé a Ripley", "34 años\nde trabajo en equipo",
                "34 años \nComo profesional busco crecer", "Disponibilidad durante\n34 años\nLima",
                "MUJER DE 34 AÑOS EN BUSCA DE EMPLEO"})
        @DisplayName("QA-FECHA-01/02 · la línea de al lado y «en busca» no salvan a la edad")
        void laLineaDeAlLadoNoSalvaALaEdad(String texto) {
            assertThat(anonimizador.anonimizar(texto))
                    .contains(AnonimizadorCv.TAPADO).doesNotContainPattern("(?<!\\d)34(?!\\d)");
        }

        /**
         * Con D-3, «3 años a⏎cargo del almacén» («a cargo» salió de la lista blanca) y «10 años en
         * buscadores web» («buscadores» no es un área de la lista fija) se tapan; los sustituyen
         * dos de la lista partidos igual por el PDF.
         */
        @ParameterizedTest(name = "«{0}» no se tapa")
        @ValueSource(strings = {"más de 5 años de\nexperiencia en ventas", "3 años en el\ncargo",
                "10 años en ventas", "2 años como\nsupervisor"})
        @DisplayName("QA-FECHA-01 · la palabra que decide, en la línea de «años», sigue valiendo aunque el PDF la parta")
        void laExperienciaPartidaPorElPdfNoSeTapa(String texto) {
            assertThat(anonimizador.anonimizar(texto)).isEqualTo(texto);
        }

        @Test
        @DisplayName("«34 años, 10 años de experiencia» tapa el primero y deja el segundo")
        void lasDosEnLaMismaLinea() {
            assertThat(anonimizador.anonimizar("34 años, 10 años de experiencia"))
                    .isEqualTo(AnonimizadorCv.TAPADO + ", 10 años de experiencia");
        }

        @Test
        @DisplayName("un CV sin fechas ni años no cambia nada")
        void sinAniosNoCambiaNada() {
            String texto = "Analista de Recursos Humanos en Ripley. Excel avanzado.";
            assertThat(anonimizador.anonimizar(texto)).isEqualTo(texto);
            assertThat(anonimizador.laReglaViejaTapabaDeMas(texto)).isFalse();
        }

        @Test
        @DisplayName("la regla vieja tapaba los años de experiencia: así se sabe a quién recalificar")
        void laReglaViejaTapabaDeMas() {
            assertThat(anonimizador.laReglaViejaTapabaDeMas(
                    "Tengo más de 5 años de experiencia en Recursos Humanos")).isTrue();
            // La edad la tapaban las dos: no es una pérdida.
            assertThat(anonimizador.laReglaViejaTapabaDeMas("Juan Pérez, 34 años")).isFalse();
            assertThat(anonimizador.laReglaViejaTapabaDeMas(null)).isFalse();
            // QA-FECHA-01/02: la edad que se escapaba por la línea siguiente o por «en busca» la
            // tapan ahora las dos reglas. El comando no debe tomar esos CV por «perdió años de
            // experiencia» y recalificarlos.
            assertThat(anonimizador.laReglaViejaTapabaDeMas(
                    "María López\n34 años\nDedicada a la gestión de personas")).isFalse();
            assertThat(anonimizador.laReglaViejaTapabaDeMas(
                    "Juan Pérez\n34 años\nEn búsqueda de nuevos retos laborales")).isFalse();
            assertThat(anonimizador.laReglaViejaTapabaDeMas(
                    "Joven de 22 años en busca de mi primer empleo")).isFalse();
        }

        @Test
        @DisplayName("la marca se reconoce en un texto devuelto por la IA")
        void reconoceLaMarca() {
            assertThat(AnonimizadorCv.traeLaMarca("Tiene más de [DATO NO UTILIZABLE] de experiencia")).isTrue();
            assertThat(AnonimizadorCv.traeLaMarca("Tiene más de 5 años de experiencia")).isFalse();
            assertThat(AnonimizadorCv.traeLaMarca(null)).isFalse();
        }

        @Test
        @DisplayName("tampoco se cuelga con un texto hecho a mala para la regla nueva")
        void noSeCuelgaConLaReglaNueva() {
            String cebo = "1 años" + " ".repeat(50_000) + "x" + " durante".repeat(5_000) + " 9 años";
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> anonimizador.anonimizar(cebo));
        }
    }

    // ============================================================ la regla de D-3

    /**
     * La regla de la edad decidida el 16/11/2026 (D-3 de la spec la-ia-sabe-que-dia-es-hoy, que
     * sustituye la lista de D-1, con la precisión de D-2): toda «N años» se tapa salvo una lista
     * blanca mínima y literal; una señal de edad delante tapa siempre, salvo «de
     * experiencia/trayectoria/servicio» justo detrás. Cada caso que se tapa tiene que taparse
     * también con la regla vieja: si no, el comando lo tomaría por «perdió años de experiencia» y
     * mandaría la edad al recalificar.
     */
    @Nested
    @DisplayName("D-3 · toda «N años» se tapa salvo la lista blanca; la señal de edad delante tapa siempre")
    class ReglaD3 {

        @ParameterizedTest(name = "«{0}» se tapa")
        @ValueSource(strings = {"Tengo 34 años trabajando en Ripley", "Juan Pérez, 34 años liderando equipos",
                "JUAN PÉREZ, 34 AÑOS TRABAJANDO EN RIPLEY", "María de los Ángeles Quispe, 34 años como cajera",
                "Joven de\n34 años trabajando en Ripley", "Licenciado, 34 años dedicado al rubro",
                "Peruana, 34 años trabajando en ventas", "Persona de 34 años como supervisor",
                "HOMBRE DE 34 AÑOS EN EL SECTOR", "Tengo 34 años de trabajo en ventas",
                "Juan Pérez, 34 años de labor", "Tengo 34 años de vida", "Cumplo 34 años como cajera",
                "CUMPLO 34 AÑOS EN VENTAS", "Tengo más de 34 años trabajando", "Edad: más de 34 años en ventas",
                "Licenciada en Administración de 34 años trabajando en ventas", "Ama de casa de 34 años",
                "Egresada de Psicología de 34 años como asistente", "Hombre, de 34 años como vigilante",
                "Luis del Pino, 34 años en logística", "ana de la Cruz, 34 años como cajera",
                "mujer, 34 años trabajando", "Ecuatoriano, 34 años trabajando en Lima"})
        @DisplayName("una señal de edad delante («Tengo», «Cumplo», «Edad», cualquier palabra + «de», un nombre y una coma) tapa venga lo que venga detrás")
        void laSenalDelanteTapaSiempre(String texto) {
            assertThat(anonimizador.anonimizar(texto))
                    .contains(AnonimizadorCv.TAPADO).doesNotContainPattern("(?<!\\d)34(?!\\d)");
            assertThat(anonimizador.laReglaViejaTapabaDeMas(texto)).isFalse();
        }

        @ParameterizedTest(name = "«{0}» se tapa")
        @ValueSource(strings = {"34 años en Lima", "34 AÑOS EN LIMA", "34 años en lima", "34 años en San Isidro",
                "34 años en el Perú", "34 años en la actualidad", "34 AÑOS EN LA ACTUALIDAD",
                "34 años en busca de empleo", "34 años en búsqueda de empleo", "34 años en la búsqueda de empleo",
                "34 años en búsqueda", "34 años en mi casa", "34 años en el extranjero", "34 años en",
                "34 años como edad", "34 años como mi edad", "34 años como", "34 años estudiando inglés",
                "34 años buscando empleo", "34 años Fernando", "34 años cumplidos", "34 años en total",
                "34 años en marzo", "34 años en san borja", "34 años en mi tierra", "34 años como madre",
                "34 años como persona", "34 años dedicándome a mi familia", "34 años sirviendo a Dios",
                "34 años al frente de mi hogar", "34 años al mando de mi casa", "34 años de trabajo",
                "34 años de antigüedad", "34 años dedicado", "34 años a cargo", "34 años administrando mi hogar"})
        @DisplayName("sin señal, lo que no está en la lista blanca se tapa: un lugar, un mes, «la actualidad», «búsqueda de», «como madre», un gerundio que no es un trabajo")
        void fueraDeLaListaSeTapa(String texto) {
            assertThat(anonimizador.anonimizar(texto))
                    .contains(AnonimizadorCv.TAPADO).doesNotContainPattern("(?<!\\d)34(?!\\d)");
            assertThat(anonimizador.laReglaViejaTapabaDeMas(texto)).isFalse();
        }

        @ParameterizedTest(name = "«{0}» no se tapa")
        @ValueSource(strings = {"10 años en Recursos Humanos", "10 años en RR.HH.", "10 AÑOS EN VENTAS",
                "MÁS DE 10 AÑOS EN VENTAS", "5 años desempeñándome como analista",
                "5 AÑOS DESEMPEÑÁNDOME COMO ANALISTA", "6 años ejerciendo la docencia",
                "8 años gestionando proyectos", "5 años en la búsqueda y selección de personal",
                "3 años en ventas, 2 años como supervisor", "Experiencia: 10 años en atención al cliente",
                "10 años en el área comercial", "4 años laborando en el rubro", "3 años de labor en ventas",
                "6 años en la docencia", "8 años en logística", "5 años en RR. HH.", "4 AÑOS EN ATENCIÓN AL CLIENTE",
                "2 años como jefa de tienda", "Más de 10 años en ventas y 3 años como supervisor",
                "6 años en el puesto", "9 años en TI"})
        @DisplayName("sin señal, la lista blanca deja ver: «en <área>», «en el cargo/…», «como <cargo>», un gerundio laboral, «más de» delante")
        void laListaCerradaDestapa(String texto) {
            assertThat(anonimizador.anonimizar(texto)).isEqualTo(texto);
            assertThat(anonimizador.laReglaViejaTapabaDeMas(texto)).isTrue();
        }

        /**
         * D-2: la única excepción a la señal. Estos CV la regla vieja sí los tapaba, así que el
         * comando tiene que poder recalificarlos «por los años».
         */
        @ParameterizedTest(name = "«{0}» no se tapa")
        @ValueSource(strings = {"Tengo 10 años de experiencia en ventas", "Profesional de 10 años de experiencia",
                "Juan Pérez, 10 años de experiencia", "JOVEN DE 5 AÑOS DE TRAYECTORIA", "Tengo 12 años de servicio",
                "Mujer de más de 10 años de experiencia", "Licenciado, 8 años de\nexperiencia en ventas"})
        @DisplayName("D-2 · «de experiencia», «de trayectoria» o «de servicio» detrás ganan a la señal de edad delante")
        void laExperienciaGanaALaSenal(String texto) {
            assertThat(anonimizador.anonimizar(texto)).isEqualTo(texto);
            assertThat(anonimizador.laReglaViejaTapabaDeMas(texto)).isTrue();
        }

        @ParameterizedTest(name = "«{0}» se tapa")
        @ValueSource(strings = {"Mujer de más de 40 años", "JOVEN DE MÁS DE 20 AÑOS",
                "Profesional de más de 30 años en ventas", "Hombre de\nmás de 40 años trabajando"})
        @DisplayName("D-2 · «Mujer/Joven/Profesional… de» gana también a «más de»")
        void laSenalGanaAMasDe(String texto) {
            assertThat(anonimizador.anonimizar(texto))
                    .contains(AnonimizadorCv.TAPADO).doesNotContainPattern("(?<!\\d)(20|30|40)(?!\\d)");
            assertThat(anonimizador.laReglaViejaTapabaDeMas(texto)).isFalse();
        }

        /**
         * D-3 quita de la lista blanca «dedicado/a», «a cargo», «al frente/servicio/mando», «de
         * trabajo» y «de antigüedad»: también con años de experiencia de verdad se tapan. Es
         * aceptable perder una frase de experiencia poco habitual; mandar la edad, no.
         */
        @ParameterizedTest(name = "«{0}» se tapa")
        @ValueSource(strings = {"3 años a cargo del almacén", "7 años de antigüedad en la empresa",
                "5 años dedicada a la docencia", "10 años al frente del área", "8 años al servicio de la empresa",
                "4 años al mando de la tienda", "6 años de trabajo en ventas", "5 años en buscadores web"})
        @DisplayName("D-3 · lo que salió de la lista blanca se tapa aunque sea experiencia")
        void loQueD3QuitoDeLaListaSeTapa(String texto) {
            assertThat(anonimizador.anonimizar(texto)).contains(AnonimizadorCv.TAPADO).doesNotContainPattern("\\d");
            assertThat(anonimizador.laReglaViejaTapabaDeMas(texto)).isFalse();
        }

        @Test
        @DisplayName("una señal delante tapa la edad y no la experiencia que viene después en la misma frase")
        void laSenalSoloTapaSuCifra() {
            assertThat(anonimizador.anonimizar("Joven de 24 años con 2 años de experiencia"))
                    .isEqualTo("Joven de " + AnonimizadorCv.TAPADO + " con 2 años de experiencia");
        }

        @Test
        @DisplayName("no se cuelga con un texto hecho a mala para las señales de delante")
        void noSeCuelgaConLasSenales() {
            String nombres = "Ab ".repeat(50_000) + ", 34 años trabajando";
            String persona = "x".repeat(100_000) + " de 34 años trabajando";
            String detras = "34 años en" + " la".repeat(30_000);
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                anonimizador.anonimizar(nombres);
                anonimizador.anonimizar(persona);
                anonimizador.anonimizar(detras);
            });
        }
    }
}
