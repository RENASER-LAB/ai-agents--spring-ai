package com.renaser.ai.ai_engine.prueba.controller;

import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.AjustarCriterio;
import com.renaser.ai.ai_engine.prueba.dto.DtosPruebaPropia.PruebaDelCandidato;
import com.renaser.ai.ai_engine.prueba.service.ServicioCalificacionPruebaPropia;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * En la ficha, la prueba técnica escrita en el editor (V67) de un candidato, criterio por
 * criterio, y el ajuste a mano de su parte calificada. Vive bajo la postulación, no bajo la
 * vacante, y por eso no está en {@link PruebaPropiaController}; sale en Swagger junto a él.
 *
 * <p>Abrirla pide {@code abrir_ficha_candidato} y ajustarla, {@code ajustar_nota}. El panel no
 * sabe sus permisos: si puede ajustar viaja en lo que se le devuelve.
 */
@RestController
@RequestMapping("/api/v1/panel/postulaciones/{postulacionId}/prueba-propia")
@RequiredArgsConstructor
@Tag(name = PruebaPropiaController.TAG, description = PruebaPropiaController.DESCRIPCION)
public class PruebaPropiaDelCandidatoController {

    private final ServicioCalificacionPruebaPropia calificacion;
    private final Permisos permisos;

    @GetMapping
    @PreAuthorize("@permisos.tiene('abrir_ficha_candidato')")
    @Operation(summary = "La prueba de un candidato, criterio por criterio: de dónde sale cada "
            + "nota, sus cerradas, sus abiertas y sus entregables, y la nota de la etapa o qué "
            + "falta para tenerla. El contenido de los entregables pide «descargar_entregables»")
    public PruebaDelCandidato prueba(@PathVariable Long postulacionId) {
        return calificacion.prueba(permisos.actual(), postulacionId);
    }

    @PutMapping("/criterios/{criterioId}/nota")
    @PreAuthorize("@permisos.tiene('ajustar_nota')")
    @Operation(summary = "Ajustar (o poner) la parte calificada de un criterio, de 0 a sus puntos "
            + "y con motivo. La parte automática no se ajusta. Recalcula sin IA")
    public PruebaDelCandidato ajustar(@PathVariable Long postulacionId, @PathVariable Long criterioId,
                                      @Valid @RequestBody AjustarCriterio datos) {
        return calificacion.ajustar(permisos.actual(), postulacionId, criterioId, datos);
    }
}
