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

    // Las áreas reflejan la estructura de Renaser. Sin un área no se puede registrar
    // una Solicitud de Talento.
    List<AreaPanel> areas(ContextoUsuario quien);

    Long crearArea(ContextoUsuario quien, String nombre);
}
