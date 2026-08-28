package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.BloquePedido;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosCuestionarioTecnico.PreguntaGenerada;
import com.renaser.ai.ai_engine.perfilintegral.service.RecetaCuestionarioTecnico.PreguntaPublicable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La receta del cuestionario técnico contra el documento de la clienta: DIR 12 · SUP 10 ·
 * OPE 8, y la aduana que no deja pasar un borrador a medias ni un tema prohibido.
 */
@DisplayName("La receta del cuestionario técnico")
class RecetaCuestionarioTecnicoTest {

    // Un borrador perfecto del nivel: una pregunta por hueco de la secuencia.
    private static List<PreguntaGenerada> borradorPerfecto(String nivel) {
        List<PreguntaGenerada> preguntas = new ArrayList<>();
        int n = 1;
        for (BloquePedido bloque : RecetaCuestionarioTecnico.estructura(nivel)) {
            for (int i = 0; i < bloque.cantidad(); i++) {
                boolean presencial = RecetaCuestionarioTecnico.PRESENCIAL.equals(bloque.bloque());
                preguntas.add(new PreguntaGenerada(
                        "T%02d".formatted(n++), bloque.bloque(), "Bloque " + bloque.bloque(),
                        "¿Cuál es la operación más grande que has administrado?",
                        presencial ? null : "montos y volúmenes",
                        presencial ? null : "el faltante que encontró",
                        presencial ? null : "respuesta genérica sin episodio",
                        presencial));
            }
        }
        return preguntas;
    }

    @Nested
    @DisplayName("La estructura")
    class Estructura {

        @Test
        @DisplayName("DIR lleva 12, SUP 10 y OPE 8 — los totales del documento")
        void losTotales() {
            assertThat(RecetaCuestionarioTecnico.totalPreguntas("DIRECCION")).isEqualTo(12);
            assertThat(RecetaCuestionarioTecnico.totalPreguntas("SUPERVISION")).isEqualTo(10);
            assertThat(RecetaCuestionarioTecnico.totalPreguntas("EJECUCION")).isEqualTo(8);
        }

        @Test
        @DisplayName("solo DIRECCION lleva muestra presencial, y solo una")
        void laPresencialEsDeDireccion() {
            assertThat(RecetaCuestionarioTecnico.estructura("DIRECCION"))
                    .filteredOn(b -> RecetaCuestionarioTecnico.PRESENCIAL.equals(b.bloque()))
                    .singleElement()
                    .satisfies(b -> assertThat(b.cantidad()).isEqualTo(1));
            assertThat(RecetaCuestionarioTecnico.estructura("SUPERVISION"))
                    .noneMatch(b -> RecetaCuestionarioTecnico.PRESENCIAL.equals(b.bloque()));
            assertThat(RecetaCuestionarioTecnico.estructura("EJECUCION"))
                    .noneMatch(b -> RecetaCuestionarioTecnico.PRESENCIAL.equals(b.bloque()));
        }

        @Test
        @DisplayName("OPE no lleva bloque de requerimiento, como en el documento")
        void opeSinRequerimiento() {
            assertThat(RecetaCuestionarioTecnico.estructura("EJECUCION"))
                    .noneMatch(b -> RecetaCuestionarioTecnico.REQUERIMIENTO.equals(b.bloque()));
        }

        @Test
        @DisplayName("un nivel desconocido revienta en la cara, no en producción")
        void nivelDesconocido() {
            assertThatThrownBy(() -> RecetaCuestionarioTecnico.estructura("GERENCIA"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("La aduana del borrador")
    class Aduana {

        @Test
        @DisplayName("un borrador perfecto pasa limpio, en los tres niveles")
        void elPerfectoPasa() {
            for (String nivel : List.of("DIRECCION", "SUPERVISION", "EJECUCION")) {
                assertThat(RecetaCuestionarioTecnico.validar(nivel, borradorPerfecto(nivel)))
                        .as(nivel).isEmpty();
            }
        }

        @Test
        @DisplayName("el orden de los bloques no se negocia")
        void elOrdenManda() {
            List<PreguntaGenerada> desordenado = new ArrayList<>(borradorPerfecto("EJECUCION"));
            PreguntaGenerada primera = desordenado.remove(0);
            desordenado.add(primera);

            assertThat(RecetaCuestionarioTecnico.validar("EJECUCION", desordenado))
                    .anyMatch(e -> e.contains("el bloque debía ser"));
        }

        @Test
        @DisplayName("sin guía de calificación no pasa: el evaluador no puede inventarse qué contar")
        void sinGuiaNoPasa() {
            List<PreguntaGenerada> sinC3 = new ArrayList<>(borradorPerfecto("EJECUCION"));
            PreguntaGenerada p = sinC3.get(2);
            sinC3.set(2, new PreguntaGenerada(p.codigo(), p.bloque(), p.bloqueEtiqueta(),
                    p.enunciado(), null, p.c4Esperado(), p.senalDeCero(), p.presencial()));

            assertThat(RecetaCuestionarioTecnico.validar("EJECUCION", sinC3))
                    .anyMatch(e -> e.contains("guía de calificación incompleta"));
        }

        @Test
        @DisplayName("presencial fuera de su bloque no pasa: la muestra jamás viaja al candidato")
        void presencialFueraDeSuBloque() {
            List<PreguntaGenerada> conFuga = new ArrayList<>(borradorPerfecto("EJECUCION"));
            PreguntaGenerada p = conFuga.get(0);
            conFuga.set(0, new PreguntaGenerada(p.codigo(), p.bloque(), p.bloqueEtiqueta(),
                    p.enunciado(), p.c3Esperado(), p.c4Esperado(), p.senalDeCero(), true));

            assertThat(RecetaCuestionarioTecnico.validar("EJECUCION", conFuga))
                    .anyMatch(e -> e.contains("presencial"));
        }

        @Test
        @DisplayName("los temas prohibidos de la clienta se atrapan, con y sin acento")
        void loProhibidoSeAtrapa() {
            List<PreguntaGenerada> conTabu = new ArrayList<>(borradorPerfecto("EJECUCION"));
            PreguntaGenerada p = conTabu.get(3);
            conTabu.set(3, new PreguntaGenerada(p.codigo(), p.bloque(), p.bloqueEtiqueta(),
                    "¿Cuál es tu religión y tienes hijos?",
                    p.c3Esperado(), p.c4Esperado(), p.senalDeCero(), p.presencial()));

            List<String> errores = RecetaCuestionarioTecnico.validar("EJECUCION", conTabu);
            assertThat(errores).anyMatch(e -> e.contains("religi"));
            assertThat(errores).anyMatch(e -> e.contains("hijos"));
        }

        @Test
        @DisplayName("«políticas de crédito» es una pregunta legítima: no se atrapa")
        void politicasDeCreditoEsLegitima() {
            List<PreguntaGenerada> legitimo = new ArrayList<>(borradorPerfecto("EJECUCION"));
            PreguntaGenerada p = legitimo.get(3);
            legitimo.set(3, new PreguntaGenerada(p.codigo(), p.bloque(), p.bloqueEtiqueta(),
                    "¿Qué políticas de crédito aplicabas y cómo cuidabas la salud financiera "
                            + "de la caja?",
                    p.c3Esperado(), p.c4Esperado(), p.senalDeCero(), p.presencial()));

            assertThat(RecetaCuestionarioTecnico.validar("EJECUCION", legitimo)).isEmpty();
        }

        @Test
        @DisplayName("los errores se acumulan todos, no el primero")
        void todosLosErrores() {
            List<PreguntaGenerada> roto = List.of(new PreguntaGenerada(
                    null, "EXPERIENCIA", null, null, null, null, null, false));

            assertThat(RecetaCuestionarioTecnico.validar("EJECUCION", roto))
                    .hasSizeGreaterThanOrEqualTo(3);
        }
    }

    @Nested
    @DisplayName("La aduana de publicación")
    class Publicacion {

        private List<PreguntaPublicable> publicables(String nivel) {
            return borradorPerfecto(nivel).stream()
                    .map(p -> new PreguntaPublicable(p.codigo(), p.enunciado(),
                            p.c3Esperado(), p.c4Esperado(), p.senalDeCero(),
                            Boolean.TRUE.equals(p.presencial())))
                    .toList();
        }

        @Test
        @DisplayName("lo que salió del agente y nadie tocó, publica")
        void loIntactoPublica() {
            assertThat(RecetaCuestionarioTecnico.validarPublicacion(
                    "DIRECCION", publicables("DIRECCION"))).isEmpty();
        }

        @Test
        @DisplayName("si el dueño borró la señal de 0 al editar, no publica")
        void sinSenalNoPublica() {
            List<PreguntaPublicable> tocado = new ArrayList<>(publicables("SUPERVISION"));
            PreguntaPublicable p = tocado.get(4);
            tocado.set(4, new PreguntaPublicable(p.codigo(), p.enunciado(),
                    p.c3Esperado(), p.c4Esperado(), null, p.presencial()));

            assertThat(RecetaCuestionarioTecnico.validarPublicacion("SUPERVISION", tocado))
                    .anyMatch(e -> e.contains("guía de calificación incompleta"));
        }

        @Test
        @DisplayName("DIR sin su presencial no publica: la muestra es parte de la estructura")
        void dirSinPresencial() {
            List<PreguntaPublicable> sinMuestra = publicables("DIRECCION").stream()
                    .map(p -> new PreguntaPublicable(p.codigo(), p.enunciado(),
                            p.c3Esperado() == null ? "dato" : p.c3Esperado(),
                            p.c4Esperado() == null ? "incomodo" : p.c4Esperado(),
                            p.senalDeCero() == null ? "senal" : p.senalDeCero(), false))
                    .toList();

            assertThat(RecetaCuestionarioTecnico.validarPublicacion("DIRECCION", sinMuestra))
                    .anyMatch(e -> e.contains("presencial"));
        }
    }
}
