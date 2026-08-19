package com.renaser.ai.ai_engine.seguridad.service.impl;

import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.seguridad.config.PropiedadesSeguridad;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.Sesion;
import com.renaser.ai.ai_engine.seguridad.service.ProveedorIdentidadEquipo;
import com.renaser.ai.ai_engine.seguridad.service.ServicioAccesoEquipo;
import com.renaser.ai.ai_engine.seguridad.service.ServicioToken;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.entity.UsuarioRol;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.RolRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRolRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Ver {@link ServicioAccesoEquipo}. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicioAccesoEquipoImpl implements ServicioAccesoEquipo {

    private final PropiedadesSeguridad propiedades;
    private final ProveedorIdentidadEquipo proveedor;
    private final ServicioToken tokens;
    private final OrganizacionRepository organizaciones;
    private final PersonaRepository personas;
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final UsuarioRolRepository usuarioRoles;

    @Override
    @Transactional
    public Sesion devLogin(String usuarioRenaserOsId) {
        if (!propiedades.isDevLoginActivo()) {
            throw new IllegalStateException("El login de desarrollo está apagado: la identidad "
                    + "del equipo viene de RENASER OS");
        }
        Usuario usuario = proveedor.autenticarDesarrollo(null, usuarioRenaserOsId)
                .orElseGet(() -> arrancarPrimerUsuario(usuarioRenaserOsId));
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
