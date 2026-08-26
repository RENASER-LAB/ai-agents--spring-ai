package com.renaser.ai.ai_engine.seguridad.service.impl;

import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.seguridad.service.ProveedorIdentidadEquipo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

// Implementación de DESARROLLO: encuentra al usuario del equipo por su id de RENASER OS
// y le emite un token propio, sin hablar con RENASER OS. Cuando exista el contrato real,
// la implementación nueva valida el token que RENASER OS emite y esta se retira.
@Service
@RequiredArgsConstructor
public class ProveedorIdentidadEquipoDesarrollo implements ProveedorIdentidadEquipo {

    private final UsuarioRepository usuarios;
    private final OrganizacionRepository organizaciones;

    @Override
    public Optional<Usuario> autenticarDesarrollo(String correo, String usuarioRenaserOsId) {
        // Sin contraseña: es un login de desarrollo, protegido por app.seguridad.dev-login-activo.
        // Solo conoce a la plataforma: los ids de RENASER OS son de la casa, no de las empresas.
        return organizaciones.findByEsPlataformaTrue()
                .map(Organizacion::getId)
                .flatMap(orgId -> usuarios.findByOrganizacionIdAndUsuarioRenaserOsId(orgId, usuarioRenaserOsId))
                .filter(Usuario::isEsActivo);
    }
}
