package com.renaser.ai.ai_engine.ai.controller.perfilintegral;

import com.renaser.ai.ai_engine.ai.service.ServicioAgentesIa;

import com.renaser.ai.ai_engine.ai.dto.DtosAgentesIa.*;
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

// Vive en un subpaquete propio (ai.controller.perfilintegral), no directo en ai.controller:
// ManejadorErrores/ConfiguracionSwagger registran por basePackageClasses, que abarca el
// paquete entero. Si este controlador compartiera paquete con AgentRunController y
// hermanos, les cambiaría el manejo de errores y el candado de Swagger sin querer — el
// motor de agentes general va abierto a propósito.
//
// La ruta sí va bajo /api/v1/panel/**, con el mismo candado del resto del panel: administra
// los agentes del hito 2 de selección, permiso por permiso, a diferencia del motor general.
@RestController
@RequestMapping("/api/v1/panel/agentes-ia")
@RequiredArgsConstructor
@Tag(name = "Panel · Agentes de IA", description = "Los 9 agentes del Perfil Integral y sus instrucciones versionadas")
public class AgentesIaPanelController {

    private final ServicioAgentesIa servicio;
    private final Permisos permisos;

    @GetMapping
    @PreAuthorize("@permisos.tiene('editar_instrucciones_ia')")
    @Operation(summary = "El catálogo de los 9 agentes")
    public List<AgenteResponse> agentes() {
        return servicio.listarAgentes(permisos.actual());
    }

    @GetMapping("/instrucciones")
    @PreAuthorize("@permisos.tiene('editar_instrucciones_ia')")
    public List<InstruccionResponse> instrucciones(@RequestParam String agenteCodigo) {
        return servicio.listarInstrucciones(permisos.actual(), agenteCodigo);
    }

    @PostMapping("/instrucciones")
    @PreAuthorize("@permisos.tiene('editar_instrucciones_ia')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear una instrucción nueva, sin activar todavía")
    public Map<String, Long> crearInstruccion(@Valid @RequestBody CrearInstruccion datos) {
        return Map.of("id", servicio.crearInstruccion(permisos.actual(), datos));
    }

    @PostMapping("/instrucciones/{id}/publicacion")
    @PreAuthorize("@permisos.tiene('editar_instrucciones_ia')")
    @Operation(summary = "Activar esta instrucción: desactiva la anterior del mismo agente")
    public void publicarInstruccion(@PathVariable Long id) {
        servicio.publicarInstruccion(permisos.actual(), id);
    }
}
