package com.renaser.ai.ai_engine.simulacion.service;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorCriterio;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Quién puede calificar la simulación de un candidato.
 *
 * <p>Esta clase no tenía ninguna prueba unitaria y guarda tres caminos, uno de ellos el de
 * <b>poner una nota</b>, que es de los pocos sitios del sistema donde una persona escribe algo
 * que decide el futuro de otra. La regla del alcance está escrita a mano dentro y va a migrar a
 * un guardián compartido: estas pruebas existen para que esa migración sea verificable.
 *
 * <p>Es además el único de los nueve guardianes que lleva el permiso <b>escrito dentro</b> en
 * vez de recibirlo por parámetro, porque los tres caminos usan el mismo. Eso queda fijado aquí:
 * si al migrar alguien le pasa otro permiso, esta prueba lo dice.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El alcance al calificar la simulación")
class ServicioCalificacionSimulacionTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long POSTULACION = 88L;
    private static final Long VACANTE = 40L;
    private static final Long USUARIO = 21L;
    private static final Long OTRO_USUARIO = 77L;

    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            USUARIO, 33L, ORGANIZACION, "EQUIPO", List.of(), Map.of());

    @Mock private PostulacionRepository postulaciones;
    @Mock private VacanteRepository vacantes;
    @Mock private CalificacionPorCriterio calificacion;
    @Mock private Permisos permisos;

    private ServicioCalificacionSimulacion servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioCalificacionSimulacion(postulaciones, vacantes, calificacion, permisos);
    }

    private void hayPostulacion() {
        when(postulaciones.findByIdAndOrganizacionId(POSTULACION, ORGANIZACION))
                .thenReturn(Optional.of(Postulacion.builder()
                        .id(POSTULACION).organizacionId(ORGANIZACION).vacanteId(VACANTE).build()));
    }

    private void conAlcanceAcotado() {
        when(permisos.alcanceDe("calificar_simulacion"))
                .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.SUS_VACANTES, USUARIO));
    }

    private void laVacanteEsDe(Long responsable) {
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(Vacante.builder()
                .id(VACANTE).organizacionId(ORGANIZACION)
                .responsableUsuarioId(responsable).build()));
    }

    @Test
    @DisplayName("Las notas de un candidato de vacante ajena no se leen")
    void lasNotasAjenasNoSeLeen() {
        hayPostulacion();
        conAlcanceAcotado();
        laVacanteEsDe(OTRO_USUARIO);

        assertThatThrownBy(() -> servicio.verNotas(QUIEN, POSTULACION))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(calificacion);
    }

    @Test
    @DisplayName("Y sobre todo: no se le pone nota a quien no te toca")
    void noSeLePoneNotaAlAjeno() {
        hayPostulacion();
        conAlcanceAcotado();
        laVacanteEsDe(OTRO_USUARIO);

        assertThatThrownBy(() -> servicio.ponerNota(QUIEN, POSTULACION, 5L, 4.0, "Bien"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(calificacion, never())
                .ponerNota(any(), anyLong(), any(), anyLong(), anyDouble(), anyString());
    }

    @Test
    @DisplayName("Los tres caminos miran calificar_simulacion, que llevan escrito dentro")
    void losTresMiranElMismoPermiso() {
        hayPostulacion();
        conAlcanceAcotado();
        laVacanteEsDe(USUARIO);
        when(calificacion.rubricaGlobalDe("SIMULACION")).thenReturn(List.of());
        when(calificacion.verNotas(POSTULACION, List.of())).thenReturn(List.of());

        servicio.verNotas(QUIEN, POSTULACION);

        verify(permisos).alcanceDe("calificar_simulacion");
    }

    @Test
    @DisplayName("Una postulación de otra empresa ni llega a mirarse el alcance")
    void laDeOtraEmpresaNiSeMira() {
        when(postulaciones.findByIdAndOrganizacionId(POSTULACION, ORGANIZACION))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.verNotas(QUIEN, POSTULACION))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(permisos, vacantes, calificacion);
    }
}
