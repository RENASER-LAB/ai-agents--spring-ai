package com.renaser.ai.ai_engine.administracion.service;

import com.renaser.ai.ai_engine.administracion.dto.DtosAdministracion.*;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ServicioAdministracion {

    List<ParametroPanel> parametros(ContextoUsuario quien);

    void editarParametro(ContextoUsuario quien, String codigo, String valor, String motivo);

    List<PlantillaPanel> plantillas(ContextoUsuario quien);

    // Editar un texto crea una versión nueva y desactiva la anterior: lo enviado con la
    // versión vieja sigue explicado por ella
    Long nuevaVersionPlantilla(ContextoUsuario quien, NuevaPlantilla datos);

    Page<FilaAuditoria> auditoria(ContextoUsuario quien, String entidad, int pagina, int tamano);

    List<SolicitudBorradoPanel> solicitudesBorradoPendientes(ContextoUsuario quien);

    // La anonimización: vacía a la persona, conserva la trazabilidad
    void ejecutarBorrado(ContextoUsuario quien, Long solicitudId);

    List<UsuarioPanel> usuariosEquipo(ContextoUsuario quien);

    Long crearUsuarioEquipo(ContextoUsuario quien, CrearUsuarioEquipo datos);

    void asignarRoles(ContextoUsuario quien, Long usuarioId, List<String> rolesNuevos);

    List<RolPanel> roles(ContextoUsuario quien);

    // ---------- Qué puede cada rol ----------
    //
    // El reparto vive en rol_permiso y el FiltroIdentidad lo relee en cada petición, así que
    // lo que se cambie aquí surte efecto en la siguiente llamada de cada afectado, sin
    // desplegar y sin que nadie tenga que volver a entrar.

    List<PermisoDelRol> permisosDelRol(ContextoUsuario quien, Long rolId);

    /** Concede el permiso, o le cambia el alcance si el rol ya lo tenía. */
    void concederPermiso(ContextoUsuario quien, Long rolId, String codigoPermiso,
                         ConcederPermiso datos);

    void revocarPermiso(ContextoUsuario quien, Long rolId, String codigoPermiso, String motivo);

    // Las áreas reflejan la estructura de Renaser. Sin un área no se puede registrar
    // una Solicitud de Talento.
    List<AreaPanel> areas(ContextoUsuario quien);

    Long crearArea(ContextoUsuario quien, String nombre);
}
