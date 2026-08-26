package com.renaser.ai.ai_engine.organizacion.controller;

import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.Personalizacion;
import com.renaser.ai.ai_engine.organizacion.service.Instrumento;
import com.renaser.ai.ai_engine.organizacion.service.ServicioPersonalizacion;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

// Las banderas de personalización de la propia organización (pieza A): encender copia el
// instrumento de la plataforma y pasa a leer lo propio; apagar vuelve a la plataforma.
@RestController
@RequestMapping("/api/v1/panel/organizacion")
@RequiredArgsConstructor
@Tag(name = "Panel · Personalización", description = "Qué instrumentos son propios y cuáles se leen de la plataforma")
public class PersonalizacionController {

    private final ServicioPersonalizacion servicio;
    private final Permisos permisos;

    public record EncenderPersonalizacion(@NotBlank String instrumento) {}

    @GetMapping("/personalizacion")
    @PreAuthorize("@permisos.tiene('personalizar_instrumentos')")
    @Operation(summary = "Qué tiene personalizado esta organización, bandera por bandera")
    public Personalizacion ver() {
        return servicio.ver(permisos.actual());
    }

    @PostMapping("/personalizacion")
    @PreAuthorize("@permisos.tiene('personalizar_instrumentos')")
    @Operation(summary = "Encender una personalización: copia el instrumento publicado de la "
            + "plataforma y desde ahí la organización lee y edita el suyo")
    public void encender(@Valid @RequestBody EncenderPersonalizacion datos) {
        servicio.encender(permisos.actual(), instrumento(datos.instrumento()));
    }

    @DeleteMapping("/personalizacion/{instrumento}")
    @PreAuthorize("@permisos.tiene('personalizar_instrumentos')")
    @Operation(summary = "Apagar una personalización: se vuelve a leer el instrumento de la "
            + "plataforma; la copia propia se archiva, nunca se borra")
    public void apagar(@PathVariable String instrumento) {
        servicio.apagar(permisos.actual(), instrumento(instrumento));
    }

    // La conversión a mano y no con el enum en la firma: un valor desconocido debe ser un
    // 400 que diga cuáles existen, no el error genérico de conversión de Spring.
    private static Instrumento instrumento(String crudo) {
        try {
            return Instrumento.valueOf(crudo.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("No existe el instrumento «" + crudo
                    + "»; los válidos son " + Arrays.toString(Instrumento.values()));
        }
    }
}
