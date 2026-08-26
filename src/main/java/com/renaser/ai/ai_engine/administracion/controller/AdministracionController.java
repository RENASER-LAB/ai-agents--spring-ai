package com.renaser.ai.ai_engine.administracion.controller;

import com.renaser.ai.ai_engine.administracion.service.ServicioAdministracion;

import com.renaser.ai.ai_engine.administracion.dto.DtosAdministracion.*;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/panel")
@RequiredArgsConstructor
@Tag(name = "Panel · Administración",
        description = "Parámetros, correos, auditoría, borrados, usuarios, roles y permisos")
public class AdministracionController {

    private final ServicioAdministracion servicio;
    private final Permisos permisos;

    // ---------- Parámetros ----------

    @GetMapping("/parametros")
    @PreAuthorize("@permisos.tiene('editar_parametros')")
    @Operation(summary = "Los parámetros del sistema")
    public List<ParametroPanel> parametros() {
        return servicio.parametros(permisos.actual());
    }

    @PutMapping("/parametros/{codigo}")
    @PreAuthorize("@permisos.tiene('editar_parametros')")
    @Operation(summary = "Editar un parámetro, con motivo. Queda auditado con el valor anterior")
    public void editarParametro(@PathVariable String codigo, @Valid @RequestBody EditarParametro datos) {
        servicio.editarParametro(permisos.actual(), codigo, datos.valor(), datos.motivo());
    }

    // ---------- Plantillas de correo ----------

    @GetMapping("/plantillas-correo")
    @PreAuthorize("@permisos.tiene('editar_textos_correo')")
    @Operation(summary = "Todas las versiones de los textos de correo")
    public List<PlantillaPanel> plantillas() {
        return servicio.plantillas(permisos.actual());
    }

    @PostMapping("/plantillas-correo")
    @PreAuthorize("@permisos.tiene('editar_textos_correo')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Editar un texto = crear una versión nueva; la anterior queda para la historia")
    public Map<String, Long> nuevaPlantilla(@Valid @RequestBody NuevaPlantilla datos) {
        return Map.of("id", servicio.nuevaVersionPlantilla(permisos.actual(), datos));
    }

    // ---------- Auditoría ----------

    @GetMapping("/auditoria")
    @PreAuthorize("@permisos.tiene('ver_auditoria')")
    @Operation(summary = "El registro de auditoría, paginado; entidad filtra por tabla")
    public Page<FilaAuditoria> auditoria(@RequestParam(required = false) String entidad,
                                         @RequestParam(defaultValue = "0") int pagina,
                                         @RequestParam(defaultValue = "50") int tamano) {
        return servicio.auditoria(permisos.actual(), entidad, pagina, tamano);
    }

    // ---------- Borrado de datos ----------

    @GetMapping("/solicitudes-borrado")
    @PreAuthorize("@permisos.tiene('ejecutar_borrado_datos')")
    @Operation(summary = "Las solicitudes de borrado pendientes")
    public List<SolicitudBorradoPanel> solicitudesBorrado() {
        return servicio.solicitudesBorradoPendientes(permisos.actual());
    }

    @PostMapping("/solicitudes-borrado/{id}/ejecucion")
    @PreAuthorize("@permisos.tiene('ejecutar_borrado_datos')")
    @Operation(summary = "Ejecutar la anonimización: vacía a la persona, borra el CV físico "
            + "y conserva la trazabilidad sin nombre")
    public void ejecutarBorrado(@PathVariable Long id) {
        servicio.ejecutarBorrado(permisos.actual(), id);
    }

    // ---------- Usuarios del equipo y roles ----------

    @GetMapping("/usuarios")
    @PreAuthorize("@permisos.tiene('crear_usuarios_y_asignar_roles')")
    @Operation(summary = "Los usuarios del equipo, con sus roles")
    public List<UsuarioPanel> usuarios() {
        return servicio.usuariosEquipo(permisos.actual());
    }

    @PostMapping("/usuarios")
    @PreAuthorize("@permisos.tiene('crear_usuarios_y_asignar_roles')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dar de alta a alguien del equipo, con su id de RENASER OS y sus roles")
    public Map<String, Long> crearUsuario(@Valid @RequestBody CrearUsuarioEquipo datos) {
        return Map.of("id", servicio.crearUsuarioEquipo(permisos.actual(), datos));
    }

    @PostMapping("/usuarios/{id}/roles")
    @PreAuthorize("@permisos.tiene('crear_usuarios_y_asignar_roles')")
    @Operation(summary = "Reemplazar los roles de un usuario. El último administrador no se puede quitar")
    public void asignarRoles(@PathVariable Long id, @Valid @RequestBody AsignarRoles datos) {
        servicio.asignarRoles(permisos.actual(), id, datos.roles());
    }

    @GetMapping("/areas")
    @PreAuthorize("@permisos.tiene('ver_solicitudes')")
    @Operation(summary = "Las áreas activas: hace falta una para registrar una solicitud")
    public List<AreaPanel> areas() {
        return servicio.areas(permisos.actual());
    }

    @PostMapping("/areas")
    @PreAuthorize("@permisos.tiene('crear_usuarios_y_asignar_roles')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un área (estructura de la organización: la administra el Administrador)")
    public Map<String, Long> crearArea(@Valid @RequestBody CrearArea datos) {
        return Map.of("id", servicio.crearArea(permisos.actual(), datos.nombre()));
    }

    @GetMapping("/roles")
    @PreAuthorize("@permisos.tiene('crear_usuarios_y_asignar_roles')")
    @Operation(summary = "Los roles que existen")
    public List<RolPanel> roles() {
        return servicio.roles(permisos.actual());
    }

    // ---------- Qué puede cada rol ----------
    //
    // Con permiso propio y no con crear_usuarios_y_asignar_roles: dar un rol a alguien es
    // una cosa, redefinir lo que ese rol significa es otra bastante mayor —quien escribe
    // aquí puede concederse todo—, y separarlas permite tener a quien haga lo primero sin
    // poder hacer lo segundo.

    @GetMapping("/roles/{id}/permisos")
    @PreAuthorize("@permisos.tiene('administrar_permisos')")
    @Operation(summary = "La matriz de un rol: el catálogo entero, con el alcance de lo que "
            + "tiene concedido y vacío en lo que no")
    public List<PermisoDelRol> permisosDelRol(@PathVariable Long id) {
        return servicio.permisosDelRol(permisos.actual(), id);
    }

    @PutMapping("/roles/{id}/permisos/{codigo}")
    @PreAuthorize("@permisos.tiene('administrar_permisos')")
    @Operation(summary = "Conceder el permiso o cambiarle el alcance. Surte efecto en la "
            + "siguiente petición de cada afectado: no hace falta que vuelva a entrar")
    public void concederPermiso(@PathVariable Long id, @PathVariable String codigo,
                                @Valid @RequestBody ConcederPermiso datos) {
        servicio.concederPermiso(permisos.actual(), id, codigo, datos);
    }

    // POST y no DELETE porque el motivo es obligatorio y va en el cuerpo: hay proxies y
    // clientes que descartan el cuerpo de un DELETE, y ahí el motivo llegaría vacío y esto
    // respondería un 400 que nadie sabe explicar. Misma forma que `/cancelacion` en las
    // sesiones y `/ejecucion` en los borrados, que son igual de definitivos.
    @PostMapping("/roles/{id}/permisos/{codigo}/revocacion")
    @PreAuthorize("@permisos.tiene('administrar_permisos')")
    @Operation(summary = "Quitarle el permiso al rol. El último «administrar_permisos» no se "
            + "puede quitar: dejaría el reparto sin nadie que pudiera tocarlo")
    public void revocarPermiso(@PathVariable Long id, @PathVariable String codigo,
                               @Valid @RequestBody RevocarPermiso datos) {
        servicio.revocarPermiso(permisos.actual(), id, codigo, datos.motivo());
    }
}
