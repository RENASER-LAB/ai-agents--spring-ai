package com.renaser.ai.ai_engine.vacante.controller;

import com.renaser.ai.ai_engine.vacante.service.ServicioVacantesPanel;

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

    @GetMapping("/vacantes")
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    @Operation(summary = "Todas las vacantes de la organización")
    public List<VacantePanel> listar() {
        return servicio.listar(permisos.actual());
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

    @PutMapping("/vacantes/{id}")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Editar una vacante que no esté cerrada")
    public void editar(@PathVariable Long id, @Valid @RequestBody GuardarVacante datos) {
        servicio.editar(permisos.actual(), id, datos);
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

    @PostMapping("/vacantes/{id}/aplicacion-evaluacion")
    @PreAuthorize("@permisos.tiene('elegir_plantilla_evaluacion')")
    @Operation(summary = "Encender o apagar la evaluación del banco en esta vacante. Apagada, "
            + "quien postule va directo a la bandeja del equipo y su única evaluación es la prueba")
    public void definirAplicacionEvaluacion(@PathVariable Long id,
                                            @Valid @RequestBody AplicarEvaluacion datos) {
        servicio.definirAplicacionEvaluacion(permisos.actual(), id, datos.aplica());
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
