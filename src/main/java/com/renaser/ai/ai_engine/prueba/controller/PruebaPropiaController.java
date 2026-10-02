package com.renaser.ai.ai_engine.prueba.controller;

import com.renaser.ai.ai_engine.perfilintegral.controller.EditorDeLaVacanteController;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CambioAplicado;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EditorDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.Mover;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.AgregarDeLaPropuestaDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.CambiarPuntosDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.CorregirInstruccionesDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarCriterioDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarDatosDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarEntregable;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarPreguntaDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.NoCompleto;
import com.renaser.ai.ai_engine.prueba.service.ServicioCalificacionPruebaPropia;
import com.renaser.ai.ai_engine.prueba.service.ServicioPruebaPropia;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
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

import java.util.List;

/**
 * La prueba técnica de una vacante escrita en el editor (V67): el editor, su publicación, lo
 * que se cambia con candidatos dentro, la copia y las recomendaciones. Lo que comparte con el
 * editor de las preguntas propias está en {@link EditorDeLaVacanteController}; la prueba de
 * un candidato en la ficha, en {@link PruebaPropiaDelCandidatoController}.
 *
 * <p>Los permisos, como en la fase 1: ver con {@code ver_vacantes} y escribir con
 * {@code editar_vacante}. El panel no sabe sus permisos: si puede editar viaja en lo que se le
 * devuelve.
 */
@RestController
@RequestMapping("/api/v1/panel/vacantes/{vacanteId}/prueba-propia")
@Tag(name = PruebaPropiaController.TAG, description = PruebaPropiaController.DESCRIPCION)
public class PruebaPropiaController extends EditorDeLaVacanteController {

    static final String TAG = "Panel · Prueba técnica de la vacante";
    static final String DESCRIPCION = "El caso, el tiempo, los entregables y los criterios que "
            + "suman 100, escritos para cada vacante nueva";

    private final ServicioPruebaPropia servicio;
    private final ServicioCalificacionPruebaPropia calificacion;

    public PruebaPropiaController(ServicioPruebaPropia servicio,
                                  ServicioCalificacionPruebaPropia calificacion, Permisos permisos) {
        super(servicio, permisos);
        this.servicio = servicio;
        this.calificacion = calificacion;
    }

    // ---------- El borrador ----------

    @PutMapping("/borrador")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "La guía de la IA, el caso y el tiempo del borrador")
    public EditorDePreguntas guardarDatos(@PathVariable Long vacanteId,
                                          @Valid @RequestBody GuardarDatosDeLaPrueba datos) {
        return servicio.guardarDatos(permisos.actual(), vacanteId, datos);
    }

    @PostMapping(value = "/borrador/consigna", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Adjuntar el enunciado en PDF o Word. Otro formato es 400")
    public EditorDePreguntas subirConsigna(@PathVariable Long vacanteId,
                                           @RequestParam("archivo") MultipartFile archivo) {
        return servicio.subirConsigna(permisos.actual(), vacanteId, archivo);
    }

    @DeleteMapping("/borrador/consigna")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Quitar el enunciado adjunto del borrador")
    public EditorDePreguntas quitarConsigna(@PathVariable Long vacanteId) {
        return servicio.quitarConsigna(permisos.actual(), vacanteId);
    }

    @PostMapping("/criterios")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Agregar un criterio, con su parte calificada y lo que mira")
    public EditorDePreguntas agregarCriterio(@PathVariable Long vacanteId,
                                            @Valid @RequestBody GuardarCriterioDePrueba datos) {
        return servicio.agregarCriterio(permisos.actual(), vacanteId, datos);
    }

    @PutMapping("/criterios/{criterioId}")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Cambiar un criterio del borrador: nombre, qué evalúa, parte "
            + "calificada, quién la califica y qué entregables mira")
    public EditorDePreguntas editarCriterio(@PathVariable Long vacanteId, @PathVariable Long criterioId,
                                           @Valid @RequestBody GuardarCriterioDePrueba datos) {
        return servicio.editarCriterio(permisos.actual(), vacanteId, criterioId, datos);
    }

    @PostMapping("/preguntas")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Agregar una pregunta. Las abiertas no llevan puntos")
    public EditorDePreguntas agregarPregunta(@PathVariable Long vacanteId,
                                            @Valid @RequestBody GuardarPreguntaDePrueba datos) {
        return servicio.agregarPregunta(permisos.actual(), vacanteId, datos);
    }

    @PutMapping("/preguntas/{preguntaId}")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Cambiar una pregunta del borrador, su criterio incluido")
    public EditorDePreguntas editarPregunta(@PathVariable Long vacanteId, @PathVariable Long preguntaId,
                                           @Valid @RequestBody GuardarPreguntaDePrueba datos) {
        return servicio.editarPregunta(permisos.actual(), vacanteId, preguntaId, datos);
    }

    @PostMapping("/entregables")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Agregar un entregable: nombre, qué debe contener, formato, si es "
            + "obligatorio y qué debe tener una buena entrega")
    public EditorDePreguntas agregarEntregable(@PathVariable Long vacanteId,
                                              @Valid @RequestBody GuardarEntregable datos) {
        return servicio.agregarEntregable(permisos.actual(), vacanteId, datos);
    }

    @PutMapping("/entregables/{entregableId}")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Cambiar un entregable del borrador")
    public EditorDePreguntas editarEntregable(@PathVariable Long vacanteId, @PathVariable Long entregableId,
                                             @Valid @RequestBody GuardarEntregable datos) {
        return servicio.editarEntregable(permisos.actual(), vacanteId, entregableId, datos);
    }

    @DeleteMapping("/entregables/{entregableId}")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Quitar un entregable: desaparece también de lo que miran los criterios")
    public EditorDePreguntas quitarEntregable(@PathVariable Long vacanteId, @PathVariable Long entregableId) {
        return servicio.quitarEntregable(permisos.actual(), vacanteId, entregableId);
    }

    @PostMapping("/entregables/{entregableId}/movimiento")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Subir o bajar un entregable")
    public EditorDePreguntas moverEntregable(@PathVariable Long vacanteId, @PathVariable Long entregableId,
                                            @Valid @RequestBody Mover datos) {
        return servicio.moverEntregable(permisos.actual(), vacanteId, entregableId, datos);
    }

    // ---------- Con la versión publicada ----------

    @PutMapping("/publicada/instrucciones")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Corregir la guía, el «qué evalúa» y el «qué debe tener» de abiertas y "
            + "entregables. Recalifica los criterios de IA; sin saldo, 409 y no se guarda nada")
    public CambioAplicado corregirInstrucciones(@PathVariable Long vacanteId,
                                                @Valid @RequestBody CorregirInstruccionesDePrueba datos) {
        return servicio.corregirInstrucciones(permisos.actual(), vacanteId, datos);
    }

    @PutMapping("/publicada/puntos")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Cambiar los puntos de cerradas, opciones, niveles y partes calificadas. "
            + "Recalcula a todos al instante, sin IA y sin mover a nadie de etapa")
    public CambioAplicado cambiarPuntos(@PathVariable Long vacanteId,
                                        @Valid @RequestBody CambiarPuntosDePrueba datos) {
        return servicio.cambiarPuntos(permisos.actual(), vacanteId, datos);
    }

    // ---------- Recomendaciones por IA ----------

    @PostMapping("/recomendaciones/{propuestaId}/agregados")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Agregar al borrador lo elegido de la propuesta: el caso, entregables, "
            + "criterios enteros o preguntas sueltas. Nada se reemplaza")
    public EditorDePreguntas agregarDeLaPropuesta(@PathVariable Long vacanteId, @PathVariable Long propuestaId,
                                                  @RequestBody AgregarDeLaPropuestaDePrueba datos) {
        return servicio.agregarDeLaPropuesta(permisos.actual(), vacanteId, propuestaId, datos);
    }

    // ---------- Quienes no la completaron ----------

    @GetMapping("/no-completaron")
    @PreAuthorize("@permisos.tiene('ver_embudo')")
    @Operation(summary = "Quienes dejaron vencer el tiempo con algo sin responder: no salen en el "
            + "ranking y su proceso espera a que alguien lo cierre")
    public List<NoCompleto> noCompletaron(@PathVariable Long vacanteId) {
        return calificacion.noCompletaron(permisos.actual(), vacanteId);
    }
}
