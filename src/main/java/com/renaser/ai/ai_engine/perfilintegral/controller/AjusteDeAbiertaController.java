package com.renaser.ai.ai_engine.perfilintegral.controller;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.AjustarNota;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioPreguntasVacante;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El ajuste a mano de una abierta de las preguntas propias de una vacante (V66). Vive bajo la
 * postulación, no bajo la vacante, y por eso no está en {@link PreguntasVacanteController};
 * sale en Swagger junto a él. Pide {@code ajustar_nota}.
 */
@RestController
@RequestMapping("/api/v1/panel")
@RequiredArgsConstructor
@Tag(name = PreguntasVacanteController.TAG, description = PreguntasVacanteController.DESCRIPCION)
public class AjusteDeAbiertaController {

    private final ServicioPreguntasVacante servicio;
    private final Permisos permisos;

    @PutMapping("/postulaciones/{postulacionId}/evaluacion/respuestas/{respuestaId}/nota")
    @PreAuthorize("@permisos.tiene('ajustar_nota')")
    @Operation(summary = "Ajustar (o poner, si la IA no pudo) la nota de una abierta de las "
            + "preguntas propias, con motivo. Recalcula sin IA y sin mover de etapa")
    public void ajustarNota(@PathVariable Long postulacionId, @PathVariable Long respuestaId,
                            @Valid @RequestBody AjustarNota datos) {
        servicio.ajustarNota(permisos.actual(), postulacionId, respuestaId, datos);
    }
}
