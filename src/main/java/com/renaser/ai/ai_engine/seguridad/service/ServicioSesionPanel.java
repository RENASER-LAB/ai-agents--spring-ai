package com.renaser.ai.ai_engine.seguridad.service;

import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.PermisoDeLaSesion;
import com.renaser.ai.ai_engine.seguridad.dto.DtosSeguridad.SesionDelPanel;
import com.renaser.ai.ai_engine.usuario.entity.Persona;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.PersonaRepository;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * La sesión del panel: nombre, correo, empresa y permisos con su alcance (V64).
 *
 * <p>Hasta aquí el panel no sabía cómo se llamaba quien entró ni qué podía hacer: cada DTO
 * traía sus {@code puedeX}. El menú lateral necesita saber qué entradas pintar antes de abrir
 * ninguna pantalla, y para eso existe esto. Los permisos salen del mismo contexto que usa cada
 * petición, así que un cambio en la matriz se ve al volver a pedirla.
 */
@Service
@RequiredArgsConstructor
public class ServicioSesionPanel {

    private final UsuarioRepository usuarios;
    private final PersonaRepository personas;
    private final OrganizacionRepository organizaciones;

    public SesionDelPanel de(ContextoUsuario quien) {
        Usuario usuario = usuarios.findById(quien.usuarioId()).orElse(null);
        Persona persona = usuario == null || usuario.getPersonaId() == null ? null
                : personas.findById(usuario.getPersonaId()).orElse(null);
        String nombre = persona == null || persona.getAnonimizadoEn() != null ? null
                : (Optional.ofNullable(persona.getNombre()).orElse("") + " "
                   + Optional.ofNullable(persona.getApellidos()).orElse("")).strip();
        String empresa = organizaciones.findById(quien.organizacionId())
                .map(Organizacion::getNombre).orElse(null);
        List<PermisoDeLaSesion> permisos = quien.permisos().entrySet().stream()
                .map(e -> new PermisoDeLaSesion(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(PermisoDeLaSesion::codigo))
                .toList();
        return new SesionDelPanel(quien.usuarioId(), nombre == null || nombre.isEmpty() ? null : nombre,
                usuario == null ? null : usuario.getCorreo(), quien.organizacionId(), empresa, permisos);
    }
}
