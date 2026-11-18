package com.renaser.ai.ai_engine.portal.controller;

import com.renaser.ai.ai_engine.organizacion.service.ServicioBrochure.ArchivoDelBrochure;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.PerfilDeEmpresa;
import com.renaser.ai.ai_engine.portal.service.ServicioEmpresasPortal;
import com.renaser.ai.ai_engine.portal.service.ServicioEmpresasPortal.Imagen;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * El perfil público de cada empresa (V85), sin token como el tablón: lo abre cualquiera desde
 * una vacante o por su enlace {@code /empresas/<código>}.
 *
 * <p>El logo y la portada son los primeros archivos de EX que se ven sin cuenta. Se sirven solo
 * por aquí, leídos del almacén y con caché pública de un día: nunca un enlace al almacén privado.
 */
@RestController
@RequestMapping("/api/v1/portal/empresas")
@RequiredArgsConstructor
@Tag(name = "Portal · Empresas", description = "El perfil público de cada empresa, sin cuenta")
public class EmpresasPortalController {

    private final ServicioEmpresasPortal servicio;

    @GetMapping("/{codigo}")
    @Operation(summary = "El perfil público de una empresa activa, con sus vacantes publicadas")
    public PerfilDeEmpresa perfil(@PathVariable String codigo) {
        return servicio.perfil(codigo);
    }

    @GetMapping("/{codigo}/imagenes/{cual}")
    @Operation(summary = "El logo o la portada de una empresa activa (logo | portada)")
    public ResponseEntity<byte[]> imagen(@PathVariable String codigo, @PathVariable String cual) {
        Imagen imagen = servicio.imagen(codigo, cual);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(imagen.tipo()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(imagen.bytes());
    }

    /**
     * El brochure en PDF (V86), sin cuenta como el perfil: el mismo que se descarga desde el
     * panel. Sin caché pública, para que cada descarga refleje el perfil del momento.
     */
    @GetMapping("/{codigo}/brochure")
    @Operation(summary = "El brochure en PDF de una empresa activa; 404 si no existe, está "
            + "suspendida o le falta «Quiénes somos»")
    public ResponseEntity<byte[]> brochure(@PathVariable String codigo) {
        ArchivoDelBrochure archivo = servicio.brochure(codigo);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(archivo.nombre(), StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(archivo.contenido());
    }
}
