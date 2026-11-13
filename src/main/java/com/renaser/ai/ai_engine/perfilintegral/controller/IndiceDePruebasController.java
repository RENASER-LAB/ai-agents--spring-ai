package com.renaser.ai.ai_engine.perfilintegral.controller;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosIndiceDePruebas.FilaDePrueba;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosIndiceDePruebas.VacanteDestino;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPreguntasVacante.VersionDePreguntas;
import com.renaser.ai.ai_engine.perfilintegral.service.EditorDeLaVacante;
import com.renaser.ai.ai_engine.perfilintegral.service.FiltrosDelIndice;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioPreguntasVacante;
import com.renaser.ai.ai_engine.prueba.service.ServicioPruebaPropia;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * «Pruebas»: el índice de las preguntas y pruebas que la empresa escribió en el editor de sus
 * vacantes (spec pruebas-indice-de-la-empresa). Es de solo lectura: se busca, se mira y se
 * copia con el {@code POST …/copia} de siempre; para editar, se entra en la vacante.
 *
 * <p>{@code tipo} elige el editor: {@code PERFIL_INTEGRAL} (las preguntas propias) o
 * {@code PRUEBA_PUESTO} (la prueba del puesto). Todo va cortado por la empresa y por el alcance
 * en el servidor; lo ajeno responde 404.
 */
@RestController
@RequestMapping("/api/v1/panel/pruebas")
@Tag(name = IndiceDePruebasController.TAG, description = IndiceDePruebasController.DESCRIPCION)
public class IndiceDePruebasController {

    static final String TAG = "Panel · Pruebas de la empresa";
    static final String DESCRIPCION = "El índice de lo escrito en el editor de cada vacante: "
            + "las preguntas del Perfil Integral y la prueba del puesto";

    static final String PERFIL_INTEGRAL = "PERFIL_INTEGRAL";
    static final String PRUEBA_PUESTO = "PRUEBA_PUESTO";

    private final ServicioPreguntasVacante preguntas;
    private final ServicioPruebaPropia prueba;
    private final Permisos permisos;

    public IndiceDePruebasController(ServicioPreguntasVacante preguntas, ServicioPruebaPropia prueba,
                                     Permisos permisos) {
        this.preguntas = preguntas;
        this.prueba = prueba;
        this.permisos = permisos;
    }

    @GetMapping
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    @Operation(summary = "Las vacantes a tu alcance con su versión escrita en el editor, publicada "
            + "o en borrador: las publicadas primero, la más reciente arriba. Los filtros admiten "
            + "varios valores separados por coma: nivel (EJECUCION…), estado (BORRADOR, PUBLICADA, "
            + "EN_USO) y situacion (SIN_PUBLICAR, PUBLICADA, CERRADA, ARCHIVADA)")
    public List<FilaDePrueba> listar(@RequestParam String tipo,
                                     @RequestParam(required = false) String buscar,
                                     @RequestParam(required = false) List<String> nivel,
                                     @RequestParam(required = false) List<String> estado,
                                     @RequestParam(required = false) List<String> situacion) {
        return editorDe(tipo).indice(permisos.actual(),
                FiltrosDelIndice.de(buscar, nivel, estado, situacion));
    }

    @GetMapping("/destinos")
    @PreAuthorize("@permisos.tiene('editar_vacante')")
    @Operation(summary = "Las vacantes que pueden recibir una copia: editables, a tu alcance y que "
            + "usan el editor para esto. Las que ya rinde alguien salen con puedeRecibir=false y su "
            + "motivo. excluir (opcional) es la vacante de origen")
    public List<VacanteDestino> destinos(@RequestParam String tipo,
                                         @RequestParam(required = false) Long excluir) {
        return editorDe(tipo).destinos(permisos.actual(), excluir);
    }

    @GetMapping("/{vacanteId}")
    @PreAuthorize("@permisos.tiene('ver_vacantes')")
    @Operation(summary = "La vista previa en solo lectura: la versión publicada o, si no hay, el "
            + "borrador, sin sus avisos. La de otra empresa, fuera de tu alcance o eliminada es 404")
    public VersionDePreguntas vistaPrevia(@PathVariable Long vacanteId, @RequestParam String tipo) {
        return editorDe(tipo).vistaPreviaDelIndice(permisos.actual(), vacanteId);
    }

    private EditorDeLaVacante editorDe(String tipo) {
        return switch (tipo.strip().toUpperCase(Locale.ROOT)) {
            case PERFIL_INTEGRAL -> preguntas;
            case PRUEBA_PUESTO -> prueba;
            default -> throw new IllegalArgumentException(
                    "El tipo es " + PERFIL_INTEGRAL + " o " + PRUEBA_PUESTO);
        };
    }
}
