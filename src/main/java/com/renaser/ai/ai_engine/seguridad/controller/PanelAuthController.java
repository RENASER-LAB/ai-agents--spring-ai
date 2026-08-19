package com.renaser.ai.ai_engine.seguridad.controller;

import com.renaser.ai.ai_engine.usuario.entity.*;
import com.renaser.ai.ai_engine.usuario.repository.*;
import com.renaser.ai.ai_engine.organizacion.entity.*;
import com.renaser.ai.ai_engine.organizacion.repository.*;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.DevLogin;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.Sesion;
import com.renaser.ai.ai_engine.seguridad.config.PropiedadesSeguridad;
import com.renaser.ai.ai_engine.seguridad.service.ProveedorIdentidadEquipo;
import com.renaser.ai.ai_engine.seguridad.service.ServicioToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

// La entrada del equipo MIENTRAS NO EXISTE el contrato con RENASER OS. En producción
// se apaga con app.seguridad.dev-login-activo=false y la identidad la emite RENASER OS.
@RestController
@RequestMapping("/api/v1/panel/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Panel · autenticación", description = "Login de desarrollo del equipo")
public class PanelAuthController {

    private final PropiedadesSeguridad propiedades;
    private final ProveedorIdentidadEquipo proveedor;
    private final ServicioToken tokens;
    private final OrganizacionRepository organizaciones;
    private final PersonaRepository personas;
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final UsuarioRolRepository usuarioRoles;

    @PostMapping("/dev-login")
    @Transactional
    @Operation(summary = "Login de desarrollo: emite un token de equipo sin RENASER OS. "
            + "El primer id que entra se crea solo, con todos los roles del equipo (bootstrap)")
    public Sesion devLogin(@Valid @RequestBody DevLogin datos) {
        if (!propiedades.isDevLoginActivo()) {
            throw new IllegalStateException("El login de desarrollo está apagado: la identidad del equipo viene de RENASER OS");
        }
        Usuario usuario = proveedor.autenticarDesarrollo(null, datos.usuarioRenaserOsId())
                .orElseGet(() -> arrancarPrimerUsuario(datos.usuarioRenaserOsId()));
        return new Sesion(tokens.emitir(usuario.getId(), usuario.getOrganizacionId(), "EQUIPO"),
                usuario.getId());
    }

    // Bootstrap: en una base recién migrada no hay nadie del equipo y sin alguien del
    // equipo no se pueden crear usuarios. El primer id que entre por el dev-login se
    // crea con los roles operativos completos. Solo pasa una vez y solo en desarrollo.
    private Usuario arrancarPrimerUsuario(String usuarioRenaserOsId) {
        Organizacion org = organizaciones.findByCodigo("RENASER")
                .orElseThrow(() -> new IllegalStateException("Falta la organización semilla RENASER"));
        if (!usuarios.findByOrganizacionIdAndUsuarioRenaserOsIdIsNotNull(org.getId()).isEmpty()) {
            // Ya hay equipo: un id desconocido no entra, se crea desde administración
            throw new IllegalArgumentException("Ese id de RENASER OS no está registrado en el sistema");
        }
        log.warn("Bootstrap de desarrollo: creando el primer usuario del equipo ({})", usuarioRenaserOsId);
        Persona persona = personas.save(Persona.builder()
                .nombre("Equipo").apellidos("Desarrollo").creadoEn(Instant.now())
                .build());
        Usuario usuario = usuarios.save(Usuario.builder()
                .organizacionId(org.getId())
                .personaId(persona.getId())
                .usuarioRenaserOsId(usuarioRenaserOsId)
                .esActivo(true)
                .creadoEn(Instant.now())
                .build());
        for (String codigo : List.of("TALENTO", "DIRECCION", "ADMINISTRADOR")) {
            roles.findByOrganizacionIdAndCodigo(org.getId(), codigo).ifPresent(rol ->
                    usuarioRoles.save(UsuarioRol.builder()
                            .usuarioId(usuario.getId()).rolId(rol.getId()).creadoEn(Instant.now())
                            .build()));
        }
        return usuario;
    }
}
