package com.renaser.ai.ai_engine.perfil.controller;

import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.OpcionCatalogo;
import com.renaser.ai.ai_engine.perfil.service.CatalogosDelPerfil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Los catálogos que necesitan las pantallas del perfil. Autenticados como todo el portal
 * —las pantallas que los usan van tras iniciar sesión— y sin permiso concreto: son datos
 * de referencia, no de negocio.
 *
 * <p>El frontend NO escribe estos valores a mano: es lo que ya se desincronizó una vez en
 * este proyecto.
 */
@RestController
@RequestMapping("/api/v1/portal/catalogos")
@RequiredArgsConstructor
@Tag(name = "Portal · Catálogos", description = "Listas de referencia para los formularios")
public class CatalogoPerfilController {

    private final CatalogosDelPerfil catalogos;

    @GetMapping("/niveles-educativos")
    @Operation(summary = "Los niveles de estudio, en su orden")
    public List<OpcionCatalogo> nivelesEducativos() {
        return catalogos.nivelesEducativos();
    }

    @GetMapping("/niveles-idioma")
    @Operation(summary = "Los niveles de idioma del marco europeo, más NATIVO")
    public List<OpcionCatalogo> nivelesIdioma() {
        return catalogos.nivelesIdioma();
    }
}
