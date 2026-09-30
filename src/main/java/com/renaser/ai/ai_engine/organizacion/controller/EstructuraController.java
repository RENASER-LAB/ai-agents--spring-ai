package com.renaser.ai.ai_engine.organizacion.controller;

import com.renaser.ai.ai_engine.organizacion.dto.DtosEstructura.CrearCargo;
import com.renaser.ai.ai_engine.organizacion.dto.DtosEstructura.GuardarSede;
import com.renaser.ai.ai_engine.organizacion.dto.DtosEstructura.ListaDeCargos;
import com.renaser.ai.ai_engine.organizacion.dto.DtosEstructura.ListaDeSedes;
import com.renaser.ai.ai_engine.organizacion.dto.DtosEstructura.RenombrarCargo;
import com.renaser.ai.ai_engine.organizacion.service.ServicioEstructura;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Las sedes y los cargos de la empresa, en Configuración (V64).
 *
 * <p>Leer no pide permiso concreto —son la estructura de la empresa y cualquiera del equipo
 * las ve en Configuración, sin acciones—; escribir pide {@code editar_estructura}.
 */
@RestController
@RequestMapping("/api/v1/panel")
@RequiredArgsConstructor
@Tag(name = "Panel · Estructura", description = "Sedes y cargos de la empresa")
public class EstructuraController {

    private final ServicioEstructura servicio;
    private final Permisos permisos;

    // ---------- Sedes ----------

    @GetMapping("/sedes")
    @Operation(summary = "Las sedes de la empresa, activas y desactivadas")
    public ListaDeSedes sedes() {
        return servicio.sedes(permisos.actual());
    }

    @PostMapping("/sedes")
    @PreAuthorize("@permisos.tiene('editar_estructura')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Añadir una sede. El nombre es único en la empresa, sin distinguir mayúsculas")
    public Map<String, Long> crearSede(@Valid @RequestBody GuardarSede datos) {
        return Map.of("id", servicio.crearSede(permisos.actual(), datos));
    }

    @PutMapping("/sedes/{id}")
    @PreAuthorize("@permisos.tiene('editar_estructura')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Editar una sede")
    public void editarSede(@PathVariable Long id, @Valid @RequestBody GuardarSede datos) {
        servicio.editarSede(permisos.actual(), id, datos);
    }

    @PostMapping("/sedes/{id}/desactivacion")
    @PreAuthorize("@permisos.tiene('editar_estructura')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desactivar una sede: deja de poder elegirse y quien está en ella la conserva")
    public void desactivarSede(@PathVariable Long id) {
        servicio.activarSede(permisos.actual(), id, false);
    }

    @PostMapping("/sedes/{id}/reactivacion")
    @PreAuthorize("@permisos.tiene('editar_estructura')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reactivar una sede")
    public void reactivarSede(@PathVariable Long id) {
        servicio.activarSede(permisos.actual(), id, true);
    }

    // ---------- Cargos ----------

    @GetMapping("/cargos")
    @Operation(summary = "El catálogo de cargos (los puestos), activos y desactivados, con cuántas "
            + "vacantes y personas lo usan")
    public ListaDeCargos cargos() {
        return servicio.cargos(permisos.actual());
    }

    @PostMapping("/cargos")
    @PreAuthorize("@permisos.tiene('editar_estructura')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Añadir un cargo con su nivel y su familia")
    public Map<String, Long> crearCargo(@Valid @RequestBody CrearCargo datos) {
        return Map.of("id", servicio.crearCargo(permisos.actual(), datos));
    }

    @PutMapping("/cargos/{id}")
    @PreAuthorize("@permisos.tiene('editar_estructura')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Renombrar un cargo. El nombre nuevo sale en todas partes, vacantes incluidas")
    public void renombrarCargo(@PathVariable Long id, @Valid @RequestBody RenombrarCargo datos) {
        servicio.renombrarCargo(permisos.actual(), id, datos.nombre());
    }

    @PostMapping("/cargos/{id}/desactivacion")
    @PreAuthorize("@permisos.tiene('editar_estructura')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desactivar un cargo: deja de poder elegirse y lo que ya lo usa lo conserva")
    public void desactivarCargo(@PathVariable Long id) {
        servicio.activarCargo(permisos.actual(), id, false);
    }

    @PostMapping("/cargos/{id}/reactivacion")
    @PreAuthorize("@permisos.tiene('editar_estructura')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reactivar un cargo")
    public void reactivarCargo(@PathVariable Long id) {
        servicio.activarCargo(permisos.actual(), id, true);
    }
}
