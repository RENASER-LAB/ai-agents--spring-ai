package com.renaser.ai.ai_engine.organizacion.controller;

import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.CrearEmpresa;
import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.EmpresaCreada;
import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.EmpresaPanel;
import com.renaser.ai.ai_engine.organizacion.service.ServicioPlataforma;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Renaser como dueña de la plataforma. Doble llave: el permiso administrar_plataforma
// (que la copia de roles del alta no reparte) y, en el servicio, ser de la organización
// plataforma. El permiso podría concederse a mano por error; la segunda llave no.
@RestController
@RequestMapping("/api/v1/panel/plataforma")
@RequiredArgsConstructor
@Tag(name = "Panel · Plataforma", description = "El alta de empresas, solo para la dueña de la plataforma")
public class PlataformaController {

    private final ServicioPlataforma servicio;
    private final Permisos permisos;

    @GetMapping("/empresas")
    @PreAuthorize("@permisos.tiene('administrar_plataforma')")
    @Operation(summary = "Las empresas dadas de alta en la plataforma")
    public List<EmpresaPanel> empresas() {
        return servicio.empresas(permisos.actual());
    }

    @PostMapping("/empresas")
    @PreAuthorize("@permisos.tiene('administrar_plataforma')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dar de alta una empresa: la crea con su siembra completa (roles, "
            + "parámetros, textos en borrador, correos activos) e invita a su primer administrador")
    public EmpresaCreada crearEmpresa(@Valid @RequestBody CrearEmpresa datos) {
        return servicio.crearEmpresa(permisos.actual(), datos);
    }
}
