package com.renaser.ai.ai_engine.validacion.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.parametro.service.ServicioParametros;
import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorCriterio;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.MaquinaEstados;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.usuario.repository.RolRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRolRepository;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;
import com.renaser.ai.ai_engine.validacion.repository.ValidacionRepository;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Quién puede mirar y mover el periodo de validación de un candidato.
 *
 * <p>Esta clase no tenía ninguna prueba unitaria, y guarda seis caminos con cuatro permisos
 * distintos. La regla del alcance está escrita a mano dentro, igual que en otros seis
 * servicios, y va a migrar a un guardián compartido: estas pruebas existen para que esa
 * migración sea verificable, porque el error probable no es de compilación sino <b>pegar el
 * permiso de al lado</b>. Si eso pasa nada falla —el endpoint sigue guardado por su
 * {@code @PreAuthorize}— y lo único que cambia es de qué permiso sale el alcance: un rol con
 * uno acotado y otro libre usaría el segundo para saltarse el primero.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El alcance sobre el periodo de validación")
class ServicioValidacionImplTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long POSTULACION = 88L;
    private static final Long VACANTE = 40L;
    private static final Long USUARIO = 21L;
    private static final Long OTRO_USUARIO = 77L;

    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            USUARIO, 33L, ORGANIZACION, "EQUIPO", List.of(), Map.of());

    @Mock private ValidacionRepository validaciones;
    @Mock private PostulacionRepository postulaciones;
    @Mock private AlcanceSobreLaVacante alcance;
    @Mock private RolRepository roles;
    @Mock private UsuarioRolRepository usuarioRoles;
    @Mock private CalificacionPorCriterio calificacion;
    @Mock private MaquinaEstados maquina;
    @Mock private ServicioParametros parametros;
    @Mock private ServicioAuditoria auditoria;
    @Mock private Permisos permisos;

    private ServicioValidacionImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioValidacionImpl(validaciones, postulaciones, alcance, roles,
                usuarioRoles, calificacion, maquina, parametros, auditoria, permisos);
    }

    /** El guardián deja pasar: la postulación es de esta empresa y el alcance llega. */
    private void alcanzable(String permiso) {
        when(alcance.laPostulacionVisible(any(), eq(POSTULACION), eq(permiso)))
                .thenReturn(Postulacion.builder()
                        .id(POSTULACION).organizacionId(ORGANIZACION).vacanteId(VACANTE).build());
    }

    /** El guardián dice que no, con el mismo 404 que si la postulación no existiera. */
    private void fueraDeAlcance(String permiso) {
        when(alcance.laPostulacionVisible(any(), eq(POSTULACION), eq(permiso)))
                .thenThrow(new ResourceNotFoundException("Postulación", "id", POSTULACION));
    }

    @Test
    @DisplayName("Ver el periodo de una vacante ajena responde 404, no 403")
    void verElDeUnaVacanteAjena() {
        fueraDeAlcance("completar_metricas_validacion");

        assertThatThrownBy(() -> servicio.ver(QUIEN, POSTULACION))
                .as("un 403 confirmaría que ese candidato está en validación")
                .isInstanceOf(ResourceNotFoundException.class);

        // Y no se llega a leer el periodo de alguien que no le toca.
        verify(validaciones, never()).findByPostulacionId(POSTULACION);
    }

    @Test
    @DisplayName("Habilitar mira su propio permiso, no el de ver las métricas")
    void habilitarMiraSuPermiso() {
        fueraDeAlcance("habilitar_validacion");

        assertThatThrownBy(() -> servicio.habilitar(QUIEN, POSTULACION, null))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(alcance).laPostulacionVisible(any(), eq(POSTULACION), eq("habilitar_validacion"));
        verify(alcance, never()).laPostulacionVisible(
                any(), eq(POSTULACION), eq("completar_metricas_validacion"));
    }

    @Test
    @DisplayName("Cerrar mira el suyo, que es el más caro de confundir")
    void cerrarMiraSuPermiso() {
        // Cerrar termina el periodo del candidato. Si el alcance saliera de un permiso de
        // lectura, quien solo puede mirar acabaría cerrando periodos de convocatorias ajenas.
        fueraDeAlcance("cerrar_validacion");

        assertThatThrownBy(() -> servicio.cerrar(QUIEN, POSTULACION))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(alcance).laPostulacionVisible(any(), eq(POSTULACION), eq("cerrar_validacion"));
        verify(maquina, never()).transicionar(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Con la vacante suya sí pasa del guardián")
    void conLaVacanteSuyaPasa() {
        alcanzable("completar_metricas_validacion");
        when(validaciones.findByPostulacionId(POSTULACION)).thenReturn(Optional.empty());

        // Pasa el guardián y falla más adelante, al no haber periodo: es la prueba de que el
        // 404 de arriba lo lanzaba el alcance y no la falta de datos.
        assertThatThrownBy(() -> servicio.ver(QUIEN, POSTULACION))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(validaciones).findByPostulacionId(POSTULACION);
    }
}
