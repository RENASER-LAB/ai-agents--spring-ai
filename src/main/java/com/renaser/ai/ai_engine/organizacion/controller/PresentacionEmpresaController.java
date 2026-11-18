package com.renaser.ai.ai_engine.organizacion.controller;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.organizacion.dto.DtosPresentacion.GuardarBrochure;
import com.renaser.ai.ai_engine.organizacion.dto.DtosPresentacion.GuardarPresentacion;
import com.renaser.ai.ai_engine.organizacion.dto.DtosPresentacion.PresentacionPanel;
import com.renaser.ai.ai_engine.organizacion.dto.DtosPresentacion.VideoDeLaEmpresa;
import com.renaser.ai.ai_engine.organizacion.service.ImagenesDeLaEmpresa.Cual;
import com.renaser.ai.ai_engine.organizacion.service.ServicioBrochure;
import com.renaser.ai.ai_engine.organizacion.service.ServicioBrochure.ArchivoDelBrochure;
import com.renaser.ai.ai_engine.organizacion.service.ServicioPresentacion;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * «Perfil de la empresa» (V85): lo que la empresa de quien ha entrado cuenta de sí misma, y que
 * el candidato ve en el portal junto a cada vacante y en el perfil público.
 *
 * <p>La dirección no lleva el id de la empresa: siempre es la de quien ha entrado. Sin
 * {@code editar_presentacion_empresa}, 403 y no se guarda nada.
 */
@RestController
@RequestMapping("/api/v1/panel/organizacion/presentacion")
@RequiredArgsConstructor
@Tag(name = "Panel · Perfil de la empresa",
        description = "Propósito, video, redes, web, logo, portada y brochure de la propia empresa")
public class PresentacionEmpresaController {

    private final ServicioPresentacion servicio;
    private final ServicioBrochure brochures;
    private final Permisos permisos;

    @GetMapping
    @PreAuthorize("@permisos.tiene('editar_presentacion_empresa')")
    @Operation(summary = "La presentación de mi empresa, con sus sedes de solo lectura")
    public PresentacionPanel ver() {
        return servicio.ver(permisos.actual());
    }

    @PutMapping
    @PreAuthorize("@permisos.tiene('editar_presentacion_empresa')")
    @Operation(summary = "Guardar la presentación entera: si un campo no vale, 400 con todos "
            + "los que fallan en «errores» y no se guarda nada")
    public PresentacionPanel guardar(@RequestBody GuardarPresentacion datos) {
        return servicio.guardar(permisos.actual(), datos);
    }

    @PostMapping(value = "/imagenes/{cual}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permisos.tiene('editar_presentacion_empresa')")
    @Operation(summary = "Subir el logo o la portada (JPG o PNG, hasta 2 MB): se vuelve a "
            + "codificar sin metadatos y sustituye al anterior")
    public PresentacionPanel subirImagen(@PathVariable String cual,
                                         @RequestParam("archivo") MultipartFile archivo) {
        return servicio.guardarImagen(permisos.actual(), cual(cual), archivo);
    }

    @DeleteMapping("/imagenes/{cual}")
    @PreAuthorize("@permisos.tiene('editar_presentacion_empresa')")
    @Operation(summary = "Quitar el logo o la portada")
    public PresentacionPanel quitarImagen(@PathVariable String cual) {
        return servicio.quitarImagen(permisos.actual(), cual(cual));
    }

    /**
     * El brochure en PDF (V86) con lo guardado, en la plantilla y el color elegidos, para abrirlo
     * en otra pestaña. No se guarda nada; sin «Quiénes somos» guardado, 409.
     */
    @GetMapping("/brochure")
    @PreAuthorize("@permisos.tiene('editar_presentacion_empresa')")
    @Operation(summary = "El brochure de mi empresa en PDF A4, con lo guardado: «Brochure - "
            + "Empresa.pdf». 409 si falta «Quiénes somos»")
    public ResponseEntity<byte[]> brochure() {
        ArchivoDelBrochure archivo = brochures.delPanel(permisos.actual());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(archivo.nombre(), StandardCharsets.UTF_8).build().toString())
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(archivo.contenido());
    }

    /**
     * La pestaña «Brochure» guarda la plantilla y el color al elegirlos, sin «Guardar»: solo esos
     * dos, sin tocar lo demás de la presentación. Fuera del catálogo, 400 y no se guarda nada.
     */
    @PutMapping("/brochure")
    @PreAuthorize("@permisos.tiene('editar_presentacion_empresa')")
    @Operation(summary = "Guardar solo la plantilla y el color del brochure: los dos del "
            + "catálogo, o 400 con los que fallan en «errores» y no se guarda nada")
    public PresentacionPanel guardarBrochure(@RequestBody GuardarBrochure eleccion) {
        return servicio.guardarBrochure(permisos.actual(), eleccion);
    }

    /**
     * Para el formulario de la vacante, que lo abre quien puede ver vacantes aunque no edite la
     * presentación: solo dice si hay video, nada más.
     */
    @GetMapping("/video")
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    @Operation(summary = "Si mi empresa tiene video de presentación")
    public VideoDeLaEmpresa video() {
        return new VideoDeLaEmpresa(servicio.tieneVideo(permisos.actual()));
    }

    /** «logo» o «portada»; otra cosa no existe. */
    private static Cual cual(String crudo) {
        return Cual.deTexto(crudo)
                .orElseThrow(() -> new ResourceNotFoundException("Imagen", "nombre", crudo));
    }
}
