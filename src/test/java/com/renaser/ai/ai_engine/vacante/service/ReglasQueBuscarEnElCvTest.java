package com.renaser.ai.ai_engine.vacante.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Qué pasa y qué no en «Qué buscar en el CV» (V80). El panel tiene las mismas pruebas en
 * {@code reglasQueBuscar.test.ts}: si una cambia, cambian las dos.
 */
@DisplayName("«Qué buscar en el CV»: lo que se puede escribir")
class ReglasQueBuscarEnElCvTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Menor de 35 años", "Que sea casado", "Mayor de edad", "Soltera, sin hijos",
            "Sexo masculino", "Mujer joven", "Que goce de buena salud", "Sin discapacidad",
            "Católico practicante", "De raza blanca", "Nacionalidad peruana",
            "Lugar de origen: Lima", "Que no esté embarazada", "Estado civil: casado",
            "Entre 25 y 35 años", "Tez clara", "Género femenino",
            // La edad con números, aunque no diga «edad» (QA-01)
            "Personas de 25 a 35 años", "Máximo 30 años", "Menos de 30 años", "Mayor a 40 años",
            "Hasta 35 años", "18 años a más", "De 18 a 30 años de preferencia", "Que tenga 25 años",
            "Entre 25-35 años", "Treinta y cinco años como máximo", "Entre veinte y treinta años",
            "Menor de 35 años, con experiencia en caja", "Menor de 30 años con experiencia en caja",
            "Experiencia en caja y menor de 35 años", "No mayor de 40", "Hasta los 35",
            "Mayores de 18 y con secundaria completa", "Trabajo bajo presión hasta 35 años",
            // El sexo nombrando a la persona (QA-01)
            "Chica para atención al cliente", "Chico para reparto", "Señora para limpieza",
            "Muchacha para atención en tienda", "Señorita para recepción", "Varón para almacén"})
    @DisplayName("Lo que no sirve para elegir a nadie se rechaza")
    void loProhibidoSeRechaza(String linea) {
        assertThat(ReglasQueBuscarEnElCv.esProhibida(linea)).as(linea).isTrue();
        assertThatThrownBy(() -> ReglasQueBuscarEnElCv.lasQueSeGuardan(List.of(linea)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ReglasQueBuscarEnElCv.MOTIVO_PROHIBIDA);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Carné de salud vigente", "Carné sanitario", "Certificado de salud",
            "Experiencia en cocina saludable", "Controla costos y mermas",
            "Ha dirigido un equipo de cocina", "Experiencia mínima de 3 años en cocina",
            "Más de 5 años de experiencia", "Certificado de manipulación de alimentos (BPM)",
            "Experiencia en el sector salud", "Conocimientos de seguridad y salud en el trabajo",
            "Manejo de productos de origen animal", "Inglés a nivel avanzado",
            // Números y años que no son una edad
            "3 años de experiencia en caja", "Más de 3 años de experiencia en cocina",
            "Inglés intermedio", "Mínimo 2 años en puestos similares", "Más de 15 años en el rubro",
            "Experiencia de 15 a 20 años en el rubro", "Experiencia laboral mínima de 15 años",
            "2 años como cajero", "20 años trabajando en restaurantes",
            "Ticket promedio mayor a 50 soles", "Atiende más de 20 mesas por turno",
            "Atender los 30 pedidos del turno",
            // «Chica» que habla de un tamaño
            "Manejo de caja chica", "Arqueo de caja chica y fondo fijo"})
    @DisplayName("Lo demás, incluidos los documentos que pide la ley, se acepta")
    void loDemasSeAcepta(String linea) {
        assertThat(ReglasQueBuscarEnElCv.esProhibida(linea)).as(linea).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // QA-04: la edad junto a la experiencia, sin coma, y el sexo con «señor»
            "2 años de experiencia y menor de 35 años", "Experiencia y menor de 35 años",
            "1 año de experiencia y menor de 30", "Con experiencia y mayor de 25 años",
            "Con 1 año de experiencia y hasta 30 años", "Señor para vigilancia",
            "Señores para seguridad",
            // QA-06: las demás categorías del Flujo 1.3 dichas como se suelen decir
            "Natural de Arequipa", "Oriundo de Lima", "No adventistas", "Que no sea adventista",
            "Colombianos abstenerse", "Bolivianos no", "Madre de familia", "Padre de familia",
            "Que no esté gestando", "Que no tenga VIH", "Piel blanca", "Afrodescendiente",
            "Minusválido"})
    @DisplayName("QA-04 / QA-06: más formas de pedir lo prohibido también se rechazan")
    void masFormasDeLoProhibidoSeRechazan(String linea) {
        assertThat(ReglasQueBuscarEnElCv.esProhibida(linea)).as(linea).isTrue();
        assertThatThrownBy(() -> ReglasQueBuscarEnElCv.lasQueSeGuardan(List.of(linea)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ReglasQueBuscarEnElCv.MOTIVO_PROHIBIDA);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Experiencia general no menor de 15 años", "Experiencia en el cargo de 15 años",
            "Experiencia comprobada en el rubro de al menos 15 años",
            "Experiencia en cocina, al menos 15 años",
            "Trabajó en cadenas de comida rápida durante 15 años",
            "Experiencia en el sector durante más de 20 años"})
    @DisplayName("QA-05: los años de experiencia de 15 o más no son una edad")
    void losAnosDeExperienciaNoSonUnaEdad(String linea) {
        assertThat(ReglasQueBuscarEnElCv.esProhibida(linea)).as(linea).isFalse();
        assertThat(ReglasQueBuscarEnElCv.lasQueSeGuardan(List.of(linea))).containsExactly(linea);
    }

    @Test
    @DisplayName("Las vacías no cuentan, se recortan los extremos y las repetidas cuentan una vez")
    void vaciasYRepetidas() {
        assertThat(ReglasQueBuscarEnElCv.lasQueSeGuardan(Arrays.asList(
                "  Controla costos y mermas  ", "", "   ", null, "controla   COSTOS y mermas",
                "Ha dirigido un equipo de cocina")))
                .containsExactly("Controla costos y mermas", "Ha dirigido un equipo de cocina");
    }

    @Test
    @DisplayName("Con 8 líneas se guardan las 6 primeras válidas")
    void seisComoMucho() {
        List<String> ocho = List.of("Uno", "Dos", "", "Tres", "Cuatro", "Cinco", "Seis", "Siete", "Ocho");
        assertThat(ReglasQueBuscarEnElCv.lasQueSeGuardan(ocho))
                .containsExactly("Uno", "Dos", "Tres", "Cuatro", "Cinco", "Seis");
    }

    @Test
    @DisplayName("Más de 200 caracteres es un 400")
    void masDeDoscientos() {
        assertThat(ReglasQueBuscarEnElCv.lasQueSeGuardan(List.of("a".repeat(200)))).hasSize(1);
        assertThatThrownBy(() -> ReglasQueBuscarEnElCv.lasQueSeGuardan(List.of("a".repeat(201))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("200");
    }

    @Test
    @DisplayName("Una prohibida más allá de la sexta también rechaza el guardado entero")
    void unaProhibidaAunqueSobre() {
        List<String> lineas = List.of("Uno", "Dos", "Tres", "Cuatro", "Cinco", "Seis", "Menor de 35 años");
        assertThatThrownBy(() -> ReglasQueBuscarEnElCv.lasQueSeGuardan(lineas))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Sin lista, ninguna línea")
    void sinLista() {
        assertThat(ReglasQueBuscarEnElCv.lasQueSeGuardan(null)).isEmpty();
    }
}
