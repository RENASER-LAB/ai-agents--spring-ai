package com.renaser.ai.ai_engine.seguridad.controller;

import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.SesionDelPanel;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.seguridad.service.ServicioSesionPanel;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La sesión de quien está en el panel, para pintar el menú (V64).
 *
 * <p>Sin permiso concreto: cualquier cuenta de equipo puede preguntar quién es y qué puede
 * hacer. No abre nada: la API sigue respondiendo 403 y 404 como antes.
 */
@RestController
@RequestMapping("/api/v1/panel/sesion")
@RequiredArgsConstructor
@Tag(name = "Panel · Sesión", description = "Quién entró, de qué empresa y con qué permisos")
public class SesionPanelController {

    private final ServicioSesionPanel servicio;
    private final Permisos permisos;

    @GetMapping
    @Operation(summary = "La sesión del panel: nombre, correo, empresa y permisos con su alcance. "
            + "Solo sirve para pintar el menú")
    public SesionDelPanel sesion() {
        return servicio.de(permisos.actual());
    }
}
