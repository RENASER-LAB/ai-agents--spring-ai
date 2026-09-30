package com.renaser.ai.ai_engine.perfilintegral.service;

import com.renaser.ai.ai_engine.perfilintegral.entity.CriterioBanco;
import com.renaser.ai.ai_engine.perfilintegral.entity.NotaRespuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Opcion;
import com.renaser.ai.ai_engine.perfilintegral.entity.Pregunta;
import com.renaser.ai.ai_engine.perfilintegral.entity.Respuesta;
import com.renaser.ai.ai_engine.perfilintegral.entity.VersionBanco;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioBancoRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.NotaRespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.OpcionRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PreguntaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.RespuestaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.VersionBancoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("La nota de cada pregunta propia, calculada al leer")
class CalificacionPorPuntosTest {

    private static Pregunta pregunta(String tipo, int puntos) {
        return Pregunta.builder().id(1L).tipo(tipo).enunciado("¿?").puntos(puntos).build();
    }

    private static Opcion opcion(long id, String puntos, int orden) {
        return Opcion.builder().id(id).preguntaId(1L).texto("o" + id)
                .puntaje(new BigDecimal(puntos)).orden(orden).build();
    }

    @Test
    @DisplayName("Una abierta de 0 puntos se guarda pero no suma ni espera a la IA (AC-07)")
    void laDeCeroPuntosNoEspera() {
        Respuesta escrita = Respuesta.builder().id(9L).texto("Quiero aprender cierres").build();
        CalificacionPorPuntos.PreguntaCalculada p =
                CalificacionPorPuntos.calcularPregunta(pregunta("ABIERTA", 0), List.of(), escrita, null);
        assertThat(p.pendiente()).isFalse();
        assertThat(p.obtenido()).isEqualByComparingTo("0");
        assertThat(p.vaALaIa()).isFalse();
    }

    @Test
    @DisplayName("Una abierta con puntos y sin nota está pendiente; en blanco vale 0 y no va a la IA")
    void abiertaPendienteYEnBlanco() {
        Respuesta escrita = Respuesta.builder().id(9L).texto("En marzo…").build();
        CalificacionPorPuntos.PreguntaCalculada pendiente =
                CalificacionPorPuntos.calcularPregunta(pregunta("ABIERTA", 20), List.of(), escrita, null);
        assertThat(pendiente.pendiente()).isTrue();
        assertThat(pendiente.obtenido()).isNull();
        assertThat(pendiente.vaALaIa()).isTrue();

        Respuesta enBlanco = Respuesta.builder().id(10L).texto("  ").build();
        CalificacionPorPuntos.PreguntaCalculada blanco =
                CalificacionPorPuntos.calcularPregunta(pregunta("ABIERTA", 20), List.of(), enBlanco, null);
        assertThat(blanco.pendiente()).isFalse();
        assertThat(blanco.obtenido()).isEqualByComparingTo("0");
        assertThat(blanco.vaALaIa()).isFalse();

        NotaRespuesta nota = NotaRespuesta.builder().respuestaId(9L).puntaje(new BigDecimal("12.5")).build();
        assertThat(CalificacionPorPuntos.calcularPregunta(pregunta("ABIERTA", 20), List.of(), escrita, nota)
                .obtenido()).isEqualByComparingTo("12.5");
    }

    @Test
    @DisplayName("La múltiple se lee de detalle.marcadas, con tope y piso")
    void laMultipleDesdeElDetalle() {
        List<Opcion> suyas = List.of(opcion(1, "6", 1), opcion(2, "6", 2), opcion(3, "-4", 3));
        Respuesta tres = Respuesta.builder().id(9L).detalle("{\"marcadas\":[1,2,3]}").build();
        Respuesta mala = Respuesta.builder().id(9L).detalle("{\"marcadas\":[3]}").build();
        assertThat(CalificacionPorPuntos.calcularPregunta(pregunta("OPCION_MULTIPLE", 10), suyas, tres, null)
                .obtenido()).isEqualByComparingTo("8");
        assertThat(CalificacionPorPuntos.calcularPregunta(pregunta("OPCION_MULTIPLE", 10), suyas, mala, null)
                .obtenido()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("La escala: sus niveles salen en su orden (1…10) y vale lo del elegido; sin responder, 0")
    void laEscala() {
        List<Opcion> niveles = new java.util.ArrayList<>();
        for (int i = 10; i >= 1; i--) {
            niveles.add(opcion(i, String.valueOf(i - 1), i));
        }
        Respuesta octavo = Respuesta.builder().id(9L).opcionId(8L).build();
        CalificacionPorPuntos.PreguntaCalculada p =
                CalificacionPorPuntos.calcularPregunta(pregunta("ESCALA", 9), niveles, octavo, null);
        assertThat(p.opciones()).extracting(Opcion::getOrden)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(p.obtenido()).isEqualByComparingTo("7");
        assertThat(CalificacionPorPuntos.calcularPregunta(pregunta("ESCALA", 9), niveles, null, null)
                .obtenido()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("La nota del banco es la suma de los criterios, sin ponderar cerradas y abiertas por cantidad (AC-11)")
    void laNotaEsLaSumaDeLosCriterios() {
        VersionBancoRepository versiones = mock(VersionBancoRepository.class);
        CriterioBancoRepository criterios = mock(CriterioBancoRepository.class);
        PreguntaRepository preguntas = mock(PreguntaRepository.class);
        OpcionRepository opciones = mock(OpcionRepository.class);
        RespuestaRepository respuestas = mock(RespuestaRepository.class);
        NotaRespuestaRepository notas = mock(NotaRespuestaRepository.class);
        CalificacionPorPuntos cuenta = new CalificacionPorPuntos(versiones, criterios, preguntas,
                opciones, respuestas, notas);

        // «Redacción»: una abierta de 60 con 30 de la IA. «Contabilidad»: cuatro únicas de 10,
        // las cuatro bien. Ponderando por cantidad (4 cerradas al 100 % y 1 abierta al 50 %)
        // saldría 90; por puntos son 30 + 40 = 70.
        when(versiones.findById(70L)).thenReturn(Optional.of(
                VersionBanco.builder().id(70L).metodoCalificacion("PUNTOS").build()));
        when(criterios.findByVersionBancoIdOrderByOrdenAscIdAsc(70L)).thenReturn(List.of(
                CriterioBanco.builder().id(1L).nombre("Redacción").orden(1).build(),
                CriterioBanco.builder().id(2L).nombre("Contabilidad").orden(2).build()));
        List<Pregunta> suyas = new ArrayList<>();
        suyas.add(Pregunta.builder().id(100L).tipo("ABIERTA").enunciado("¿A?").puntos(60)
                .criterioBancoId(1L).orden(1).build());
        List<Opcion> todasLasOpciones = new ArrayList<>();
        List<Respuesta> suyasRespondidas = new ArrayList<>();
        suyasRespondidas.add(Respuesta.builder().id(900L).preguntaId(100L).texto("Mi cierre…").build());
        for (long i = 1; i <= 4; i++) {
            long id = 100L + i;
            suyas.add(Pregunta.builder().id(id).tipo("OPCION_UNICA").enunciado("¿" + i + "?").puntos(10)
                    .criterioBancoId(2L).orden((int) i + 1).build());
            todasLasOpciones.add(Opcion.builder().id(id * 10).preguntaId(id).texto("Bien")
                    .puntaje(BigDecimal.TEN).orden(1).build());
            todasLasOpciones.add(Opcion.builder().id(id * 10 + 1).preguntaId(id).texto("Mal")
                    .puntaje(BigDecimal.ZERO).orden(2).build());
            suyasRespondidas.add(Respuesta.builder().id(900L + i).preguntaId(id).opcionId(id * 10).build());
        }
        when(preguntas.findByVersionBancoIdOrderByOrden(70L)).thenReturn(suyas);
        when(opciones.findByPreguntaIdIn(anyList())).thenReturn(todasLasOpciones);
        when(respuestas.findByEvaluacionId(50L)).thenReturn(suyasRespondidas);
        when(notas.findByRespuestaIdIn(anyList())).thenReturn(List.of(NotaRespuesta.builder()
                .respuestaId(900L).puntaje(new BigDecimal("30")).build()));

        CalificacionPorPuntos.Resultado r = cuenta.calcular(70L, 50L);

        assertThat(r.completo()).isTrue();
        assertThat(r.criterios()).extracting(CalificacionPorPuntos.CriterioCalculado::nota)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("30"), new BigDecimal("40"));
        assertThat(r.nota()).isEqualByComparingTo("70");
    }
}
