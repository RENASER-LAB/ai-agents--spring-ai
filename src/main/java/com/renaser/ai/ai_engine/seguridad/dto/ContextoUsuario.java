package com.renaser.ai.ai_engine.seguridad.dto;

import java.util.List;
import java.util.Map;

// Quién está llamando, resuelto una vez por petición: usuario, organización, roles y el
// mapa permiso -> alcance efectivo. Si dos roles dan el mismo permiso con distinto
// alcance, aquí ya viene el más amplio (TODO > SUS_VACANTES > PROPIO).
public record ContextoUsuario(
        Long usuarioId,
        Long personaId,
        Long organizacionId,
        String tipo,                    // CANDIDATO o EQUIPO
        List<Long> rolIds,
        Map<String, String> permisos    // codigo -> alcance
) {

    public boolean tiene(String codigoPermiso) {
        return permisos.containsKey(codigoPermiso);
    }

    public String alcance(String codigoPermiso) {
        return permisos.get(codigoPermiso);
    }
}
