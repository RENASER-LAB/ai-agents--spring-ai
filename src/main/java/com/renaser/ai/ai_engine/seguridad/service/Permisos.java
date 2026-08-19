package com.renaser.ai.ai_engine.seguridad.service;

import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

// El bean que usan los @PreAuthorize: @PreAuthorize("@permisos.tiene('cerrar_vacante')").
// Decide si se puede llamar; el alcance (qué filas) lo aplican los servicios con
// alcanceDe(...), porque un WHERE no cabe en una anotación.
@Component("permisos")
public class Permisos {

    public boolean tiene(String codigoPermiso) {
        ContextoUsuario contexto = actualONulo();
        return contexto != null && contexto.tiene(codigoPermiso);
    }

    public ContextoUsuario actual() {
        ContextoUsuario contexto = actualONulo();
        if (contexto == null) {
            throw new AccessDeniedException("No hay una identidad en esta petición");
        }
        return contexto;
    }

    public FiltroAlcance alcanceDe(String codigoPermiso) {
        ContextoUsuario contexto = actual();
        String alcance = contexto.alcance(codigoPermiso);
        if (alcance == null) {
            throw new AccessDeniedException("No tienes el permiso «" + codigoPermiso + "»");
        }
        return FiltroAlcance.desde(alcance, contexto.usuarioId());
    }

    private ContextoUsuario actualONulo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof ContextoUsuario contexto) {
            return contexto;
        }
        return null;
    }
}
