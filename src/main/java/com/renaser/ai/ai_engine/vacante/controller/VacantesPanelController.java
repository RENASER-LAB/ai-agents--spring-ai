package com.renaser.ai.ai_engine.vacante.controller;

import com.renaser.ai.ai_engine.vacante.service.ServicioFichaVacante;
import com.renaser.ai.ai_engine.vacante.service.ServicioVacantesPanel;

import com.renaser.ai.ai_engine.vacante.dto.DtosFichaVacante.FichaResponse;
import com.renaser.ai.ai_engine.vacante.dto.DtosFichaVacante.GuardarFicha;
import com.renaser.ai.ai_engine.vacante.dto.DtosVacante.*;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/panel")
@RequiredArgsConstructor
@Tag(name = "Panel · Vacantes", description = "Crear, publicar y cerrar vacantes y sus requisitos")
public class VacantesPanelController {

    private final ServicioVacantesPanel servicio;
    private final ServicioFichaVacante fichaVacante;
    private final Permisos permisos;

    // ---------- Puestos ----------

    @GetMapping("/puestos")
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    @Operation(summary = "El catálogo de puestos activos")
    public List<PuestoResponse> puestos() {
        return servicio.listarPuestos(permisos.actual());
    }

    @PostMapping("/puestos")
    @PreAuthorize("@permisos.tiene('crear_vacante')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un puesto en el catálogo")
    public Map<String, Long> crearPuesto(@Valid @RequestBody GuardarPuesto datos) {
        return Map.of("id", servicio.crearPuesto(permisos.actual(), datos));
    }

    // ---------- Vacantes ----------

    /**
     * Las vacantes de la organización, en una de sus dos listas.
     *
     * <p>Sin parámetro devuelve la lista de todos los días: las que nadie ha archivado. Con
     * {@code archivadas=true}, solo las archivadas. <b>El corte lo hace el servidor</b>: si lo
     * hiciera la pantalla, una archivada reaparecería en cuanto alguien buscara, filtrara por
     * estado o pasara de página.
     */
    @GetMapping("/vacantes")
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    @Operation(summary = "Las vacantes de la organización. Por defecto, las no archivadas; "
            + "con «archivadas=true», solo las archivadas")
    public List<VacantePanel> listar(
            @RequestParam(name = "archivadas", defaultValue = "false") boolean archivadas) {
        return servicio.listar(permisos.actual(), archivadas);
    }

    /**
     * Cuántas archivadas hay: el número del botón «Archivadas (N)» de la cabecera.
     *
     * <p>Va con {@code ver_vacantes} y no con {@code cerrar_vacante}: consultar lo guardado es
     * leer, y quien no puede archivar puede necesitar mirar una convocatoria vieja.
     */
    @GetMapping("/vacantes/archivadas/conteo")
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    @Operation(summary = "Cuántas vacantes archivadas tiene la organización")
    public ConteoDeArchivadas conteoDeArchivadas() {
        return servicio.contarArchivadas(permisos.actual());
    }

    @GetMapping("/vacantes/{id}")
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    public VacantePanel detalle(@PathVariable Long id) {
        return servicio.detalle(permisos.actual(), id);
    }

    @PostMapping("/vacantes")
    @PreAuthorize("@permisos.tiene('crear_vacante')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear una vacante en borrador. Exige una solicitud aprobada (ABIERTA)")
    public Map<String, Long> crear(@Valid @RequestBody GuardarVacante datos) {
        return Map.of("id", servicio.crear(permisos.actual(), datos));
    }

    /**
     * Guardar el formulario de una vacante que no esté cerrada.
     *
     * <p>El cuerpo es el mismo del alta, remuneración incluida: el panel reutiliza esa
     * pantalla para corregir. Lo que llega se compara con lo guardado —sin contar los
     * espacios del principio y del final—, y si la vacante está publicada y cambió algo que
     * el candidato ve, sale <b>un único aviso</b> a la campana de cada postulación en
     * carrera. Sin correo.
     *
     * <p>Lo que devuelve es lo que el panel no puede deducir: si de verdad cambió algo y a
     * cuánta gente le llegó.
     */
    @PutMapping("/vacantes/{id}")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Editar una vacante que no esté cerrada. Si está publicada, avisa "
            + "por la campana del portal a cada postulación en carrera de lo que cambió")
    public VacanteActualizadaResponse editar(@PathVariable Long id,
                                             @Valid @RequestBody GuardarVacante datos) {
        return servicio.editar(permisos.actual(), id, datos);
    }

    @PostMapping("/vacantes/{id}/plantilla-evaluacion")
    @PreAuthorize("@permisos.tiene('elegir_plantilla_evaluacion')")
    @Operation(summary = "Elegir qué evaluación responderá quien postule. Hace falta antes de publicar")
    public void asignarPlantilla(@PathVariable Long id,
                                 @RequestBody AsignarPlantilla datos) {
        servicio.asignarPlantillaEvaluacion(permisos.actual(), id, datos.plantillaEvaluacionId());
    }

    @PostMapping("/vacantes/{id}/plantilla-prueba")
    @PreAuthorize("@permisos.tiene('elegir_plantilla_prueba')")
    @Operation(summary = "Elegir qué prueba del puesto rendirá quien llegue a esa etapa. Hace falta antes de publicar")
    public void asignarPlantillaPrueba(@PathVariable Long id,
                                       @RequestBody AsignarPlantillaPrueba datos) {
        servicio.asignarPlantillaPrueba(permisos.actual(), id, datos.versionPlantillaPruebaId());
    }

    @PostMapping("/vacantes/{id}/instrumento-tecnico")
    @PreAuthorize("@permisos.tiene('elegir_plantilla_prueba')")
    @Operation(summary = "Qué se rinde en la etapa técnica de esta vacante: la prueba del "
            + "puesto (PLANTILLA) o el cuestionario CAZATALENTOS (CUESTIONARIO_TECNICO), y en "
            + "cuántos minutos. Uno de los dos, y hace falta tenerlo listo antes de publicar")
    public void elegirInstrumentoTecnico(@PathVariable Long id,
                                         @Valid @RequestBody ElegirInstrumentoTecnico datos) {
        servicio.elegirInstrumentoTecnico(permisos.actual(), id, datos.instrumento(),
                datos.minutos());
    }

    @PostMapping("/vacantes/{id}/aplicacion-evaluacion")
    @PreAuthorize("@permisos.tiene('elegir_plantilla_evaluacion')")
    @Operation(summary = "Encender o apagar la evaluación del banco en esta vacante. Apagada, "
            + "quien postule va directo a la bandeja del equipo y su única evaluación es la prueba")
    public void definirAplicacionEvaluacion(@PathVariable Long id,
                                            @Valid @RequestBody AplicarEvaluacion datos) {
        servicio.definirAplicacionEvaluacion(permisos.actual(), id, datos.aplica());
    }

    @PostMapping("/vacantes/{id}/calificacion-automatica")
    @PreAuthorize("@permisos.tiene('elegir_plantilla_evaluacion')")
    @Operation(summary = "Encender o apagar el recorrido automático. Encendido, la "
            + "postulación se califica y avanza sola hasta que termina la prueba del puesto, "
            + "y solo entonces espera a una persona")
    public void activarCalificacionAutomatica(
            @PathVariable Long id,
            @Valid @RequestBody ActivarCalificacionAutomatica datos) {
        servicio.activarCalificacionAutomatica(permisos.actual(), id, datos.activa());
    }

    @PostMapping("/vacantes/{id}/version-pesos")
    @PreAuthorize("@permisos.tiene('publicar_version_pesos')")
    @Operation(summary = "Elegir qué versión de pesos rige la decisión de esta vacante. "
            + "No recalcula nada hacia atrás")
    public void asignarVersionPesos(@PathVariable Long id,
                                    @Valid @RequestBody AsignarVersionPesos datos) {
        servicio.asignarVersionPesos(permisos.actual(), id, datos.versionPesosId());
    }

    @PostMapping("/vacantes/{id}/cierre-prueba")
    @PreAuthorize("@permisos.tiene('elegir_plantilla_prueba')")
    @Operation(summary = "Fijar cuándo cierra la prueba de esta vacante, para todos. Mueve "
            + "también los intentos ya abiertos, salvo los de quien tenga fecha propia. "
            + "Con «cierraEn» vacío se quita y se vuelven a contar los días de la plantilla")
    public CierrePruebaResponse definirCierrePrueba(@PathVariable Long id,
                                                    @Valid @RequestBody DefinirCierrePrueba datos) {
        return servicio.definirCierrePrueba(permisos.actual(), id, datos);
    }

    // ---------- La ficha de vacante (método CAZATALENTOS) ----------

    @GetMapping("/vacantes/{id}/ficha")
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    @Operation(summary = "La ficha del método CAZATALENTOS: las 10 preguntas al dueño y "
            + "sus salidas. COMPLETA es lo que permite generar el cuestionario técnico")
    public FichaResponse ficha(@PathVariable Long id) {
        return fichaVacante.ver(permisos.actual(), id);
    }

    @PutMapping("/vacantes/{id}/ficha")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Guardar la ficha, a medias o completa. El tamaño (MICRO/MEDIA/"
            + "GRANDE) se deriva solo y la respuesta sugiere la versión de pesos que toca")
    public FichaResponse guardarFicha(@PathVariable Long id,
                                      @Valid @RequestBody GuardarFicha datos) {
        return fichaVacante.guardar(permisos.actual(), id, datos);
    }

    // ---------- Los textos de correo de esta vacante ----------

    @GetMapping("/vacantes/{id}/plantillas-correo")
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    @Operation(summary = "Los avisos que esta vacante manda con un texto propio. Vacío "
            + "significa que usa los de siempre")
    public List<PlantillaCorreoDeVacante> plantillasCorreo(@PathVariable Long id) {
        return servicio.plantillasCorreo(permisos.actual(), id);
    }

    @PostMapping("/vacantes/{id}/plantillas-correo")
    @PreAuthorize("@permisos.tiene('editar_textos_correo')")
    @Operation(summary = "Hacer que esta vacante mande otro texto en lugar del aviso que le "
            + "tocaba. Sin esto, una plantilla es una para toda la organización y cambiarla "
            + "se la cambia a todas las convocatorias")
    public void asignarPlantillaCorreo(@PathVariable Long id,
                                       @Valid @RequestBody AsignarPlantillaCorreo datos) {
        servicio.asignarPlantillaCorreo(permisos.actual(), id, datos);
    }

    @DeleteMapping("/vacantes/{id}/plantillas-correo/{avisoCodigo}")
    @PreAuthorize("@permisos.tiene('editar_textos_correo')")
    @Operation(summary = "Devolver ese aviso al texto por defecto")
    public void quitarPlantillaCorreo(@PathVariable Long id, @PathVariable String avisoCodigo) {
        servicio.quitarPlantillaCorreo(permisos.actual(), id, avisoCodigo);
    }

    /**
     * Cambiar solo lo que la vacante dice que paga, desde la tarjeta del detalle.
     *
     * <p>Sigue teniendo verbo propio porque sigue teniendo pantalla propia: la tarjeta del
     * sueldo, con su motivo y su botón, que existe para cambiarlo sin abrir el formulario
     * entero. Quien abre el formulario cambia el sueldo por el PUT de la vacante, y entonces
     * sale un solo aviso con todo lo que tocó.
     *
     * <p>Va con {@code editar_vacante}: quien puede cambiar el puesto puede cambiar el sueldo.
     * Partirlo en un permiso aparte le daría a alguien la mitad del formulario, y la empresa
     * que decide el presupuesto es la misma que redacta la convocatoria.
     */
    @PostMapping("/vacantes/{id}/remuneracion")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Definir o cambiar la remuneración (OCULTA, FIJA o RANGO). Si la "
            + "vacante está publicada, avisa por la campana del portal a cada candidato que "
            + "sigue en carrera. No se manda correo. El motivo es obligatorio")
    public RemuneracionActualizadaResponse actualizarRemuneracion(
            @PathVariable Long id, @Valid @RequestBody ActualizarRemuneracion datos) {
        return servicio.actualizarRemuneracion(permisos.actual(), id, datos);
    }

    @PostMapping("/vacantes/{id}/publicacion")
    @PreAuthorize("@permisos.tiene('publicar_vacante')")
    @Operation(summary = "Publicar: la vacante aparece en el portal")
    public void publicar(@PathVariable Long id) {
        servicio.publicar(permisos.actual(), id);
    }

    @PostMapping("/vacantes/{id}/cierre")
    @PreAuthorize("@permisos.tiene('cerrar_vacante')")
    @Operation(summary = "Cerrar: detiene postulaciones nuevas; las que están en marcha se deciden una a una")
    public void cerrar(@PathVariable Long id, @Valid @RequestBody CerrarVacante datos) {
        servicio.cerrar(permisos.actual(), id, datos.motivo());
    }

    /**
     * Archivar: la vacante cerrada deja la lista habitual y se consulta en «Archivadas».
     *
     * <p><b>Verbo propio y no un campo del formulario.</b> Archivar no es corregir la vacante:
     * es retirarla de la mesa de trabajo de todo el equipo, y pasa por otro permiso. Metido en
     * el PUT, cualquier guardado del formulario podría archivarla de rebote — y el cuerpo del
     * PUT lo manda la pantalla entero cada vez.
     *
     * <p>No cierra la vacante para poder archivarla: si no está {@code CERRADA}, se rechaza.
     * Tampoco si queda alguien en carrera, y eso se vuelve a comprobar aquí aunque el panel ya
     * lo haya mirado al abrir su modal.
     */
    @PostMapping("/vacantes/{id}/archivo")
    @PreAuthorize("@permisos.tiene('cerrar_vacante')")
    @Operation(summary = "Archivar una vacante CERRADA y sin nadie en carrera: sale de la "
            + "lista habitual del panel y su proceso se sigue consultando en Archivadas. No "
            + "cierra postulaciones ni avisa a nadie")
    public void archivar(@PathVariable Long id) {
        servicio.archivar(permisos.actual(), id);
    }

    /**
     * Desarchivar: vuelve a la lista habitual, {@code CERRADA} y sin reabrir nada.
     *
     * <p>{@code DELETE} sobre el mismo recurso que creó el {@code POST}: lo que se quita es la
     * marca de archivo, no la vacante. La eliminación de una vacante es de la entrega 03 y
     * tendrá su propia dirección.
     */
    @DeleteMapping("/vacantes/{id}/archivo")
    @PreAuthorize("@permisos.tiene('cerrar_vacante')")
    @Operation(summary = "Devolver una vacante archivada a la lista habitual. Sigue CERRADA y "
            + "sus postulaciones no se tocan")
    public void desarchivar(@PathVariable Long id) {
        servicio.desarchivar(permisos.actual(), id);
    }

    // ---------- Requisitos objetivos ----------

    @GetMapping("/vacantes/{id}/requisitos")
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    public List<RequisitoPanel> requisitos(@PathVariable Long id) {
        return servicio.requisitos(permisos.actual(), id);
    }

    @PostMapping("/vacantes/{id}/requisitos")
    @PreAuthorize("@permisos.tiene('definir_requisitos_objetivos')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Añadir un requisito objetivo: lo único que descarta sin intervención humana")
    public Map<String, Long> agregarRequisito(@PathVariable Long id, @Valid @RequestBody GuardarRequisito datos) {
        return Map.of("id", servicio.agregarRequisito(permisos.actual(), id, datos));
    }

    @DeleteMapping("/vacantes/{id}/requisitos/{requisitoId}")
    @PreAuthorize("@permisos.tiene('definir_requisitos_objetivos')")
    @Operation(summary = "Desactivar un requisito (no se borra: lo ya aplicado sigue explicado)")
    public void desactivarRequisito(@PathVariable Long id, @PathVariable Long requisitoId) {
        servicio.desactivarRequisito(permisos.actual(), id, requisitoId);
    }
}
