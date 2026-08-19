package com.renaser.ai.ai_engine.catalogo.controller;

import com.renaser.ai.ai_engine.catalogo.dto.DtosCatalogo.Catalogos;
import com.renaser.ai.ai_engine.catalogo.service.ServicioCatalogo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los catálogos que necesitan los formularios del panel, en una sola llamada.
 *
 * <p>Existe porque antes no había ninguno y cualquier pantalla tenía que llevar los códigos
 * escritos a mano. Eso ya falló una vez: los desplegables ofrecían familias que la base no
 * reconoce y guardar reventaba con una violación de clave foránea.
 *
 * <p>Va sin permiso concreto, solo con sesión de equipo: son datos de referencia, no de negocio.
 * Quien puede entrar al panel puede leer la lista de familias.
 */
@RestController
@RequestMapping("/api/v1/panel")
@RequiredArgsConstructor
@Tag(name = "Catálogos", description = "Los valores que admiten los formularios")
public class CatalogoController {

    private final ServicioCatalogo servicio;

    @GetMapping("/catalogos")
    @Operation(summary = "Todos los valores que admiten los formularios: niveles, familias, "
            + "etapas, urgencias, tipos y motivos de cierre, y los 18 estados")
    public Catalogos catalogos() {
        return servicio.todos();
    }
}
