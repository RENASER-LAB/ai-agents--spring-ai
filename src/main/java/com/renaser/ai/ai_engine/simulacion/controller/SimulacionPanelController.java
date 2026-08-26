package com.renaser.ai.ai_engine.simulacion.controller;

import com.renaser.ai.ai_engine.perfilintegral.service.CalificacionPorCriterio;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.simulacion.dto.DtosSimulacion.*;
import com.renaser.ai.ai_engine.simulacion.service.ServicioCalificacionSimulacion;
import com.renaser.ai.ai_engine.simulacion.service.ServicioSimulacion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/panel")
@RequiredArgsConstructor
@Tag(name = "Panel · Simulación", description = "Sesiones de simulación, asistencia y calificación")
public class SimulacionPanelController {

    private final ServicioSimulacion servicio;
    private final ServicioCalificacionSimulacion calificacion;
    private final Permisos permisos;

    // ---------- Sesiones ----------

    @GetMapping("/sesiones-simulacion")
    @PreAuthorize("@permisos.tiene('crear_sesiones_simulacion')")
    public List<SesionPanel> listar() {
        return servicio.listarSesiones(permisos.actual());
    }

    @PostMapping("/sesiones-simulacion")
    @PreAuthorize("@permisos.tiene('crear_sesiones_simulacion')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear una sesión. Publicarla mueve a quien estaba esperando fecha")
    public Map<String, Long> crear(@Valid @RequestBody CrearSesion datos) {
        return Map.of("id", servicio.crearSesion(permisos.actual(), datos));
    }

    @GetMapping("/sesiones-simulacion/{id}")
    @PreAuthorize("@permisos.tiene('crear_sesiones_simulacion')")
    public SesionPanel detalle(@PathVariable Long id) {
        return servicio.verSesion(permisos.actual(), id);
    }

    @GetMapping("/sesiones-simulacion/{id}/inscritos")
    @PreAuthorize("@permisos.tiene('ver_inscritos_simulacion')")
    @Operation(summary = "Quién eligió esta fecha. Trae la inscripcion_id que piden marcas y "
            + "asistencia. Cuánto se ve depende del alcance del rol, que se edita en el panel")
    public List<InscritoEnSesion> inscritos(@PathVariable Long id) {
        return servicio.listarInscritos(permisos.actual(), id);
    }

    @PostMapping("/sesiones-simulacion/{id}/cupo")
    @PreAuthorize("@permisos.tiene('crear_sesiones_simulacion')")
    @Operation(summary = "Ampliar el cupo. Si estaba llena, vuelve a ofrecerse")
    public void ampliarCupo(@PathVariable Long id, @Valid @RequestBody AmpliarCupo datos) {
        servicio.ampliarCupo(permisos.actual(), id, datos);
    }

    @PostMapping("/sesiones-simulacion/{id}/cancelacion")
    @PreAuthorize("@permisos.tiene('crear_sesiones_simulacion')")
    @Operation(summary = "Cancelar. A los inscritos se les avisa y vuelven a elegir")
    public void cancelar(@PathVariable Long id, @Valid @RequestBody CancelarSesion datos) {
        servicio.cancelarSesion(permisos.actual(), id, datos);
    }

    @PostMapping("/sesiones-simulacion/{id}/responsables")
    @PreAuthorize("@permisos.tiene('crear_sesiones_simulacion')")
    @Operation(summary = "Quién conduce la sesión. Solo roles admitidos por el parámetro")
    public void asignarResponsable(@PathVariable Long id, @Valid @RequestBody AsignarResponsable datos) {
        servicio.asignarResponsable(permisos.actual(), id, datos);
    }

    // ---------- La matriz de información crítica ----------

    @GetMapping("/sesiones-simulacion/{id}/informacion-critica")
    @PreAuthorize("@permisos.tiene('definir_informacion_critica')")
    public List<InformacionCriticaResponse> informacionCritica(@PathVariable Long id) {
        return servicio.verInformacionCritica(permisos.actual(), id);
    }

    @PostMapping("/sesiones-simulacion/{id}/informacion-critica")
    @PreAuthorize("@permisos.tiene('definir_informacion_critica')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Qué debería preguntar un candidato fuerte, qué es opcional y qué descubrir")
    public Map<String, Long> agregarInformacionCritica(@PathVariable Long id,
                                                       @Valid @RequestBody CrearInformacionCritica datos) {
        return Map.of("id", servicio.agregarInformacionCritica(permisos.actual(), id, datos));
    }

    // ---------- Durante la sesión ----------

    @PostMapping("/inscripciones/{id}/marcas")
    @PreAuthorize("@permisos.tiene('marcar_eventos_simulacion')")
    @Operation(summary = "Marcar uno de los diez eventos observables. Solo lo que se hizo, nunca lo que se pensó")
    public void marcarEvento(@PathVariable Long id, @Valid @RequestBody MarcarEvento datos) {
        servicio.marcarEvento(permisos.actual(), id, datos);
    }

    @GetMapping("/inscripciones/{id}/marcas")
    @PreAuthorize("@permisos.tiene('marcar_eventos_simulacion')")
    public List<MarcaResponse> verMarcas(@PathVariable Long id) {
        return servicio.verMarcas(permisos.actual(), id);
    }

    @PostMapping("/inscripciones/{id}/asistencia")
    @PreAuthorize("@permisos.tiene('marcar_asistencia')")
    @Operation(summary = "Marcar si asistió. Si no, vuelve a la bandeja del equipo")
    public void marcarAsistencia(@PathVariable Long id, @Valid @RequestBody MarcarAsistencia datos) {
        servicio.marcarAsistencia(permisos.actual(), id, datos);
    }

    @PostMapping("/postulaciones/{id}/ausencia-simulacion")
    @PreAuthorize("@permisos.tiene('decidir_sobre_ausente')")
    @Operation(summary = "Qué hacer con quien faltó: otra fecha o cerrar. Nunca es automático")
    public void decidirSobreAusente(@PathVariable Long id, @Valid @RequestBody DecidirSobreAusente datos) {
        servicio.decidirSobreAusente(permisos.actual(), id, datos);
    }

    // ---------- Calificación ----------

    @GetMapping("/postulaciones/{id}/simulacion/notas")
    @PreAuthorize("@permisos.tiene('calificar_simulacion')")
    public List<CalificacionPorCriterio.Vista> verNotas(@PathVariable Long id) {
        return calificacion.verNotas(permisos.actual(), id);
    }

    @PostMapping("/postulaciones/{id}/simulacion/criterios/{criterioId}/nota")
    @PreAuthorize("@permisos.tiene('calificar_simulacion')")
    @Operation(summary = "Poner la nota de un criterio. La explicación es obligatoria")
    public void ponerNota(@PathVariable Long id, @PathVariable Long criterioId,
                          @Valid @RequestBody PonerNota datos) {
        calificacion.ponerNota(permisos.actual(), id, criterioId, datos.puntaje(), datos.explicacion());
    }

    @PostMapping("/postulaciones/{id}/simulacion/calificacion")
    @PreAuthorize("@permisos.tiene('calificar_simulacion')")
    @Operation(summary = "Ponderar las notas. Exige que estén los diez criterios")
    public Map<String, BigDecimal> calcular(@PathVariable Long id) {
        return Map.of("nota", calificacion.calcularNota(permisos.actual(), id));
    }

    // ---------- La conversación final ----------

    @GetMapping("/postulaciones/{id}/conversacion-final")
    @PreAuthorize("@permisos.tiene('hacer_conversacion_final')")
    public List<PreguntaResponse> verPreguntas(@PathVariable Long id) {
        return servicio.verPreguntas(permisos.actual(), id);
    }

    @PostMapping("/postulaciones/{id}/conversacion-final")
    @PreAuthorize("@permisos.tiene('hacer_conversacion_final')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una pregunta escrita a mano")
    public Map<String, Long> registrarPregunta(@PathVariable Long id,
                                               @Valid @RequestBody RegistrarPregunta datos) {
        return Map.of("id", servicio.registrarPregunta(permisos.actual(), id, datos));
    }

    @PostMapping("/postulaciones/{id}/conversacion-final/generar")
    @PreAuthorize("@permisos.tiene('hacer_conversacion_final')")
    @Operation(summary = "Pedirle al agente SIMULACION que prepare las preguntas. Tarda "
            + "decenas de segundos; las que ya se contestaron no se tocan")
    public PreguntasEncoladas generarPreguntas(@PathVariable Long id) {
        return servicio.generarPreguntas(permisos.actual(), id);
    }

    @PostMapping("/conversacion-final/{preguntaId}/respuesta")
    @PreAuthorize("@permisos.tiene('hacer_conversacion_final')")
    @Operation(summary = "Lo que contestó, si el riesgo quedó resuelto y la observación")
    public void responderPregunta(@PathVariable Long preguntaId,
                                  @Valid @RequestBody ResponderPregunta datos) {
        servicio.responderPregunta(permisos.actual(), preguntaId, datos);
    }

    /** El cuerpo de poner una nota. Mismo contrato que la calificación de la prueba. */
    public record PonerNota(
            @jakarta.validation.constraints.NotNull Double puntaje,
            @jakarta.validation.constraints.NotBlank String explicacion) {}
}
