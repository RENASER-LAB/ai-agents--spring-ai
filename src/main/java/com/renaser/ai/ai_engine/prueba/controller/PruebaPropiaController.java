package com.renaser.ai.ai_engine.prueba.controller;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CambioAplicado;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.CopiarDeOtraVacante;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EditorDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.EstadoDeLaRecomendacion;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.Mover;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.PedirRecomendaciones;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.RecomendacionPedida;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.VacanteCopiable;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.VersionDePreguntas;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.AgregarDeLaPropuestaDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.AjustarCriterio;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.CambiarPuntosDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.CorregirInstruccionesDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarCriterioDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarDatosDeLaPrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarEntregable;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.GuardarPreguntaDePrueba;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.NoCompleto;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.PruebaDelCandidato;
import com.renaser.ai.ai_engine.prueba.service.ServicioCalificacionPruebaPropia;
import com.renaser.ai.ai_engine.prueba.service.ServicioPruebaPropia;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

import java.util.List;

/**
 * La prueba técnica de una vacante escrita en el editor (V67): el editor, su publicación, lo
 * que se cambia con candidatos dentro, la copia y las recomendaciones; y en la ficha, la
 * prueba de un candidato criterio por criterio y el ajuste a mano de su parte calificada.
 *
 * <p>Los permisos, como en la fase 1: ver con {@code ver_vacantes}, escribir con
 * {@code editar_vacante}, ajustar con {@code ajustar_nota} y abrir la prueba de un candidato
 * con {@code abrir_ficha_candidato}. El panel no sabe sus permisos: si puede editar o ajustar
 * viaja en lo que se le devuelve.
 */
@RestController
@RequestMapping("/api/v1/panel")
@RequiredArgsConstructor
@Tag(name = "Panel · Prueba técnica de la vacante",
        description = "El caso, el tiempo, los entregables y los criterios que suman 100, "
                + "escritos para cada vacante nueva")
public class PruebaPropiaController {

    private static final String BASE = "/vacantes/{vacanteId}/prueba-propia";

    private final ServicioPruebaPropia servicio;
    private final ServicioCalificacionPruebaPropia calificacion;
    private final Permisos permisos;

    @GetMapping(BASE)
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    @Operation(summary = "El editor de la prueba: el borrador y la publicada, con su balance, "
            + "sus avisos y cómo va la recalificación")
    public EditorDePreguntas ver(@PathVariable Long vacanteId) {
        return servicio.ver(permisos.actual(), vacanteId);
    }

    // ---------- El borrador ----------

    @PostMapping(BASE + "/borrador")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Abrir el borrador: vacío, o copia de la publicada si nadie empezó a rendirla")
    public EditorDePreguntas abrirBorrador(@PathVariable Long vacanteId) {
        return servicio.abrirBorrador(permisos.actual(), vacanteId);
    }

    @PutMapping(BASE + "/borrador")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "La guía de la IA, el caso y el tiempo del borrador")
    public EditorDePreguntas guardarDatos(@PathVariable Long vacanteId,
                                          @Valid @RequestBody GuardarDatosDeLaPrueba datos) {
        return servicio.guardarDatos(permisos.actual(), vacanteId, datos);
    }

    @DeleteMapping(BASE + "/borrador")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Descartar el borrador entero")
    public EditorDePreguntas descartarBorrador(@PathVariable Long vacanteId) {
        return servicio.descartarBorrador(permisos.actual(), vacanteId);
    }

    @PostMapping(value = BASE + "/borrador/consigna", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Adjuntar el enunciado en PDF o Word. Otro formato es 400")
    public EditorDePreguntas subirConsigna(@PathVariable Long vacanteId,
                                           @RequestParam("archivo") MultipartFile archivo) {
        return servicio.subirConsigna(permisos.actual(), vacanteId, archivo);
    }

    @DeleteMapping(BASE + "/borrador/consigna")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Quitar el enunciado adjunto del borrador")
    public EditorDePreguntas quitarConsigna(@PathVariable Long vacanteId) {
        return servicio.quitarConsigna(permisos.actual(), vacanteId);
    }

    @PostMapping(BASE + "/criterios")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Agregar un criterio, con su parte calificada y lo que mira")
    public EditorDePreguntas agregarCriterio(@PathVariable Long vacanteId,
                                            @Valid @RequestBody GuardarCriterioDePrueba datos) {
        return servicio.agregarCriterio(permisos.actual(), vacanteId, datos);
    }

    @PutMapping(BASE + "/criterios/{criterioId}")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Cambiar un criterio del borrador: nombre, qué evalúa, parte "
            + "calificada, quién la califica y qué entregables mira")
    public EditorDePreguntas editarCriterio(@PathVariable Long vacanteId, @PathVariable Long criterioId,
                                           @Valid @RequestBody GuardarCriterioDePrueba datos) {
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
    @Operation(summary = "Agregar una pregunta. Las abiertas no llevan puntos")
    public EditorDePreguntas agregarPregunta(@PathVariable Long vacanteId,
                                            @Valid @RequestBody GuardarPreguntaDePrueba datos) {
        return servicio.agregarPregunta(permisos.actual(), vacanteId, datos);
    }

    @PutMapping(BASE + "/preguntas/{preguntaId}")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Cambiar una pregunta del borrador, su criterio incluido")
    public EditorDePreguntas editarPregunta(@PathVariable Long vacanteId, @PathVariable Long preguntaId,
                                           @Valid @RequestBody GuardarPreguntaDePrueba datos) {
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
    @Operation(summary = "Subir o bajar una pregunta dentro de su criterio")
    public EditorDePreguntas moverPregunta(@PathVariable Long vacanteId, @PathVariable Long preguntaId,
                                          @Valid @RequestBody Mover datos) {
        return servicio.moverPregunta(permisos.actual(), vacanteId, preguntaId, datos);
    }

    @PostMapping(BASE + "/entregables")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Agregar un entregable: nombre, qué debe contener, formato, si es "
            + "obligatorio y qué debe tener una buena entrega")
    public EditorDePreguntas agregarEntregable(@PathVariable Long vacanteId,
                                              @Valid @RequestBody GuardarEntregable datos) {
        return servicio.agregarEntregable(permisos.actual(), vacanteId, datos);
    }

    @PutMapping(BASE + "/entregables/{entregableId}")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Cambiar un entregable del borrador")
    public EditorDePreguntas editarEntregable(@PathVariable Long vacanteId, @PathVariable Long entregableId,
                                             @Valid @RequestBody GuardarEntregable datos) {
        return servicio.editarEntregable(permisos.actual(), vacanteId, entregableId, datos);
    }

    @DeleteMapping(BASE + "/entregables/{entregableId}")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Quitar un entregable: desaparece también de lo que miran los criterios")
    public EditorDePreguntas quitarEntregable(@PathVariable Long vacanteId, @PathVariable Long entregableId) {
        return servicio.quitarEntregable(permisos.actual(), vacanteId, entregableId);
    }

    @PostMapping(BASE + "/entregables/{entregableId}/movimiento")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Subir o bajar un entregable")
    public EditorDePreguntas moverEntregable(@PathVariable Long vacanteId, @PathVariable Long entregableId,
                                            @Valid @RequestBody Mover datos) {
        return servicio.moverEntregable(permisos.actual(), vacanteId, entregableId, datos);
    }

    @PostMapping(BASE + "/publicacion")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Publicar la prueba. Si algo falta, 400 con la lista entera en `faltas`; "
            + "si alguien ya empezó a rendir la publicada, 409")
    public EditorDePreguntas publicar(@PathVariable Long vacanteId) {
        return servicio.publicar(permisos.actual(), vacanteId);
    }

    // ---------- Con la versión publicada ----------

    @PutMapping(BASE + "/publicada/instrucciones")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Corregir la guía, el «qué evalúa» y el «qué debe tener» de abiertas y "
            + "entregables. Recalifica los criterios de IA; sin saldo, 409 y no se guarda nada")
    public CambioAplicado corregirInstrucciones(@PathVariable Long vacanteId,
                                                @Valid @RequestBody CorregirInstruccionesDePrueba datos) {
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
    @Operation(summary = "Cambiar los puntos de cerradas, opciones, niveles y partes calificadas. "
            + "Recalcula a todos al instante, sin IA y sin mover a nadie de etapa")
    public CambioAplicado cambiarPuntos(@PathVariable Long vacanteId,
                                        @Valid @RequestBody CambiarPuntosDePrueba datos) {
        return servicio.cambiarPuntos(permisos.actual(), vacanteId, datos);
    }

    // ---------- Copiar de otra vacante ----------

    @GetMapping(BASE + "/copiables")
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    @Operation(summary = "Las vacantes de la empresa con prueba propia publicada: activas, "
            + "cerradas y archivadas (no las eliminadas)")
    public List<VacanteCopiable> copiables(@PathVariable Long vacanteId,
                                           @RequestParam(required = false) String buscar,
                                           @RequestParam(required = false) String nivel) {
        return servicio.copiables(permisos.actual(), vacanteId, buscar, nivel);
    }

    @GetMapping(BASE + "/copiables/{vacanteOrigenId}")
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    @Operation(summary = "La vista previa, con caso, tiempo y entregables, de la prueba de otra "
            + "vacante de la empresa. La de otra empresa es 404")
    public VersionDePreguntas vistaPrevia(@PathVariable Long vacanteId, @PathVariable Long vacanteOrigenId) {
        return servicio.vistaPrevia(permisos.actual(), vacanteId, vacanteOrigenId);
    }

    @PostMapping(BASE + "/copia")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Copiar la prueba publicada de otra vacante en un borrador de esta. La "
            + "de otra empresa es 404")
    public EditorDePreguntas copiar(@PathVariable Long vacanteId,
                                    @Valid @RequestBody CopiarDeOtraVacante datos) {
        return servicio.copiar(permisos.actual(), vacanteId, datos);
    }

    // ---------- Recomendaciones por IA ----------

    @PostMapping(BASE + "/recomendaciones")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Pedir a la IA el caso, los entregables y los criterios que completen la "
            + "prueba hasta 100. Contesta 202 y el panel sondea")
    public ResponseEntity<RecomendacionPedida> pedirRecomendaciones(
            @PathVariable Long vacanteId, @Valid @RequestBody(required = false) PedirRecomendaciones datos) {
        RecomendacionPedida pedida = servicio.pedirRecomendaciones(permisos.actual(), vacanteId, datos);
        return ResponseEntity.status(pedida.encolada() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(pedida);
    }

    @GetMapping(BASE + "/recomendaciones")
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    @Operation(summary = "Cómo va la última recomendación de la prueba y, si está lista, la propuesta")
    public EstadoDeLaRecomendacion comoVaLaRecomendacion(@PathVariable Long vacanteId) {
        return servicio.comoVaLaRecomendacion(permisos.actual(), vacanteId);
    }

    @PostMapping(BASE + "/recomendaciones/{propuestaId}/agregados")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Agregar al borrador lo elegido de la propuesta: el caso, entregables, "
            + "criterios enteros o preguntas sueltas. Nada se reemplaza")
    public EditorDePreguntas agregarDeLaPropuesta(@PathVariable Long vacanteId, @PathVariable Long propuestaId,
                                                  @RequestBody AgregarDeLaPropuestaDePrueba datos) {
        return servicio.agregarDeLaPropuesta(permisos.actual(), vacanteId, propuestaId, datos);
    }

    // ---------- Quienes no la completaron ----------

    @GetMapping(BASE + "/no-completaron")
    @PreAuthorize("@permisos.tiene('ver_embudo')")
    @Operation(summary = "Quienes dejaron vencer el tiempo con algo sin responder: no salen en el "
            + "ranking y su proceso espera a que alguien lo cierre")
    public List<NoCompleto> noCompletaron(@PathVariable Long vacanteId) {
        return calificacion.noCompletaron(permisos.actual(), vacanteId);
    }

    // ---------- La ficha: la prueba de un candidato ----------

    @GetMapping("/postulaciones/{postulacionId}/prueba-propia")
    @PreAuthorize("@permisos.tiene('abrir_ficha_candidato')")
    @Operation(summary = "La prueba de un candidato, criterio por criterio: de dónde sale cada "
            + "nota, sus cerradas, sus abiertas y sus entregables, y la nota de la etapa o qué "
            + "falta para tenerla. El contenido de los entregables pide «descargar_entregables»")
    public PruebaDelCandidato prueba(@PathVariable Long postulacionId) {
        return calificacion.prueba(permisos.actual(), postulacionId);
    }

    @PutMapping("/postulaciones/{postulacionId}/prueba-propia/criterios/{criterioId}/nota")
    @PreAuthorize("@permisos.tiene('ajustar_nota')")
    @Operation(summary = "Ajustar (o poner) la parte calificada de un criterio, de 0 a sus puntos "
            + "y con motivo. La parte automática no se ajusta. Recalcula sin IA")
    public PruebaDelCandidato ajustar(@PathVariable Long postulacionId, @PathVariable Long criterioId,
                                      @Valid @RequestBody AjustarCriterio datos) {
        return calificacion.ajustar(permisos.actual(), postulacionId, criterioId, datos);
    }
}
