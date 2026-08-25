package com.renaser.ai.ai_engine.perfil.controller;

import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.PerfilCompleto;
import com.renaser.ai.ai_engine.perfil.service.ServicioPerfilPanel;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El perfil del candidato en la ficha del panel: solo lectura.
 *
 * <p>El perfil NO puntúa — no entra en notas ni en el ranking. Sirve para leer a un
 * candidato sin abrir su currículum, y la pantalla distingue lo que la persona escribió
 * de lo que se sacó del archivo.
 */
@RestController
@RequestMapping("/api/v1/panel")
@RequiredArgsConstructor
@Tag(name = "Panel · Perfil del candidato", description = "La trayectoria del candidato "
        + "sin abrir su archivo")
public class PerfilPanelController {

    private final ServicioPerfilPanel servicio;
    private final Permisos permisos;

    @GetMapping("/postulaciones/{id}/perfil")
    @PreAuthorize("@permisos.tiene('ver_perfil_candidato')")
    @Operation(summary = "El perfil del candidato de esta postulación. Sin perfil responde "
            + "200 con todo vacío; la pretensión solo viaja con el permiso ver_pretension")
    public PerfilCompleto ver(@PathVariable Long id) {
        return servicio.verDePostulacion(permisos.actual(), id);
    }
}
