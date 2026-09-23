package com.renaser.ai.ai_engine.seguridad.controller;

import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.AceptarInvitacion;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.DevLogin;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.Login;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.PedirRecuperacion;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.RestablecerClave;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.Sesion;
import com.renaser.ai.ai_engine.seguridad.service.ServicioAccesoEquipo;
import com.renaser.ai.ai_engine.seguridad.service.ServicioInvitaciones;
import com.renaser.ai.ai_engine.seguridad.service.ServicioRecuperacionClave;
import com.renaser.ai.ai_engine.seguridad.service.ServicioRecuperacionClave.Publico;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// La entrada del equipo al panel, de Renaser y de todas las empresas: correo y
// contraseña, cuentas que nacen solo por invitación. RENASER OS queda dormido; el
// dev-login sobrevive apagado por defecto, para local y para las pruebas.
@RestController
@RequestMapping("/api/v1/panel/auth")
@RequiredArgsConstructor
@Tag(name = "Panel · autenticación", description = "Login del panel de empresas e invitaciones")
public class PanelAuthController {

    private final ServicioAccesoEquipo acceso;
    private final ServicioInvitaciones invitaciones;
    private final ServicioRecuperacionClave recuperacion;

    @PostMapping("/login")
    @Operation(summary = "Entrar al panel con correo y contraseña. Solo cuentas de equipo: "
            + "la contraseña de un candidato no abre esta puerta")
    public Sesion entrar(@Valid @RequestBody Login datos) {
        return acceso.entrar(datos);
    }

    // El token del cuerpo ES la credencial: quien lo tiene todavía no tiene cuenta, por
    // eso la ruta es pública (permitAll en la cadena panel). Lo que acota el riesgo está
    // en ServicioInvitaciones: 32 bytes de azar, solo el hash guardado, vence, un solo uso.
    @PostMapping("/invitacion")
    @Operation(summary = "Canjear una invitación: pone nombre y contraseña, crea la cuenta "
            + "de equipo con los roles invitados y devuelve la sesión")
    public Sesion aceptarInvitacion(@Valid @RequestBody AceptarInvitacion datos) {
        return invitaciones.aceptar(datos);
    }

    // «Me olvidé mi contraseña», del lado del equipo. Las mismas reglas que en el portal:
    // 202 siempre y sin cuerpo; el enlace llega por correo, uno por cada empresa en la que
    // ese correo tenga cuenta, y lleva a la pantalla del panel y a ninguna otra.
    @PostMapping("/recuperacion")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Pedir el enlace para elegir una contraseña nueva del panel. "
            + "Responde 202 siempre, exista o no la cuenta")
    public void pedirRecuperacion(@RequestBody PedirRecuperacion datos, HttpServletRequest request) {
        recuperacion.solicitar(Publico.EQUIPO, datos.correo(), request.getRemoteAddr());
    }

    // El token del cuerpo es la credencial, como en la invitación. No abre sesión.
    @PostMapping("/restablecer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elegir la contraseña nueva del panel con el enlace del correo. 204 "
            + "si cambió; el mismo 401 para cualquier enlace que no sirva")
    public void restablecerClave(@Valid @RequestBody RestablecerClave datos) {
        recuperacion.restablecer(Publico.EQUIPO, datos.token(), datos.contrasena());
    }

    @PostMapping("/dev-login")
    @Operation(summary = "Login de desarrollo: emite un token de equipo sin contraseña. "
            + "Apagado por defecto; el primer id que entra se crea solo (bootstrap)")
    public Sesion devLogin(@Valid @RequestBody DevLogin datos) {
        return acceso.devLogin(datos.usuarioRenaserOsId());
    }
}
