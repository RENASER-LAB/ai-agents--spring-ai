package com.renaser.ai.ai_engine.perfilintegral.controller;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.AgregarDeLaPropuesta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.AjustarNota;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CambiarPuntos;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CambioAplicado;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CopiarDeOtraVacante;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CorregirInstrucciones;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EditorDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EstadoDeLaRecomendacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarCriterio;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarDatosDelBorrador;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.GuardarPregunta;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.Mover;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PedirRecomendaciones;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.RecomendacionPedida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.VacanteCopiable;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.VersionDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioPreguntasVacante;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

import java.util.List;

/**
 * Las preguntas propias de una vacante (V66): el editor agrupado por criterios, su
 * publicación, lo que se cambia con candidatos dentro, la copia, las recomendaciones de la IA
 * y el ajuste a mano de una abierta.
 *
 * <p>Viven bajo la vacante y con sus permisos: ver con {@code ver_vacantes}, escribir con
 * {@code editar_vacante}. Ajustar una nota pide {@code ajustar_nota}. El panel no sabe sus
 * permisos: si puede editar viaja en el propio editor.
 */
@RestController
@RequestMapping("/api/v1/panel")
@RequiredArgsConstructor
@Tag(name = "Panel · Preguntas propias de la vacante",
        description = "Criterios y preguntas que suman 100, escritos para cada vacante")
public class PreguntasVacanteController {

    private static final String BASE = "/vacantes/{vacanteId}/preguntas-propias";

    private final ServicioPreguntasVacante servicio;
    private final Permisos permisos;

    @GetMapping(BASE)
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    @Operation(summary = "El editor: el borrador y la publicada, con su balance, sus avisos y "
            + "cómo va la recalificación")
    public EditorDePreguntas ver(@PathVariable Long vacanteId) {
        return servicio.ver(permisos.actual(), vacanteId);
    }

    // ---------- El borrador ----------

    @PostMapping(BASE + "/borrador")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Abrir el borrador: vacío, o copia de la publicada si nadie ha postulado")
    public EditorDePreguntas abrirBorrador(@PathVariable Long vacanteId) {
        return servicio.abrirBorrador(permisos.actual(), vacanteId);
    }

    @PutMapping(BASE + "/borrador")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "La guía de calificación y los minutos estimados del borrador")
    public EditorDePreguntas guardarDatosDelBorrador(@PathVariable Long vacanteId,
                                                     @Valid @RequestBody GuardarDatosDelBorrador datos) {
        return servicio.guardarDatosDelBorrador(permisos.actual(), vacanteId, datos);
    }

    @DeleteMapping(BASE + "/borrador")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Descartar el borrador entero")
    public EditorDePreguntas descartarBorrador(@PathVariable Long vacanteId) {
        return servicio.descartarBorrador(permisos.actual(), vacanteId);
    }

    @PostMapping(BASE + "/criterios")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Agregar un criterio al borrador")
    public EditorDePreguntas agregarCriterio(@PathVariable Long vacanteId,
                                            @Valid @RequestBody GuardarCriterio datos) {
        return servicio.agregarCriterio(permisos.actual(), vacanteId, datos);
    }

    @PutMapping(BASE + "/criterios/{criterioId}")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Cambiar el nombre o lo que evalúa un criterio del borrador")
    public EditorDePreguntas editarCriterio(@PathVariable Long vacanteId, @PathVariable Long criterioId,
                                           @Valid @RequestBody GuardarCriterio datos) {
        return servicio.editarCriterio(permisos.actual(), vacanteId, criterioId, datos);
    }

    @DeleteMapping(BASE + "/criterios/{criterioId}")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Quitar un criterio: sus preguntas quedan sin criterio hasta moverlas")
    public EditorDePreguntas quitarCriterio(@PathVariable Long vacanteId, @PathVariable Long criterioId) {
        return servicio.quitarCriterio(permisos.actual(), vacanteId, criterioId);
    }

    @PostMapping(BASE + "/criterios/{criterioId}/movimiento")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Subir o bajar un criterio (ARRIBA o ABAJO)")
    public EditorDePreguntas moverCriterio(@PathVariable Long vacanteId, @PathVariable Long criterioId,
                                          @Valid @RequestBody Mover datos) {
        return servicio.moverCriterio(permisos.actual(), vacanteId, criterioId, datos);
    }

    @PostMapping(BASE + "/preguntas")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Agregar una pregunta. Sin criterios en el borrador, nace «General»")
    public EditorDePreguntas agregarPregunta(@PathVariable Long vacanteId,
                                            @Valid @RequestBody GuardarPregunta datos) {
        return servicio.agregarPregunta(permisos.actual(), vacanteId, datos);
    }

    @PutMapping(BASE + "/preguntas/{preguntaId}")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Cambiar una pregunta del borrador, su criterio incluido")
    public EditorDePreguntas editarPregunta(@PathVariable Long vacanteId, @PathVariable Long preguntaId,
                                           @Valid @RequestBody GuardarPregunta datos) {
        return servicio.editarPregunta(permisos.actual(), vacanteId, preguntaId, datos);
    }

    @DeleteMapping(BASE + "/preguntas/{preguntaId}")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Quitar una pregunta del borrador")
    public EditorDePreguntas quitarPregunta(@PathVariable Long vacanteId, @PathVariable Long preguntaId) {
        return servicio.quitarPregunta(permisos.actual(), vacanteId, preguntaId);
    }

    @PostMapping(BASE + "/preguntas/{preguntaId}/movimiento")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Subir o bajar una pregunta dentro de su criterio (ARRIBA o ABAJO)")
    public EditorDePreguntas moverPregunta(@PathVariable Long vacanteId, @PathVariable Long preguntaId,
                                          @Valid @RequestBody Mover datos) {
        return servicio.moverPregunta(permisos.actual(), vacanteId, preguntaId, datos);
    }

    @PostMapping(BASE + "/publicacion")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Publicar el borrador. Si algo falta, 400 con la lista entera en "
            + "`faltas`; con postulantes y otra versión publicada, 409")
    public EditorDePreguntas publicar(@PathVariable Long vacanteId) {
        return servicio.publicar(permisos.actual(), vacanteId);
    }

    // ---------- Con la versión publicada ----------

    @PutMapping(BASE + "/publicada/instrucciones")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Corregir la guía, el «qué evalúa» y el «qué debe tener». Recalifica "
            + "a quien ya tiene nota; sin saldo de IA, 409 y no se guarda nada")
    public CambioAplicado corregirInstrucciones(@PathVariable Long vacanteId,
                                                @Valid @RequestBody CorregirInstrucciones datos) {
        return servicio.corregirInstrucciones(permisos.actual(), vacanteId, datos);
    }

    @PostMapping(BASE + "/publicada/recalificacion")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Reintentar la recalificación de quienes se quedaron con la guía anterior")
    public CambioAplicado reintentarRecalificacion(@PathVariable Long vacanteId) {
        return servicio.reintentarRecalificacion(permisos.actual(), vacanteId);
    }

    @PutMapping(BASE + "/publicada/puntos")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Cambiar los puntos de preguntas, opciones y niveles. Recalcula a "
            + "todos al instante, sin IA; si el total no queda en 100, 400 con la lista")
    public CambioAplicado cambiarPuntos(@PathVariable Long vacanteId,
                                        @Valid @RequestBody CambiarPuntos datos) {
        return servicio.cambiarPuntos(permisos.actual(), vacanteId, datos);
    }

    // ---------- Copiar de otra vacante ----------

    @GetMapping(BASE + "/copiables")
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    @Operation(summary = "Las vacantes de la empresa con preguntas propias publicadas: "
            + "activas, cerradas y archivadas (no las eliminadas), las más recientes primero")
    public List<VacanteCopiable> copiables(@PathVariable Long vacanteId,
                                           @RequestParam(required = false) String buscar,
                                           @RequestParam(required = false) String nivel) {
        return servicio.copiables(permisos.actual(), vacanteId, buscar, nivel);
    }

    @GetMapping(BASE + "/copiables/{vacanteOrigenId}")
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    @Operation(summary = "La vista previa, en solo lectura, de las preguntas de otra vacante "
            + "de la empresa. La de otra empresa es 404")
    public VersionDePreguntas vistaPrevia(@PathVariable Long vacanteId,
                                          @PathVariable Long vacanteOrigenId) {
        return servicio.vistaPrevia(permisos.actual(), vacanteId, vacanteOrigenId);
    }

    @PostMapping(BASE + "/copia")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Copiar la versión publicada de otra vacante de la empresa en un "
            + "borrador de esta (reemplaza el que hubiera). La de otra empresa es 404")
    public EditorDePreguntas copiar(@PathVariable Long vacanteId,
                                    @Valid @RequestBody CopiarDeOtraVacante datos) {
        return servicio.copiar(permisos.actual(), vacanteId, datos);
    }

    // ---------- Recomendaciones por IA ----------

    @PostMapping(BASE + "/recomendaciones")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Pedir a la IA criterios y preguntas que completen el borrador hasta "
            + "100. Contesta 202 y el panel sondea; sin cupo o apagada, encolada=false")
    public ResponseEntity<RecomendacionPedida> pedirRecomendaciones(
            @PathVariable Long vacanteId, @Valid @RequestBody(required = false) PedirRecomendaciones datos) {
        RecomendacionPedida pedida = servicio.pedirRecomendaciones(permisos.actual(), vacanteId, datos);
        return ResponseEntity.status(pedida.encolada() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .body(pedida);
    }

    @GetMapping(BASE + "/recomendaciones")
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    @Operation(summary = "Cómo va la última recomendación y, si está lista, la propuesta")
    public EstadoDeLaRecomendacion comoVaLaRecomendacion(@PathVariable Long vacanteId) {
        return servicio.comoVaLaRecomendacion(permisos.actual(), vacanteId);
    }

    @PostMapping(BASE + "/recomendaciones/{propuestaId}/agregados")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Agregar al borrador lo elegido de la propuesta: criterios enteros o "
            + "preguntas sueltas. Nada se reemplaza")
    public EditorDePreguntas agregarDeLaPropuesta(@PathVariable Long vacanteId,
                                                  @PathVariable Long propuestaId,
                                                  @RequestBody AgregarDeLaPropuesta datos) {
        return servicio.agregarDeLaPropuesta(permisos.actual(), vacanteId, propuestaId, datos);
    }

    // ---------- Ajustar a mano una abierta ----------

    @PutMapping("/postulaciones/{postulacionId}/evaluacion/respuestas/{respuestaId}/nota")
    @PreAuthorize("@permisos.tiene('ajustar_nota')")
    @Operation(summary = "Ajustar (o poner, si la IA no pudo) la nota de una abierta de las "
            + "preguntas propias, con motivo. Recalcula sin IA y sin mover de etapa")
    public void ajustarNota(@PathVariable Long postulacionId, @PathVariable Long respuestaId,
                            @Valid @RequestBody AjustarNota datos) {
        servicio.ajustarNota(permisos.actual(), postulacionId, respuestaId, datos);
    }
}
