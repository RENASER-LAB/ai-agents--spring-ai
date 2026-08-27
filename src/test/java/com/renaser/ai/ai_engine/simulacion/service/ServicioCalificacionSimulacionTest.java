package com.renaser.ai.ai_engine.simulacion.service;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorCriterio;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Quién puede calificar la simulación de un candidato.
 *
 * <p>Esta clase guarda tres caminos, uno de ellos el de <b>poner una nota</b>, que es de los
 * pocos sitios del sistema donde una persona escribe algo que decide el futuro de otra. Qué
 * postulaciones alcanza quien mira lo decide {@code AlcanceSobreLaVacante} y allí tiene sus
 * pruebas; lo que se comprueba aquí es que se le pregunta por el permiso correcto y que su no
 * llega intacto, sin calificar nada por el camino.
 *
 * <p>Es además el único de los nueve que lleva el permiso <b>escrito dentro</b> en vez de
 * recibirlo por parámetro, porque los tres caminos usan el mismo. Eso queda fijado aquí: si
 * alguien le pasara otro permiso al guardián, esta prueba lo dice.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El alcance al calificar la simulación")
class ServicioCalificacionSimulacionTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long POSTULACION = 88L;
    private static final Long VACANTE = 40L;
    private static final Long USUARIO = 21L;

    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            USUARIO, 33L, ORGANIZACION, "EQUIPO", List.of(), Map.of());

    @Mock private AlcanceSobreLaVacante alcance;
    @Mock private CalificacionPorCriterio calificacion;

    private ServicioCalificacionSimulacion servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioCalificacionSimulacion(alcance, calificacion);
    }

    /** Lo que contesta el guardián cuando la postulación es alcanzable. */
    private void alcanzable() {
        when(alcance.laPostulacionVisible(any(), eq(POSTULACION), eq("calificar_simulacion")))
                .thenReturn(Postulacion.builder()
                        .id(POSTULACION).organizacionId(ORGANIZACION).vacanteId(VACANTE).build());
    }

    /** Y lo que contesta cuando no lo es: el mismo 404 que si no existiera. */
    private void fueraDeAlcance() {
        when(alcance.laPostulacionVisible(any(), eq(POSTULACION), eq("calificar_simulacion")))
                .thenThrow(new ResourceNotFoundException("Postulación", "id", POSTULACION));
    }

    @Test
    @DisplayName("Las notas de un candidato de vacante ajena no se leen")
    void lasNotasAjenasNoSeLeen() {
        fueraDeAlcance();

        assertThatThrownBy(() -> servicio.verNotas(QUIEN, POSTULACION))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(calificacion);
    }

    @Test
    @DisplayName("Y sobre todo: no se le pone nota a quien no te toca")
    void noSeLePoneNotaAlAjeno() {
        fueraDeAlcance();

        assertThatThrownBy(() -> servicio.ponerNota(QUIEN, POSTULACION, 5L, 4.0, "Bien"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(calificacion, never())
                .ponerNota(any(), anyLong(), any(), anyLong(), anyDouble(), anyString());
    }

    @Test
    @DisplayName("Los tres caminos miran calificar_simulacion, que llevan escrito dentro")
    void losTresMiranElMismoPermiso() {
        alcanzable();
        when(calificacion.rubricaGlobalDe("SIMULACION")).thenReturn(List.of());
        when(calificacion.verNotas(POSTULACION, List.of())).thenReturn(List.of());

        servicio.verNotas(QUIEN, POSTULACION);

        verify(alcance).laPostulacionVisible(any(), eq(POSTULACION), eq("calificar_simulacion"));
    }
}
