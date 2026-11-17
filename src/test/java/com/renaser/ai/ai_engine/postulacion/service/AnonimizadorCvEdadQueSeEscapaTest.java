package com.renaser.ai.ai_engine.postulacion.service;

import com.renaser.ai.ai_engine.postulacion.service.impl.AnonimizadorCv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresiones de la QA de la spec la-ia-sabe-que-dia-es-hoy: edades escritas de formas que un CV
 * real usa y que la regla nueva deja pasar a la IA.
 *
 * <p>La spec pide tapar «34 años» solo en su línea y, ante la duda, tapar: mandar la edad a un
 * tercero es peor que perder un dato. La lista de lo que dice «esto es experiencia» se mira justo
 * detrás de «años», pero ese hueco admite saltos de línea, así que la primera palabra de la línea
 * siguiente («En», «Como», «Dedicada», un gerundio) salva a la edad de la línea de arriba.
 *
 * <p>Segunda vuelta (QA-FECHA-03 a 05): en la misma línea, la lista toma por experiencia
 * cualquier «en», «como», «dedicado» o palabra terminada en «-ando»/«-iendo», aunque lo que
 * siga no sea lo que hizo la persona: «Joven de 22 años en búsqueda de…», «Tengo 34 años en
 * Lima», «34 años Fernando». Además el comando tomaría esos CV por «perdió años de
 * experiencia» y los recalificaría mandando la edad.
 *
 * <p>Tercera vuelta (QA-FECHA-06 a 10), con la regla decidida en D-1 y D-2: una señal de edad
 * delante tapa siempre (salvo «de experiencia/trayectoria/servicio» detrás) y, sin señal, solo
 * destapa una lista cerrada. Lo que se escapa aún: «Tengo/Edad: más de N años», un nombre con
 * «de la», personas fuera de la lista fija, «en» + una palabra en minúscula que no es un área y
 * entradas que no están en la lista cerrada.
 */
@DisplayName("QA · la edad no se escapa por la línea siguiente, por «de N años en busca» ni por lo que la lista toma por experiencia")
class AnonimizadorCvEdadQueSeEscapaTest {

    private final AnonimizadorCv anonimizador = new AnonimizadorCv();

    @ParameterizedTest(name = "«{0}» se tapa")
    @ValueSource(strings = {
            "María López\n34 años\nDedicada a la gestión de personas",
            "Juan Pérez\n34 años\nEn búsqueda de nuevos retos laborales",
            "Rosa Huayta\n34 AÑOS\nCOMO PROFESIONAL DE RR. HH. BUSCO CRECER",
            "Carlos Ruiz\n34 años\nBuscando oportunidades en logística",
            "DATOS PERSONALES\r\n34 años\r\nEn Lima, Perú"})
    @DisplayName("QA-FECHA-01 · «34 años» solo en su línea se tapa aunque la línea siguiente empiece por «En», «Como», «Dedicada» o un gerundio")
    void laEdadSolaEnSuLineaSeTapa(String texto) {
        String tapado = anonimizador.anonimizar(texto);
        assertThat(tapado).contains(AnonimizadorCv.TAPADO).doesNotContainPattern("(?<!\\d)34(?!\\d)");
    }

    @ParameterizedTest(name = "«{0}» se tapa")
    @ValueSource(strings = {
            "Joven de 22 años en busca de mi primer empleo",
            "Mujer de 34 años en busca de nuevos retos"})
    @DisplayName("QA-FECHA-02 · «de N años en busca de…» habla de la edad y se tapa")
    void deNAniosEnBuscaSeTapa(String texto) {
        String tapado = anonimizador.anonimizar(texto);
        assertThat(tapado).contains(AnonimizadorCv.TAPADO).doesNotContainPattern("(?<!\\d)(22|34)(?!\\d)");
    }

    @ParameterizedTest(name = "«{0}» se tapa y el comando no lo toma por experiencia perdida")
    @ValueSource(strings = {
            "Joven de 22 años en búsqueda de mi primer empleo",
            "Mujer de 34 años en búsqueda de nuevos retos",
            "Joven de 22 años en la búsqueda de mi primer empleo",
            "Joven de 22 años estudiando Administración en la UPC",
            "Joven de 22 años buscando mi primera oportunidad laboral",
            "Profesional de 30 años dedicado a la docencia"})
    @DisplayName("QA-FECHA-03 · «Joven/Mujer/Profesional de N años» es una edad aunque detrás vaya «en búsqueda», un gerundio o «dedicado»")
    void deNAniosConLoQueLaListaTomaPorExperienciaSeTapa(String texto) {
        assertThat(anonimizador.anonimizar(texto))
                .contains(AnonimizadorCv.TAPADO).doesNotContainPattern("(?<!\\d)(22|30|34)(?!\\d)");
        assertThat(anonimizador.laReglaViejaTapabaDeMas(texto)).isFalse();
    }

    @ParameterizedTest(name = "«{0}» se tapa y el comando no lo toma por experiencia perdida")
    @ValueSource(strings = {
            "Tengo 34 años en Lima",
            "Soltero, 34 años en la actualidad",
            "Tengo 34 años en la actualidad",
            "34 años como edad"})
    @DisplayName("QA-FECHA-04 · «en»/«como» seguidos de un lugar, de «la actualidad» o de «edad» no son experiencia")
    void enOComoSinUnTrabajoDetrasSeTapa(String texto) {
        assertThat(anonimizador.anonimizar(texto))
                .contains(AnonimizadorCv.TAPADO).doesNotContainPattern("(?<!\\d)34(?!\\d)");
        assertThat(anonimizador.laReglaViejaTapabaDeMas(texto)).isFalse();
    }

    @ParameterizedTest(name = "«{0}» se tapa y el comando no lo toma por experiencia perdida")
    @ValueSource(strings = {
            "Díaz 34 años Fernando",
            "34 años Orlando Quispe",
            "34 AÑOS ARMANDO ROJAS"})
    @DisplayName("QA-FECHA-05 · un nombre terminado en «-ando» (Fernando, Orlando, Armando) no es un gerundio")
    void unNombreEnAndoNoEsUnGerundio(String texto) {
        assertThat(anonimizador.anonimizar(texto))
                .contains(AnonimizadorCv.TAPADO).doesNotContainPattern("(?<!\\d)34(?!\\d)");
        assertThat(anonimizador.laReglaViejaTapabaDeMas(texto)).isFalse();
    }

    /**
     * Lo que el arreglo de QA-FECHA-03 a 05 no debe llevarse por delante: es lo que hizo la
     * persona. «Búsqueda y selección» es el oficio de Recursos Humanos, no una búsqueda de empleo.
     * («dedicada» salió de esta lista con D-1: no está en la lista cerrada; ver QA-FECHA-10).
     */
    @ParameterizedTest(name = "«{0}» no se tapa")
    @ValueSource(strings = {
            "5 años en búsqueda y selección de personal",
            "5 años liderando equipos comerciales",
            "más de 10 años en ventas",
            "4 años trabajando en logística"})
    @DisplayName("QA-FECHA-03 a 05 · la experiencia con «en búsqueda y selección» o un gerundio laboral sigue sin taparse")
    void laExperienciaQueSeParecePeroNoEsEdadNoSeTapa(String texto) {
        assertThat(anonimizador.anonimizar(texto)).isEqualTo(texto);
    }

    // ===================================================== tercera vuelta, con D-1 y D-2

    /**
     * D-1: «Tengo» y «Edad» son señales de edad y tapan venga lo que venga; «más de» solo destapa
     * sin señal. Con «más de» entre la señal y la cifra la edad llega entera, también la rotulada.
     */
    @ParameterizedTest(name = "«{0}» se tapa y el comando no lo toma por experiencia perdida")
    @ValueSource(strings = {
            "Tengo más de 30 años",
            "Tengo mas de 30 años",
            "Tengo más de 30 años en Lima",
            "Tengo más de 30 años y vivo en Lima",
            "Edad: más de 30 años",
            "EDAD: MÁS DE 30 AÑOS"})
    @DisplayName("QA-FECHA-06 · «Tengo más de N años» y «Edad: más de N años» son una edad: la señal gana a «más de»")
    void laSenalTengoOEdadGanaAMasDe(String texto) {
        assertThat(anonimizador.anonimizar(texto))
                .contains(AnonimizadorCv.TAPADO).doesNotContainPattern("(?<!\\d)30(?!\\d)");
        assertThat(anonimizador.laReglaViejaTapabaDeMas(texto)).isFalse();
    }

    /** D-1: «Nombre Apellido,» delante tapa; un apellido con «de la» o «del» sigue siendo un nombre. */
    @ParameterizedTest(name = "«{0}» se tapa y el comando no lo toma por experiencia perdida")
    @ValueSource(strings = {
            "Ana de la Cruz, 34 años como cajera",
            "Juan Pérez de la Torre, 34 años trabajando en Ripley",
            "Rosa del Águila, 34 años en ventas",
            "José Luis de la Puente, 34 años como supervisor"})
    @DisplayName("QA-FECHA-07 · «Nombre Apellido,» con «de la»/«del» en el apellido es una señal de edad")
    void unApellidoConParticulaEsUnNombre(String texto) {
        assertThat(anonimizador.anonimizar(texto))
                .contains(AnonimizadorCv.TAPADO).doesNotContainPattern("(?<!\\d)34(?!\\d)");
        assertThat(anonimizador.laReglaViejaTapabaDeMas(texto)).isFalse();
    }

    /** D-1: «Joven/Mujer/Hombre/Profesional/… de» es una clase abierta de personas. */
    @ParameterizedTest(name = "«{0}» se tapa y el comando no lo toma por experiencia perdida")
    @ValueSource(strings = {
            "Alumna de 20 años trabajando medio tiempo",
            "Alumno de 20 años como practicante",
            "Caballero de 20 años como vigilante",
            "Ecuatoriano de 20 años trabajando en Lima"})
    @DisplayName("QA-FECHA-08 · «Alumna/Alumno/Caballero/Ecuatoriano de N años» es una edad como «Joven de N años»")
    void otraPersonaDeNAniosEsUnaEdad(String texto) {
        assertThat(anonimizador.anonimizar(texto))
                .contains(AnonimizadorCv.TAPADO).doesNotContainPattern("(?<!\\d)20(?!\\d)");
        assertThat(anonimizador.laReglaViejaTapabaDeMas(texto)).isFalse();
    }

    /**
     * D-1: «en &lt;área de trabajo&gt;» destapa, pero no «en &lt;ciudad o lugar&gt;», y todo lo demás
     * se tapa. Un lugar o una palabra que no es un área, escritos en minúscula, no son experiencia.
     */
    @ParameterizedTest(name = "«{0}» se tapa y el comando no lo toma por experiencia perdida")
    @ValueSource(strings = {
            "34 años en miraflores",
            "34 años en surco",
            "34 años en los olivos",
            "34 años en la molina",
            "Cumplo 34 años en marzo",
            "34 años en este mundo",
            "34 años en familia"})
    @DisplayName("QA-FECHA-09 · «en» + un lugar o algo que no es un área de trabajo, en minúscula, no destapa")
    void enMinusculaSinUnAreaDetrasSeTapa(String texto) {
        assertThat(anonimizador.anonimizar(texto))
                .contains(AnonimizadorCv.TAPADO).doesNotContainPattern("(?<!\\d)34(?!\\d)");
        assertThat(anonimizador.laReglaViejaTapabaDeMas(texto)).isFalse();
    }

    /**
     * D-1: sin señal, solo destapa la lista cerrada («de experiencia/trayectoria/servicio/labor»,
     * «en el cargo/…», «en &lt;área&gt;», «como &lt;cargo&gt;», «durante»/«más de» delante y los
     * gerundios laborales). «dedicada», «a cargo» y «al servicio» no están: se tapan.
     */
    @ParameterizedTest(name = "«{0}» se tapa y el comando no lo toma por experiencia perdida")
    @ValueSource(strings = {
            "34 años dedicada a mi familia",
            "34 años a cargo de mis hijos",
            "34 años al servicio de Dios"})
    @DisplayName("QA-FECHA-10 · lo que no está en la lista cerrada de D-1 («dedicada», «a cargo», «al servicio») se tapa")
    void fueraDeLaListaCerradaSeTapa(String texto) {
        assertThat(anonimizador.anonimizar(texto))
                .contains(AnonimizadorCv.TAPADO).doesNotContainPattern("(?<!\\d)34(?!\\d)");
        assertThat(anonimizador.laReglaViejaTapabaDeMas(texto)).isFalse();
    }
}
