package com.renaser.ai.ai_engine.decision.controller;

import com.renaser.ai.ai_engine.decision.dto.DtosDecision.*;
import com.renaser.ai.ai_engine.decision.service.ServicioDecision;
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

/**
 * La decisión final: el semáforo, las barreras críticas y la evidencia adicional.
 *
 * <p>{@code decidir} no lleva {@code @PreAuthorize} con un permiso fijo a propósito: la
 * primera vez exige {@code decidir_contratacion} y corregir una ya tomada exige
 * {@code cambiar_decision} — dos públicos distintos (RF-119 vs RF-121) —, y el servicio es
 * quien sabe en cuál de los dos casos está.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Panel · Decisión", description = "El semáforo final y las barreras críticas de una vacante")
public class DecisionPanelController {

    private final ServicioDecision servicio;
    private final Permisos permisos;

    @GetMapping("/api/v1/panel/vacantes/{vacanteId}/barreras-criticas")
    @PreAuthorize("@permisos.tiene('definir_barreras_criticas')")
    public List<BarreraResponse> listarBarreras(@PathVariable Long vacanteId) {
        return servicio.listarBarrerasDeVacante(permisos.actual(), vacanteId);
    }

    @PostMapping("/api/v1/panel/vacantes/{vacanteId}/barreras-criticas")
    @PreAuthorize("@permisos.tiene('definir_barreras_criticas')")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> definirBarrera(@PathVariable Long vacanteId, @Valid @RequestBody CrearBarrera datos) {
        return Map.of("id", servicio.definirBarrera(permisos.actual(), vacanteId, datos));
    }

    @PostMapping("/api/v1/panel/postulaciones/{postulacionId}/barreras-detectadas")
    @PreAuthorize("@permisos.tiene('decidir_contratacion')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reportar una barrera crítica en este candidato, ya confirmada")
    public Map<String, Long> registrarBarrera(@PathVariable Long postulacionId,
                                              @Valid @RequestBody RegistrarBarrera datos) {
        return Map.of("id", servicio.registrarBarreraDetectada(permisos.actual(), postulacionId, datos));
    }

    @GetMapping("/api/v1/panel/postulaciones/{postulacionId}/semaforo")
    @PreAuthorize("@permisos.tiene('ver_semaforo_decision')")
    public SemaforoResponse verSemaforo(@PathVariable Long postulacionId) {
        return servicio.verSemaforo(permisos.actual(), postulacionId);
    }

    @PostMapping("/api/v1/panel/postulaciones/{postulacionId}/decision")
    @Operation(summary = "Tomar o cambiar la decisión final. El motivo es siempre obligatorio")
    public void decidir(@PathVariable Long postulacionId, @Valid @RequestBody Decidir datos) {
        servicio.decidir(permisos.actual(), postulacionId, datos);
    }

    @PostMapping("/api/v1/panel/postulaciones/{postulacionId}/evidencia-adicional")
    @PreAuthorize("@permisos.tiene('pedir_evidencia_adicional')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Pedir evidencia adicional cuando el semáforo sale ámbar. Tope configurable")
    public void pedirEvidencia(@PathVariable Long postulacionId, @Valid @RequestBody PedirEvidencia datos) {
        servicio.pedirEvidenciaAdicional(permisos.actual(), postulacionId, datos);
    }
}
