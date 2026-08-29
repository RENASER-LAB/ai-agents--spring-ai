package com.renaser.ai.ai_engine.perfilintegral.controller;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosEvaluacion.EntregaResponse;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosEvaluacion.EvaluacionCandidato;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosEvaluacion.Responder;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioEvaluacion;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * El cuestionario técnico de la vacante, desde el portal del candidato.
 *
 * <p>Los mismos cuatro verbos que la evaluación del banco y con la misma forma de respuesta:
 * para quien responde es el mismo gesto —abrir, empezar, escribir, entregar— y solo cambia
 * qué examen le tocó. Controlador aparte y no cuatro rutas más en el otro porque la ruta es
 * lo que distingue a los dos instrumentos: el portal sabe cuál pedir por el estado de su
 * proceso, no por un parámetro.
 *
 * <p>Sin {@code @PreAuthorize}, como su hermano: lo que decide no es un permiso sino de quién
 * es la postulación, y una que no es tuya responde 404.
 *
 * <p>⚠️ Aquí no se suben archivos, y no es un olvido: la etapa técnica del método CAZATALENTOS
 * se contesta escribiendo. La muestra de trabajo —lo único que se «produce»— está marcada
 * PRESENCIAL, jamás se envía y se resuelve en la entrevista con el dueño.
 */
@RestController
@RequestMapping("/api/v1/portal")
@RequiredArgsConstructor
@Tag(name = "Portal · Cuestionario técnico",
        description = "La prueba técnica de la vacante, cuando es la que se rinde")
public class CuestionarioTecnicoPortalController {

    private final ServicioEvaluacion servicio;
    private final Permisos permisos;

    @GetMapping("/cuestionario-tecnico/{uuid}")
    @Operation(summary = "Mi cuestionario técnico, con sus preguntas y lo que llevo respondido")
    public EvaluacionCandidato ver(@PathVariable UUID uuid) {
        return servicio.verTecnico(permisos.actual(), uuid);
    }

    @PostMapping("/cuestionario-tecnico/{uuid}/inicio")
    @Operation(summary = "Empezar. Aquí arranca el reloj, si la vacante fijó minutos")
    public EvaluacionCandidato iniciar(@PathVariable UUID uuid) {
        return servicio.iniciarTecnico(permisos.actual(), uuid);
    }

    @PutMapping("/cuestionario-tecnico/{uuid}/respuestas/{preguntaId}")
    @Operation(summary = "Guardar una respuesta. Se guarda al momento: si se corta, retoma aquí")
    public void responder(@PathVariable UUID uuid, @PathVariable Long preguntaId,
                          @Valid @RequestBody Responder datos) {
        servicio.responderTecnico(permisos.actual(), uuid, preguntaId, datos);
    }

    @PostMapping("/cuestionario-tecnico/{uuid}/entrega")
    @Operation(summary = "Entregar. Ya no se puede cambiar nada, y pasa a calificarse")
    public EntregaResponse entregar(@PathVariable UUID uuid) {
        return servicio.entregarTecnico(permisos.actual(), uuid);
    }
}
