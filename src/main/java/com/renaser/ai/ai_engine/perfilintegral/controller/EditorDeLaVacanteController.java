package com.renaser.ai.ai_engine.perfilintegral.controller;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CambioAplicado;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CopiarDeOtraVacante;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EditorDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EstadoDeLaRecomendacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.Mover;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PedirRecomendaciones;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.RecomendacionPedida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.VacanteCopiable;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.VersionDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.service.EditorDeLaVacante;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Los endpoints que los dos editores de una vacante tienen iguales: el de sus preguntas
 * propias y el de su prueba del puesto. Cada editor los publica bajo su propia ruta, la del
 * {@code @RequestMapping} de su controlador, y con los permisos de la vacante: ver con
 * {@code ver_vacantes}, escribir con {@code editar_vacante}. El panel no sabe sus permisos: si
 * puede editar viaja en el propio editor.
 *
 * <p>No es un controlador por sí mismo (no lleva {@code @RestController}): solo lo son sus
 * hijos.
 */
public class EditorDeLaVacanteController {

    private final EditorDeLaVacante editor;
    protected final Permisos permisos;

    protected EditorDeLaVacanteController(EditorDeLaVacante editor, Permisos permisos) {
        this.editor = editor;
        this.permisos = permisos;
    }

    @GetMapping
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    @Operation(summary = "El editor: el borrador y la publicada, con su balance, sus avisos y "
            + "cómo va la recalificación")
    public EditorDePreguntas ver(@PathVariable Long vacanteId) {
        return editor.ver(permisos.actual(), vacanteId);
    }

    // ---------- El borrador ----------

    @PostMapping("/borrador")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Abrir el borrador: vacío, o copia de la publicada mientras su vara se "
            + "pueda mover")
    public EditorDePreguntas abrirBorrador(@PathVariable Long vacanteId) {
        return editor.abrirBorrador(permisos.actual(), vacanteId);
    }

    @DeleteMapping("/borrador")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Descartar el borrador entero")
    public EditorDePreguntas descartarBorrador(@PathVariable Long vacanteId) {
        return editor.descartarBorrador(permisos.actual(), vacanteId);
    }

    @DeleteMapping("/criterios/{criterioId}")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Quitar un criterio: sus preguntas quedan sin criterio hasta moverlas")
    public EditorDePreguntas quitarCriterio(@PathVariable Long vacanteId, @PathVariable Long criterioId) {
        return editor.quitarCriterio(permisos.actual(), vacanteId, criterioId);
    }

    @PostMapping("/criterios/{criterioId}/movimiento")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Subir o bajar un criterio (ARRIBA o ABAJO)")
    public EditorDePreguntas moverCriterio(@PathVariable Long vacanteId, @PathVariable Long criterioId,
                                          @Valid @RequestBody Mover datos) {
        return editor.moverCriterio(permisos.actual(), vacanteId, criterioId, datos);
    }

    @DeleteMapping("/preguntas/{preguntaId}")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Quitar una pregunta del borrador")
    public EditorDePreguntas quitarPregunta(@PathVariable Long vacanteId, @PathVariable Long preguntaId) {
        return editor.quitarPregunta(permisos.actual(), vacanteId, preguntaId);
    }

    @PostMapping("/preguntas/{preguntaId}/movimiento")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Subir o bajar una pregunta dentro de su criterio (ARRIBA o ABAJO)")
    public EditorDePreguntas moverPregunta(@PathVariable Long vacanteId, @PathVariable Long preguntaId,
                                          @Valid @RequestBody Mover datos) {
        return editor.moverPregunta(permisos.actual(), vacanteId, preguntaId, datos);
    }

    @PostMapping("/publicacion")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Publicar el borrador. Si algo falta, 400 con la lista entera en "
            + "`faltas`; si la vara de la publicada ya no se mueve, 409")
    public EditorDePreguntas publicar(@PathVariable Long vacanteId) {
        return editor.publicar(permisos.actual(), vacanteId);
    }

    // ---------- Con la versión publicada ----------

    @PostMapping("/publicada/recalificacion")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Reintentar la recalificación de quienes se quedaron con la guía anterior")
    public CambioAplicado reintentarRecalificacion(@PathVariable Long vacanteId) {
        return editor.reintentarRecalificacion(permisos.actual(), vacanteId);
    }

    // ---------- Copiar de otra vacante ----------

    @GetMapping("/copiables")
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    @Operation(summary = "Las vacantes de la empresa con su versión publicada: activas, cerradas "
            + "y archivadas (no las eliminadas), las más recientes primero")
    public List<VacanteCopiable> copiables(@PathVariable Long vacanteId,
                                           @RequestParam(required = false) String buscar,
                                           @RequestParam(required = false) String nivel) {
        return editor.copiables(permisos.actual(), vacanteId, buscar, nivel);
    }

    @GetMapping("/copiables/{vacanteOrigenId}")
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    @Operation(summary = "La vista previa, en solo lectura, de la versión publicada de otra "
            + "vacante de la empresa. La de otra empresa es 404")
    public VersionDePreguntas vistaPrevia(@PathVariable Long vacanteId,
                                          @PathVariable Long vacanteOrigenId) {
        return editor.vistaPrevia(permisos.actual(), vacanteId, vacanteOrigenId);
    }

    @PostMapping("/copia")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Copiar la versión publicada de otra vacante de la empresa en un "
            + "borrador de esta (reemplaza el que hubiera). La de otra empresa es 404")
    public EditorDePreguntas copiar(@PathVariable Long vacanteId,
                                    @Valid @RequestBody CopiarDeOtraVacante datos) {
        return editor.copiar(permisos.actual(), vacanteId, datos);
    }

    // ---------- Recomendaciones por IA ----------

    @PostMapping("/recomendaciones")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Pedir a la IA lo que complete el borrador hasta 100. Contesta 202 y el "
            + "panel sondea; sin cupo o apagada, encolada=false")
    public ResponseEntity<RecomendacionPedida> pedirRecomendaciones(
            @PathVariable Long vacanteId, @Valid @RequestBody(required = false) PedirRecomendaciones datos) {
        RecomendacionPedida pedida = editor.pedirRecomendaciones(permisos.actual(), vacanteId, datos);
        return ResponseEntity.status(pedida.encolada() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(pedida);
    }

    @GetMapping("/recomendaciones")
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    @Operation(summary = "Cómo va la última recomendación y, si está lista, la propuesta")
    public EstadoDeLaRecomendacion comoVaLaRecomendacion(@PathVariable Long vacanteId) {
        return editor.comoVaLaRecomendacion(permisos.actual(), vacanteId);
    }
}
