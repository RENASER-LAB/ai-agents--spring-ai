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

    List<TextoConsentimientoPanel> textosConsentimiento(ContextoUsuario quien);

    // Crea la versión nueva del texto legal Y LA PUBLICA: es lo que le abre a la empresa
    // la puerta de publicar vacantes (pieza D). Una versión publicada jamás se toca; los
    // consentimientos ya firmados siguen apuntando a la suya.
    Long publicarTextoConsentimiento(ContextoUsuario quien, NuevoTextoConsentimiento datos);

    Page<FilaAuditoria> auditoria(ContextoUsuario quien, String entidad, int pagina, int tamano);

    // El borrado 29733 vive aparte, en ServicioBorradoDatos: es el código más
    // destructivo del sistema y no debe compartir techo con editar un parámetro.

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
