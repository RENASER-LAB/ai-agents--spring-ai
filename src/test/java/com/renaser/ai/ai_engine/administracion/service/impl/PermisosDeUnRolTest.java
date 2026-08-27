package com.renaser.ai.ai_engine.administracion.service.impl;

import com.renaser.ai.ai_engine.administracion.dto.DtosAdministracion.ConcederPermiso;
import com.renaser.ai.ai_engine.administracion.dto.DtosAdministracion.PermisoDelRol;
import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.usuario.entity.Permiso;
import com.renaser.ai.ai_engine.usuario.entity.Rol;
import com.renaser.ai.ai_engine.usuario.entity.RolPermiso;
import com.renaser.ai.ai_engine.usuario.repository.PermisoRepository;
import com.renaser.ai.ai_engine.usuario.repository.RolPermisoRepository;
import com.renaser.ai.ai_engine.usuario.repository.RolRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cambiar lo que puede cada rol, desde el panel y sin desplegar.
 *
 * <p>Esto existe porque el reparto de {@code rol_permiso} no es una decisión de una vez:
 * mañana puede hacer falta que el responsable de área deje de ver algo, o que empiece a
 * verlo. El {@code FiltroIdentidad} relee los permisos en cada petición, así que lo que se
 * escribe aquí surte efecto en la siguiente llamada de cada afectado — sin desplegar y sin
 * que nadie tenga que volver a entrar.
 *
 * <p>Lo que se prueba, entonces, no es que la fila se guarde: es lo que protege a la mano
 * que la escribe de dejar el sistema inservible o sin rastro.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Los permisos de un rol")
class PermisosDeUnRolTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long ROL = 4L;

    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            10L, 20L, ORGANIZACION, "EQUIPO", List.of(5L), Map.of());

    @Mock private RolRepository roles;
    @Mock private PermisoRepository permisos;
    @Mock private RolPermisoRepository rolPermisos;
    @Mock private ServicioAuditoria auditoria;

    @InjectMocks private ServicioAdministracionImpl servicio;

    private static Rol elRol() {
        return Rol.builder().id(ROL).organizacionId(ORGANIZACION)
                .codigo("RESPONSABLE_AREA").nombre("Responsable del área").esSistema(true).build();
    }

    private static Permiso permiso(Long id, String codigo) {
        return Permiso.builder().id(id).codigo(codigo).etiqueta("Etiqueta de " + codigo)
                .grupo("SESIONES").orden(1).build();
    }

    @Test
    @DisplayName("La matriz trae el catálogo entero, con alcance vacío en lo que el rol no tiene")
    void laMatrizTraeTambienLoQueFalta() {
        when(roles.findById(ROL)).thenReturn(Optional.of(elRol()));
        when(rolPermisos.findByRolId(ROL)).thenReturn(List.of(
                RolPermiso.builder().rolId(ROL).permisoId(30L).alcance("SUS_VACANTES").build()));
        when(permisos.findAllByOrderByGrupoAscOrdenAsc()).thenReturn(List.of(
                permiso(30L, "ver_inscritos_simulacion"),
                permiso(31L, "decidir_sobre_ausente")));

        List<PermisoDelRol> matriz = servicio.permisosDelRol(QUIEN, ROL);

        // Solo lo concedido no serviría: quien administra necesita ver lo que falta para
        // poder añadirlo. El alcance vacío es «este rol no tiene esto».
        assertThat(matriz).extracting(PermisoDelRol::codigo)
                .containsExactly("ver_inscritos_simulacion", "decidir_sobre_ausente");
        assertThat(matriz.get(0).alcance()).isEqualTo("SUS_VACANTES");
        assertThat(matriz.get(1).alcance()).isNull();
    }

    @Test
    @DisplayName("Conceder guarda el alcance y deja escrito el motivo")
    void concederDejaRastro() {
        when(roles.findById(ROL)).thenReturn(Optional.of(elRol()));
        when(permisos.findByCodigo("ver_inscritos_simulacion"))
                .thenReturn(Optional.of(permiso(30L, "ver_inscritos_simulacion")));
        when(rolPermisos.findById(any())).thenReturn(Optional.empty());

        servicio.concederPermiso(QUIEN, ROL, "ver_inscritos_simulacion",
                new ConcederPermiso("SUS_VACANTES", "El área conduce sus propias sesiones"));

        ArgumentCaptor<RolPermiso> guardado = ArgumentCaptor.captor();
        verify(rolPermisos).save(guardado.capture());
        assertThat(guardado.getValue().getAlcance()).isEqualTo("SUS_VACANTES");
        assertThat(guardado.getValue().getRolId()).isEqualTo(ROL);

        // Esto cambia lo que un grupo de personas puede hacer con los datos de candidatos
        // reales. Sin fila de auditoría no hay forma de contestar «¿desde cuándo, y por qué?».
        verify(auditoria).registrar(eq(ORGANIZACION), eq(QUIEN), eq("conceder_permiso"),
                eq("rol"), eq(ROL), any(), any(), eq("El área conduce sus propias sesiones"));
    }

    @Test
    @DisplayName("Volver a conceder el mismo alcance no escribe nada")
    void elMismoAlcanceNoEsUnCambio() {
        when(roles.findById(ROL)).thenReturn(Optional.of(elRol()));
        when(permisos.findByCodigo(anyString()))
                .thenReturn(Optional.of(permiso(30L, "ver_inscritos_simulacion")));
        when(rolPermisos.findById(any())).thenReturn(Optional.of(
                RolPermiso.builder().rolId(ROL).permisoId(30L).alcance("TODO").build()));

        servicio.concederPermiso(QUIEN, ROL, "ver_inscritos_simulacion",
                new ConcederPermiso("TODO", "por si acaso"));

        // Un panel que reenvía la fila entera al guardar mandaría esto en cada pulsación:
        // la auditoría se llenaría de cambios que no cambiaron nada y taparía los que sí.
        verify(rolPermisos, never()).save(any());
        verify(auditoria, never()).registrar(any(), any(), anyString(), anyString(), any(),
                any(), any(), anyString());
    }

    @Test
    @DisplayName("El último «administrar_permisos» no se puede revocar")
    void elUltimoQueAdministraNoSeVa() {
        when(roles.findById(ROL)).thenReturn(Optional.of(elRol()));
        when(permisos.findByCodigo("administrar_permisos"))
                .thenReturn(Optional.of(permiso(40L, "administrar_permisos")));
        when(rolPermisos.findById(any())).thenReturn(Optional.of(
                RolPermiso.builder().rolId(ROL).permisoId(40L).alcance("TODO").build()));
        when(rolPermisos.contarEnOrganizacion(40L, ORGANIZACION)).thenReturn(1L);

        assertThatThrownBy(() -> servicio.revocarPermiso(QUIEN, ROL, "administrar_permisos", "limpieza"))
                .as("quitarlo deja el reparto sin nadie que pueda volver a tocarlo, y de ahí "
                        + "solo se sale entrando a la base a mano")
                .isInstanceOf(IllegalStateException.class);

        verify(rolPermisos, never()).delete(any());
    }

    @Test
    @DisplayName("Con otro rol que también lo tenga, revocarlo sí se puede")
    void conOtroQueAdministreSiSePuede() {
        when(roles.findById(ROL)).thenReturn(Optional.of(elRol()));
        when(permisos.findByCodigo("administrar_permisos"))
                .thenReturn(Optional.of(permiso(40L, "administrar_permisos")));
        when(rolPermisos.findById(any())).thenReturn(Optional.of(
                RolPermiso.builder().rolId(ROL).permisoId(40L).alcance("TODO").build()));
        when(rolPermisos.contarEnOrganizacion(40L, ORGANIZACION)).thenReturn(2L);

        servicio.revocarPermiso(QUIEN, ROL, "administrar_permisos", "lo lleva Dirección");

        verify(rolPermisos).delete(any());
    }

    @Test
    @DisplayName("El candado cuenta dentro de la organización, no en toda la base")
    void elCandadoNoSeAbrePorLoQueTengaOtraOrganizacion() {
        when(roles.findById(ROL)).thenReturn(Optional.of(elRol()));
        when(permisos.findByCodigo("administrar_permisos"))
                .thenReturn(Optional.of(permiso(40L, "administrar_permisos")));
        when(rolPermisos.findById(any())).thenReturn(Optional.of(
                RolPermiso.builder().rolId(ROL).permisoId(40L).alcance("TODO").build()));
        // En global hay dos filas; en esta organización, una sola.
        when(rolPermisos.contarEnOrganizacion(40L, ORGANIZACION)).thenReturn(1L);

        assertThatThrownBy(() -> servicio.revocarPermiso(QUIEN, ROL, "administrar_permisos", "limpieza"))
                .as("que otra organización tenga el suyo no cubre a esta: se quedaría sin "
                        + "administrador creyendo que alguien la respalda")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Un permiso que no está en el catálogo se rechaza antes de tocar la base")
    void unCodigoInventadoNoLlegaALaBase() {
        when(roles.findById(ROL)).thenReturn(Optional.of(elRol()));
        when(permisos.findByCodigo("ver_lo_que_sea")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.concederPermiso(QUIEN, ROL, "ver_lo_que_sea",
                new ConcederPermiso("TODO", "probando")))
                .as("dejarlo llegar a la clave ajena daría un 500 con el nombre de una "
                        + "restricción dentro, y lo que pasó es que el permiso no existe")
                .isInstanceOf(ResourceNotFoundException.class);

        verify(rolPermisos, never()).save(any());
    }

    @Test
    @DisplayName("Un rol de otra organización no existe para quien pregunta")
    void elRolDeOtraOrganizacionNoExiste() {
        when(roles.findById(ROL)).thenReturn(Optional.of(
                Rol.builder().id(ROL).organizacionId(99L).codigo("TALENTO").build()));

        assertThatThrownBy(() -> servicio.permisosDelRol(QUIEN, ROL))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
