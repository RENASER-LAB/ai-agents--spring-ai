package com.renaser.ai.ai_engine.perfil.controller;

import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.OpcionCatalogo;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.OpcionUbigeo;
import com.renaser.ai.ai_engine.perfil.service.CatalogosDelPerfil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Los catálogos que necesitan las pantallas del perfil. Sin permiso concreto: son datos de
 * referencia, no de negocio.
 *
 * <p>Los niveles van autenticados como el resto del portal, porque sus pantallas viven tras
 * iniciar sesión. <b>El ubigeo no</b>: la ciudad se elige en el formulario de registro, y
 * ahí todavía no hay cuenta ni token que enseñar. Pedirlo autenticado dejaría el
 * desplegable vacío justo donde es obligatorio. La excepción está escrita en
 * {@code ConfiguracionSeguridad}, en la cadena del portal.
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

    @GetMapping("/ubigeo")
    @Operation(summary = "Las provincias del Perú y «fuera del Perú», agrupables por "
            + "departamento. Público: lo pide el formulario de registro")
    public List<OpcionUbigeo> ubigeo() {
        return catalogos.ubigeo();
    }
}
