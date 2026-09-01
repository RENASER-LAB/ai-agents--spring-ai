package com.renaser.ai.ai_engine.postulacion.controller;

import com.renaser.ai.ai_engine.perfilintegral.dto.DtosExcelRanking.ExcelDeRanking;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosExcelRanking.PedidoExcelRanking;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioExcelRanking;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El ranking de una vacante, para llevárselo.
 *
 * <p>Vive en {@code postulacion.controller} y no junto a su servicio por lo mismo que el
 * ranking: el controlador que publica lo del Perfil Integral es el de postulaciones, y este
 * paquete es el que {@code ConfiguracionSwagger} y {@code ManejadorErrores} ya reconocen —un
 * controlador fuera de ellos publica endpoints sin candado y devuelve 500 donde debería
 * devolver 400—.
 *
 * <p>Es un POST y no un GET porque la lista de candidatos ya ordenada no cabe en una URL:
 * ochenta ids y la frase del filtro pasan de los límites que cualquier proxy corta. No
 * escribe nada; lo único que crea es el archivo.
 */
@RestController
@RequestMapping("/api/v1/panel")
@RequiredArgsConstructor
@Tag(name = "Panel · Ranking en Excel", description = "La tanda seleccionada, en dos hojas")
public class ExcelRankingController {

    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ServicioExcelRanking excel;
    private final Permisos permisos;

    @PostMapping("/vacantes/{id}/ranking/excel")
    @PreAuthorize("@permisos.tiene('ver_embudo')")
    @Operation(summary = "El ranking en un .xlsx de dos hojas —Resumen y Detalle—, con los "
            + "candidatos en el MISMO orden en que llegan los postulacionIds: filtrar y "
            + "ordenar es cosa del cliente y aquí no se vuelve a ordenar. Solo hay columnas "
            + "para PERFIL_INTEGRAL y PRUEBA_PUESTO")
    public ResponseEntity<byte[]> excel(@PathVariable Long id,
                                        @Valid @RequestBody PedidoExcelRanking pedido) {
        ExcelDeRanking libro = excel.generar(permisos.actual(), id, pedido);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + libro.nombreArchivo() + "\"")
                .contentType(MediaType.parseMediaType(XLSX))
                .body(libro.contenido());
    }
}
