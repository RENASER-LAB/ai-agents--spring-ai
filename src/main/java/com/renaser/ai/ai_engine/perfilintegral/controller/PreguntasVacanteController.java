package com.renaser.ai.ai_engine.perfilintegral.controller;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.AgregarDeLaPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CambiarPuntos;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CambioAplicado;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CorregirInstrucciones;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EditorDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarCriterio;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarDatosDelBorrador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarPregunta;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioPreguntasVacante;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las preguntas propias de una vacante (V66): el editor agrupado por criterios, su
 * publicación, lo que se cambia con candidatos dentro, la copia y las recomendaciones de la IA.
 * Lo que comparte con el editor de la prueba del puesto está en
 * {@link EditorDeLaVacanteController}; el ajuste a mano de una abierta, en
 * {@link AjusteDeAbiertaController}.
 *
 * <p>Viven bajo la vacante y con sus permisos: ver con {@code ver_vacantes}, escribir con
 * {@code editar_vacante}. El panel no sabe sus permisos: si puede editar viaja en el propio
 * editor.
 */
@RestController
@RequestMapping("/api/v1/panel/vacantes/{vacanteId}/preguntas-propias")
@Tag(name = PreguntasVacanteController.TAG, description = PreguntasVacanteController.DESCRIPCION)
public class PreguntasVacanteController extends EditorDeLaVacanteController {

    static final String TAG = "Panel · Preguntas propias de la vacante";
    static final String DESCRIPCION = "Criterios y preguntas que suman 100, escritos para cada vacante";

    private final ServicioPreguntasVacante servicio;

    public PreguntasVacanteController(ServicioPreguntasVacante servicio, Permisos permisos) {
        super(servicio, permisos);
        this.servicio = servicio;
    }

    // ---------- El borrador ----------

    @PutMapping("/borrador")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "La guía de calificación y los minutos estimados del borrador")
    public EditorDePreguntas guardarDatosDelBorrador(@PathVariable Long vacanteId,
                                                     @Valid @RequestBody GuardarDatosDelBorrador datos) {
        return servicio.guardarDatosDelBorrador(permisos.actual(), vacanteId, datos);
    }

    @PostMapping("/criterios")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Agregar un criterio al borrador")
    public EditorDePreguntas agregarCriterio(@PathVariable Long vacanteId,
                                            @Valid @RequestBody GuardarCriterio datos) {
        return servicio.agregarCriterio(permisos.actual(), vacanteId, datos);
    }

    @PutMapping("/criterios/{criterioId}")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Cambiar el nombre o lo que evalúa un criterio del borrador")
    public EditorDePreguntas editarCriterio(@PathVariable Long vacanteId, @PathVariable Long criterioId,
                                           @Valid @RequestBody GuardarCriterio datos) {
        return servicio.editarCriterio(permisos.actual(), vacanteId, criterioId, datos);
    }

    @PostMapping("/preguntas")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Agregar una pregunta. Sin criterios en el borrador, nace «General»")
    public EditorDePreguntas agregarPregunta(@PathVariable Long vacanteId,
                                            @Valid @RequestBody GuardarPregunta datos) {
        return servicio.agregarPregunta(permisos.actual(), vacanteId, datos);
    }

    @PutMapping("/preguntas/{preguntaId}")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Cambiar una pregunta del borrador, su criterio incluido")
    public EditorDePreguntas editarPregunta(@PathVariable Long vacanteId, @PathVariable Long preguntaId,
                                           @Valid @RequestBody GuardarPregunta datos) {
        return servicio.editarPregunta(permisos.actual(), vacanteId, preguntaId, datos);
    }

    // ---------- Con la versión publicada ----------

    @PutMapping("/publicada/instrucciones")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Corregir la guía, el «qué evalúa» y el «qué debe tener». Recalifica "
            + "a quien ya tiene nota; sin saldo de IA, 409 y no se guarda nada")
    public CambioAplicado corregirInstrucciones(@PathVariable Long vacanteId,
                                                @Valid @RequestBody CorregirInstrucciones datos) {
        return servicio.corregirInstrucciones(permisos.actual(), vacanteId, datos);
    }

    @PutMapping("/publicada/puntos")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Cambiar los puntos de preguntas, opciones y niveles. Recalcula a "
            + "todos al instante, sin IA; si el total no queda en 100, 400 con la lista")
    public CambioAplicado cambiarPuntos(@PathVariable Long vacanteId,
                                        @Valid @RequestBody CambiarPuntos datos) {
        return servicio.cambiarPuntos(permisos.actual(), vacanteId, datos);
    }

    // ---------- Recomendaciones por IA ----------

    @PostMapping("/recomendaciones/{propuestaId}/agregados")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Agregar al borrador lo elegido de la propuesta: criterios enteros o "
            + "preguntas sueltas. Nada se reemplaza")
    public EditorDePreguntas agregarDeLaPropuesta(@PathVariable Long vacanteId,
                                                  @PathVariable Long propuestaId,
                                                  @RequestBody AgregarDeLaPropuesta datos) {
        return servicio.agregarDeLaPropuesta(permisos.actual(), vacanteId, propuestaId, datos);
    }
}
