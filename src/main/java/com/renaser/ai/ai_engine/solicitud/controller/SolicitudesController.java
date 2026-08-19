package com.renaser.ai.ai_engine.solicitud.controller;

import com.renaser.ai.ai_engine.solicitud.service.ServicioSolicitudes;

import com.renaser.ai.ai_engine.solicitud.dto.DtosSolicitud.*;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/panel/solicitudes")
@RequiredArgsConstructor
@Tag(name = "Panel · Solicitudes de Talento", description = "Talento prepara, Dirección aprueba")
public class SolicitudesController {

    private final ServicioSolicitudes servicio;
    private final Permisos permisos;

    @PostMapping
    @PreAuthorize("@permisos.tiene('crear_solicitud')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una Solicitud de Talento, con sus 3 a 5 resultados esperados")
    public Map<String, Long> crear(@Valid @RequestBody CrearSolicitud datos) {
        return Map.of("id", servicio.crear(permisos.actual(), datos));
    }

    @GetMapping
    @PreAuthorize("@permisos.tiene('ver_solicitudes')")
    @Operation(summary = "Las solicitudes visibles según el alcance de quien mira")
    public List<SolicitudResumen> listar() {
        return servicio.listar(permisos.actual());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permisos.tiene('ver_solicitudes')")
    @Operation(summary = "El detalle completo de una solicitud")
    public SolicitudDetalle detalle(@PathVariable Long id) {
        return servicio.detalle(permisos.actual(), id);
    }

    @PostMapping("/{id}/aprobacion")
    @PreAuthorize("@permisos.tiene('aprobar_solicitud')")
    @Operation(summary = "Dirección aprueba: la solicitud queda ABIERTA y admite vacante")
    public void aprobar(@PathVariable Long id, @Valid @RequestBody Decidir datos) {
        servicio.aprobar(permisos.actual(), id, datos.motivo());
    }

    @PostMapping("/{id}/rechazo")
    @PreAuthorize("@permisos.tiene('aprobar_solicitud')")
    @Operation(summary = "Dirección rechaza, con motivo")
    public void rechazar(@PathVariable Long id, @Valid @RequestBody Decidir datos) {
        servicio.rechazar(permisos.actual(), id, datos.motivo());
    }
}
