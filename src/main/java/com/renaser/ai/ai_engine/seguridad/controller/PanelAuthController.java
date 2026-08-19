package com.renaser.ai.ai_engine.seguridad.controller;

import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.DevLogin;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.Sesion;
import com.renaser.ai.ai_engine.seguridad.service.ServicioAccesoEquipo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// La entrada del equipo MIENTRAS NO EXISTE el contrato con RENASER OS. En producción
// se apaga con app.seguridad.dev-login-activo=false y la identidad la emite RENASER OS.
@RestController
@RequestMapping("/api/v1/panel/auth")
@RequiredArgsConstructor
@Tag(name = "Panel · autenticación", description = "Login de desarrollo del equipo")
public class PanelAuthController {

    private final ServicioAccesoEquipo acceso;

    @PostMapping("/dev-login")
    @Operation(summary = "Login de desarrollo: emite un token de equipo sin RENASER OS. "
            + "El primer id que entra se crea solo, con todos los roles del equipo (bootstrap)")
    public Sesion devLogin(@Valid @RequestBody DevLogin datos) {
        return acceso.devLogin(datos.usuarioRenaserOsId());
    }
}
