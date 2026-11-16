package com.renaser.ai.ai_engine.postulacion.controller;

import com.renaser.ai.ai_engine.informe.dto.DtosInforme.ArchivoArmado;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.Informe;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.InformesDelPostulante;
import com.renaser.ai.ai_engine.informe.dto.DtosInforme.VisorDeLaVacante;
import com.renaser.ai.ai_engine.informe.service.ServicioInformes;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Los informes por etapa del candidato: el visor del panel y sus descargas.
 *
 * <p>Vive en {@code postulacion.controller} por lo mismo que el Excel del ranking: este paquete es
 * el que {@code ConfiguracionSwagger} y {@code ManejadorErrores} ya reconocen.
 *
 * <p>Todo es de solo lectura y se arma en el momento. <b>El permiso lo decide la etapa</b>, y por
 * eso no va en la anotación: el CV pide «ver_perfil_integral», la evaluación
 * «ver_respuestas_evaluacion» y la prueba «abrir_ficha_candidato». Sin él, 403; un candidato de
 * otra empresa, 404. Solo se exige aquí estar en el panel con alguno de los tres.
 */
@RestController
@RequestMapping("/api/v1/panel")
@RequiredArgsConstructor
@Tag(name = "Panel · Informes por etapa", description = "El informe del CV, de la evaluación y de la prueba")
public class InformesController {

    private static final String ALGUNO = "@permisos.tiene('ver_perfil_integral') "
            + "or @permisos.tiene('ver_respuestas_evaluacion') "
            + "or @permisos.tiene('abrir_ficha_candidato')";

    private final ServicioInformes informes;
    private final Permisos permisos;

    @GetMapping("/postulaciones/{id}/informes")
    @PreAuthorize(ALGUNO)
    @Operation(summary = "Qué informes de esta postulación puede ver quien pregunta y si ya se "
            + "pueden descargar. Uno no disponible trae su motivo: «Todavía no tiene "
            + "calificación», «La IA está calificando» o «La calificación falló». Sin permiso "
            + "para una etapa, esa etapa no sale")
    public InformesDelPostulante estados(@PathVariable Long id) {
        return informes.estados(permisos.actual(), id);
    }

    @GetMapping("/postulaciones/{id}/informes/{etapa}")
    @PreAuthorize(ALGUNO)
    @Operation(summary = "El informe de una etapa (cv, evaluacion o prueba) para el visor: los "
            + "mismos datos que el PDF. 409 si todavía no hay nada que enseñar")
    public Informe informe(@PathVariable Long id, @PathVariable String etapa) {
        return informes.informe(permisos.actual(), id, etapa);
    }

    @GetMapping("/postulaciones/{id}/informes/{etapa}/pdf")
    @PreAuthorize(ALGUNO)
    @Operation(summary = "El informe de una etapa en PDF A4, generado en el momento: «Informe CV - "
            + "Nombre - Vacante.pdf». No se guarda nada")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id, @PathVariable String etapa) {
        return descarga(informes.pdf(permisos.actual(), id, etapa));
    }

    @GetMapping("/postulaciones/{id}/informes/zip")
    @PreAuthorize(ALGUNO)
    @Operation(summary = "Los informes disponibles de un postulante en un .zip. Si falta alguno, "
            + "un aviso dentro dice cuál y por qué")
    public ResponseEntity<byte[]> zipDelPostulante(@PathVariable Long id) {
        return descarga(informes.zipDelPostulante(permisos.actual(), id));
    }

    @GetMapping("/vacantes/{id}/informes")
    @PreAuthorize("@permisos.tiene('ver_embudo') and (" + ALGUNO + ")")
    @Operation(summary = "La lista del visor de informes: los postulantes de la vacante en el orden "
            + "del ranking, con sus tres notas (las de las etapas que quien pregunta puede ver) y "
            + "su grupo")
    public VisorDeLaVacante visor(@PathVariable Long id) {
        return informes.visor(permisos.actual(), id);
    }

    @GetMapping("/vacantes/{id}/informes/zip")
    @PreAuthorize("@permisos.tiene('ver_embudo') and (" + ALGUNO + ")")
    @Operation(summary = "Los informes de toda la vacante en un .zip: una carpeta por postulante "
            + "que quien pregunta puede ver, con sus informes disponibles, y un aviso con los que "
            + "faltan y su motivo. Puede tardar en una vacante grande")
    public ResponseEntity<byte[]> zipDeLaVacante(@PathVariable Long id) {
        return descarga(informes.zipDeLaVacante(permisos.actual(), id));
    }

    private static ResponseEntity<byte[]> descarga(ArchivoArmado archivo) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(archivo.nombre(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(archivo.tipo()))
                .body(archivo.contenido());
    }
}
