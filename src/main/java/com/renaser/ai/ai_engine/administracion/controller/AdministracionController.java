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
@Tag(name = "Panel · Administración", description = "Parámetros, correos, auditoría, borrados, usuarios")
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
}
