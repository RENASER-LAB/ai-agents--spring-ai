package com.renaser.ai.ai_engine.pesos.controller;

import com.renaser.ai.ai_engine.pesos.service.ServicioPesos;

import com.renaser.ai.ai_engine.pesos.dto.DtosPesos.*;
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

// Solo Dirección: cambiar pesos y publicarlos es una decisión suya (docs/04).
@RestController
@RequestMapping("/api/v1/panel/pesos")
@RequiredArgsConstructor
@Tag(name = "Panel · Pesos", description = "Cómo se reparte la nota: por etapa, por componente del Perfil Integral, por dimensión y por criterio")
public class PesosController {

    private final ServicioPesos servicio;
    private final Permisos permisos;

    @GetMapping("/versiones")
    @PreAuthorize("@permisos.tiene('publicar_version_pesos')")
    public List<VersionPesosResponse> versiones() {
        return servicio.listar(permisos.actual());
    }

    @PostMapping("/versiones")
    @PreAuthorize("@permisos.tiene('publicar_version_pesos')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear una versión de pesos, en borrador")
    public Map<String, Long> crearVersion(@Valid @RequestBody CrearVersionPesos datos) {
        return Map.of("id", servicio.crearVersion(permisos.actual(), datos));
    }

    @PostMapping("/versiones/{id}/publicacion")
    @PreAuthorize("@permisos.tiene('publicar_version_pesos')")
    @Operation(summary = "Publicar: valida que cada grupo sume lo que debe sumar y ya no se puede editar")
    public void publicar(@PathVariable Long id) {
        servicio.publicarVersion(permisos.actual(), id);
    }

    @GetMapping("/versiones/{id}/etapas")
    @PreAuthorize("@permisos.tiene('publicar_version_pesos')")
    public List<PesoEtapaResponse> pesosEtapa(@PathVariable Long id) {
        return servicio.listarPesosEtapa(permisos.actual(), id);
    }

    @PostMapping("/versiones/{id}/etapas")
    @PreAuthorize("@permisos.tiene('publicar_version_pesos')")
    @ResponseStatus(HttpStatus.CREATED)
    public void agregarPesoEtapa(@PathVariable Long id, @Valid @RequestBody CrearPesoEtapa datos) {
        servicio.agregarPesoEtapa(permisos.actual(), id, datos);
    }

    @GetMapping("/versiones/{id}/componentes")
    @PreAuthorize("@permisos.tiene('publicar_version_pesos')")
    public List<PesoComponenteResponse> pesosComponente(@PathVariable Long id) {
        return servicio.listarPesosComponente(permisos.actual(), id);
    }

    @PostMapping("/versiones/{id}/componentes")
    @PreAuthorize("@permisos.tiene('publicar_version_pesos')")
    @ResponseStatus(HttpStatus.CREATED)
    public void agregarPesoComponente(@PathVariable Long id, @Valid @RequestBody CrearPesoComponente datos) {
        servicio.agregarPesoComponente(permisos.actual(), id, datos);
    }

    @GetMapping("/versiones/{id}/dimensiones")
    @PreAuthorize("@permisos.tiene('publicar_version_pesos')")
    public List<PesoDimensionResponse> pesosDimension(@PathVariable Long id) {
        return servicio.listarPesosDimension(permisos.actual(), id);
    }

    @PostMapping("/versiones/{id}/dimensiones")
    @PreAuthorize("@permisos.tiene('publicar_version_pesos')")
    @ResponseStatus(HttpStatus.CREATED)
    public void agregarPesoDimension(@PathVariable Long id, @Valid @RequestBody CrearPesoDimension datos) {
        servicio.agregarPesoDimension(permisos.actual(), id, datos);
    }

    @GetMapping("/versiones/{id}/criterios")
    @PreAuthorize("@permisos.tiene('publicar_version_pesos')")
    public List<PesoCriterioResponse> pesosCriterio(@PathVariable Long id) {
        return servicio.listarPesosCriterio(permisos.actual(), id);
    }

    @PostMapping("/versiones/{id}/criterios")
    @PreAuthorize("@permisos.tiene('publicar_version_pesos')")
    @ResponseStatus(HttpStatus.CREATED)
    public void agregarPesoCriterio(@PathVariable Long id, @Valid @RequestBody CrearPesoCriterio datos) {
        servicio.agregarPesoCriterio(permisos.actual(), id, datos);
    }
}
