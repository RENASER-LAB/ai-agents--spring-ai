package com.renaser.ai.ai_engine.simulacion.service.impl;

import com.renaser.ai.ai_engine.perfilintegral.entity.Alerta;
import com.renaser.ai.ai_engine.perfilintegral.repository.AlertaRepository;
import com.renaser.ai.ai_engine.perfilintegral.repository.PerfilTalentoRepository;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorCriterio;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.PreguntaIa;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacionIa.ResultadoConversacion;
import com.renaser.ai.ai_engine.simulacion.entity.PreguntaGenerada;
import com.renaser.ai.ai_engine.simulacion.repository.InscripcionSesionRepository;
import com.renaser.ai.ai_engine.simulacion.repository.PreguntaGeneradaRepository;
import com.renaser.ai.ai_engine.vacante.entity.Puesto;
import com.renaser.ai.ai_engine.vacante.service.ContextoDeLaVacante;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lo que el agente de la conversación final puede escribir, y lo que no puede tocar.
 *
 * <p>La regla que más importa es la primera: <b>lo que se dijo en la sala no se borra</b>.
 * Las preguntas se pueden volver a pedir —porque se ajustó una nota, o porque la primera
 * tanda salió floja—, y una pregunta que ya se le leyó al candidato y él contestó es un
 * hecho ocurrido. Si una segunda pasada la barriera, quedaría una respuesta huérfana o, peor,
 * desaparecería del expediente la única prueba de que ese riesgo se aclaró.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El puente entre la IA y la conversación final")
class PuenteSimulacionIaImplTest {

    private static final long POSTULACION = 55L;

    @Mock private PostulacionRepository postulaciones;
    @Mock private PerfilTalentoRepository perfiles;
    @Mock private AlertaRepository alertas;
    @Mock private CalificacionPorCriterio calificacion;
    @Mock private InscripcionSesionRepository inscripciones;
    @Mock private PreguntaGeneradaRepository preguntas;
    @Mock private ContextoDeLaVacante contexto;

    @InjectMocks
    private PuenteSimulacionIaImpl puente;

    // ============ Lo que se le manda al agente ============

    @Test
    void sinNadaDeLoQuePreguntarNoSeGastaUnaLlamada() {
        // Sin retrato, sin notas y sin eventos marcados, el modelo solo podría escribir
        // preguntas de manual. Esas no aportan nada que no estuviera ya en el currículum, y
        // producirlas cuesta lo mismo que producir las buenas.
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(postulacion()));
        when(contexto.puestoDe(any(Postulacion.class))).thenReturn(puesto());
        when(perfiles.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());
        when(alertas.findByPostulacionId(POSTULACION)).thenReturn(List.of());
        when(calificacion.rubricaGlobalDe("SIMULACION")).thenReturn(List.of());
        when(inscripciones.findByPostulacionIdAndEsVigenteTrue(POSTULACION))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> puente.insumoConversacion(POSTULACION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nada de lo que preguntar");
    }

    // ============ Lo que se guarda de lo que contesta ============

    @Test
    void lasPreguntasYaContestadasSeQuedanYLasDemasSeRehacen() {
        PreguntaGenerada contestada = pregunta(1L, "¿Qué pasó en esos ocho minutos?", 1,
                "Dije que no lo vi venir");
        PreguntaGenerada sinContestar = pregunta(2L, "Una que nadie llegó a hacer", 2, null);
        montar(List.of(contestada, sinContestar), List.of());

        puente.guardarPreguntas(POSTULACION, 77L, new ResultadoConversacion(List.of(
                new PreguntaIa("Nueva pregunta", "Sale de la línea de tiempo", null))));

        verify(preguntas).delete(sinContestar);
        verify(preguntas, never()).delete(contestada);

        ArgumentCaptor<PreguntaGenerada> guardada =
                ArgumentCaptor.forClass(PreguntaGenerada.class);
        verify(preguntas).save(guardada.capture());
        // Se numera detrás de la que ya se hizo, no encima de ella.
        assertThat(guardada.getValue().getOrden()).isEqualTo(2);
        assertThat(guardada.getValue().getTexto()).isEqualTo("Nueva pregunta");
        assertThat(guardada.getValue().getMotivo()).isEqualTo("Sale de la línea de tiempo");
        assertThat(guardada.getValue().getEjecucionIaId()).isEqualTo(77L);
    }

    @Test
    void unaAlertaQueNoEsDeEsteCandidatoSeDescarta() {
        // Un id inventado dejaría la pregunta apuntando a la contradicción de otra persona.
        // Vale más una pregunta sin alerta que una atada a la equivocada.
        montar(List.of(), List.of(alerta(9L)));

        puente.guardarPreguntas(POSTULACION, 77L, new ResultadoConversacion(List.of(
                new PreguntaIa("Una pregunta", "Un motivo", 404L))));

        ArgumentCaptor<PreguntaGenerada> guardada =
                ArgumentCaptor.forClass(PreguntaGenerada.class);
        verify(preguntas).save(guardada.capture());
        assertThat(guardada.getValue().getAlertaId()).isNull();
    }

    @Test
    void laAlertaDeLaQueSaleQuedaAtadaALaPregunta() {
        // Es lo que permite ver después si ese riesgo concreto quedó resuelto en la
        // conversación o sigue abierto cuando llega la decisión.
        montar(List.of(), List.of(alerta(9L)));

        puente.guardarPreguntas(POSTULACION, 77L, new ResultadoConversacion(List.of(
                new PreguntaIa("Una pregunta", "Un motivo", 9L))));

        ArgumentCaptor<PreguntaGenerada> guardada =
                ArgumentCaptor.forClass(PreguntaGenerada.class);
        verify(preguntas).save(guardada.capture());
        assertThat(guardada.getValue().getAlertaId()).isEqualTo(9L);
    }

    @Test
    void nuncaSeGuardanMasDeCincoPreguntas() {
        // La conversación dura quince minutos. Ocho preguntas no son más información: son la
        // misma conversación corriendo, en la que ninguna respuesta se llega a rascar.
        montar(List.of(), List.of());
        List<PreguntaIa> ocho = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            ocho.add(new PreguntaIa("Pregunta " + i, "Motivo " + i, null));
        }

        puente.guardarPreguntas(POSTULACION, 77L, new ResultadoConversacion(ocho));

        verify(preguntas, times(5)).save(any(PreguntaGenerada.class));
    }

    @Test
    void unResultadoVacioNoSeGuarda() {
        assertThatThrownBy(() -> puente.guardarPreguntas(POSTULACION, 77L, null))
                .isInstanceOf(IllegalStateException.class);

        verify(preguntas, never()).save(any());
    }

    // ============ Apoyo ============

    private void montar(List<PreguntaGenerada> yaEstan, List<Alerta> suyas) {
        when(postulaciones.findById(POSTULACION)).thenReturn(Optional.of(postulacion()));
        when(preguntas.findByPostulacionIdOrderByOrden(POSTULACION)).thenReturn(yaEstan);
        when(alertas.findByPostulacionId(POSTULACION)).thenReturn(suyas);
    }

    private Postulacion postulacion() {
        return Postulacion.builder()
                .id(POSTULACION).organizacionId(1L).vacanteId(7L)
                .estadoCodigo("SIMULACION_POR_CONFIRMAR")
                .build();
    }

    private Puesto puesto() {
        return Puesto.builder()
                .id(3L).nombre("Analista de procesos").nivelPuestoCodigo("OPERATIVO")
                .build();
    }

    private Alerta alerta(Long id) {
        return Alerta.builder()
                .id(id).postulacionId(POSTULACION).tipo("CONTRADICCION")
                .descripcion("Dice que avisa pronto")
                .build();
    }

    private PreguntaGenerada pregunta(Long id, String texto, int orden, String respuesta) {
        return PreguntaGenerada.builder()
                .id(id).postulacionId(POSTULACION).texto(texto).orden(orden)
                .respuesta(respuesta)
                .build();
    }
}
