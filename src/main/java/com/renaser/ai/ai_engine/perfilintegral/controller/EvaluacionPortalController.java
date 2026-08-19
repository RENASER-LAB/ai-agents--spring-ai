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
 * La evaluación, desde el portal del candidato.
 *
 * <p>Vive en su propio controlador y no dentro de {@code PortalController} porque es de otro
 * dominio: aquélla es la puerta de la postulación, ésta es el examen. Comparten la ruta
 * {@code /portal} porque comparten la puerta de seguridad, que es lo que importa.
 *
 * <p>No lleva {@code @PreAuthorize}: lo que decide aquí no es un permiso sino de quién es la
 * postulación, y eso lo comprueba el servicio. Una que no es tuya responde 404, igual que en
 * el resto del portal — decir 403 ya confirmaría que existe.
 */
@RestController
@RequestMapping("/api/v1/portal")
@RequiredArgsConstructor
@Tag(name = "Portal · Evaluación", description = "El examen que responde el candidato")
public class EvaluacionPortalController {

    private final ServicioEvaluacion servicio;
    private final Permisos permisos;

    @GetMapping("/evaluacion/{uuid}")
    @Operation(summary = "Mi evaluación, con las preguntas en mi orden y lo que llevo respondido")
    public EvaluacionCandidato ver(@PathVariable UUID uuid) {
        return servicio.ver(permisos.actual(), uuid);
    }

    @PostMapping("/evaluacion/{uuid}/inicio")
    @Operation(summary = "Empezar. La primera vez elige qué preguntas me tocan y en qué orden")
    public EvaluacionCandidato iniciar(@PathVariable UUID uuid) {
        return servicio.iniciar(permisos.actual(), uuid);
    }

    @PutMapping("/evaluacion/{uuid}/respuestas/{preguntaId}")
    @Operation(summary = "Guardar una respuesta. Se guarda al momento: si se corta, retoma aquí")
    public void responder(@PathVariable UUID uuid, @PathVariable Long preguntaId,
                          @Valid @RequestBody Responder datos) {
        servicio.responder(permisos.actual(), uuid, preguntaId, datos);
    }

    @PostMapping("/evaluacion/{uuid}/entrega")
    @Operation(summary = "Entregar. Ya no se puede cambiar nada, y pasa a calificarse")
    public EntregaResponse entregar(@PathVariable UUID uuid) {
        return servicio.entregar(permisos.actual(), uuid);
    }
}
