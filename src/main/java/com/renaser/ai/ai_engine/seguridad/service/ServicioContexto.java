package com.renaser.ai.ai_engine.seguridad.service;

import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import com.renaser.ai.ai_engine.usuario.repository.RolPermisoRepository;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.entity.UsuarioRol;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Arma el ContextoUsuario de una petición: roles del usuario y sus permisos con el
// alcance efectivo. Si dos roles dan el mismo permiso, gana el alcance más amplio.
@Service
@RequiredArgsConstructor
public class ServicioContexto {

    private static final Map<String, Integer> AMPLITUD = Map.of(
            "PROPIO", 1, "SUS_VACANTES", 2, "TODO", 3);

    private final UsuarioRolRepository usuarioRoles;
    private final RolPermisoRepository rolPermisos;

    public ContextoUsuario armar(Usuario usuario, String tipo) {
        List<Long> rolIds = usuarioRoles.findByUsuarioId(usuario.getId()).stream()
                .map(UsuarioRol::getRolId)
                .toList();

        Map<String, String> permisos = new HashMap<>();
        if (!rolIds.isEmpty()) {
            for (Object[] fila : rolPermisos.permisosDeRoles(rolIds)) {
                String codigo = (String) fila[0];
                String alcance = (String) fila[1];
                permisos.merge(codigo, alcance, (actual, nuevo) ->
                        AMPLITUD.get(nuevo) > AMPLITUD.get(actual) ? nuevo : actual);
            }
        }
        return new ContextoUsuario(usuario.getId(), usuario.getPersonaId(),
                usuario.getOrganizacionId(), tipo, rolIds, Map.copyOf(permisos));
    }
}
