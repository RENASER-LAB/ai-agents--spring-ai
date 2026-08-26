package com.renaser.ai.ai_engine.organizacion.controller;

import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.ConsumoEmpresa;
import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.CrearEmpresa;
import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.EmpresaCreada;
import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.EmpresaPanel;
import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.MotivoPlataforma;
import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.TopeIa;
import com.renaser.ai.ai_engine.organizacion.service.Instrumento;
import com.renaser.ai.ai_engine.organizacion.service.ServicioPersonalizacion;
import com.renaser.ai.ai_engine.organizacion.service.ServicioPlataforma;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final ServicioPersonalizacion personalizacion;
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
            + "parámetros, textos en borrador, correos activos, tope de IA si se pide) e "
            + "invita a su primer administrador")
    public EmpresaCreada crearEmpresa(@Valid @RequestBody CrearEmpresa datos) {
        return servicio.crearEmpresa(permisos.actual(), datos);
    }

    @GetMapping("/consumo")
    @PreAuthorize("@permisos.tiene('administrar_plataforma')")
    @Operation(summary = "El consumo de IA de un mes (YYYY-MM; en blanco, el corriente) por "
            + "empresa y por agente: total gastado y tokens. Con esto Renaser factura fuera")
    public List<ConsumoEmpresa> consumo(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String mes) {
        return servicio.consumo(permisos.actual(), mes);
    }

    // ---------- Suspensión, tope y personalización: el continente (pieza F) ----------

    @PostMapping("/empresas/{id}/suspension")
    @PreAuthorize("@permisos.tiene('administrar_plataforma')")
    @Operation(summary = "Suspender una empresa: su equipo no entra (ni con tokens vivos), sus "
            + "vacantes salen del tablón, y los candidatos que ya estaban dentro no se tocan")
    public void suspender(@PathVariable Long id, @Valid @RequestBody MotivoPlataforma datos) {
        servicio.suspender(permisos.actual(), id, datos.motivo());
    }

    @PostMapping("/empresas/{id}/reactivacion")
    @PreAuthorize("@permisos.tiene('administrar_plataforma')")
    @Operation(summary = "Reactivar una empresa suspendida: todo vuelve tal cual")
    public void reactivar(@PathVariable Long id, @Valid @RequestBody MotivoPlataforma datos) {
        servicio.reactivar(permisos.actual(), id, datos.motivo());
    }

    @PutMapping("/empresas/{id}/tope-ia")
    @PreAuthorize("@permisos.tiene('administrar_plataforma')")
    @Operation(summary = "Poner, subir o quitar (tope en blanco) el tope mensual de IA de una "
            + "empresa. Lo que quedó en espera lo despierta el sondeo de la cola solo")
    public void ponerTopeIa(@PathVariable Long id, @RequestBody TopeIa datos) {
        servicio.ponerTopeIa(permisos.actual(), id, datos.tope());
    }

    @PostMapping("/empresas/{id}/personalizacion/{instrumento}")
    @PreAuthorize("@permisos.tiene('administrar_plataforma')")
    @Operation(summary = "Encender una personalización DE OTRA empresa, cuando ella lo pide "
            + "fuera del sistema: misma copia y misma auditoría que si lo hiciera ella")
    public void encenderPersonalizacion(@PathVariable Long id, @PathVariable String instrumento,
                                        @Valid @RequestBody MotivoPlataforma datos) {
        personalizacion.encenderPara(permisos.actual(), id, instrumento(instrumento), datos.motivo());
    }

    @DeleteMapping("/empresas/{id}/personalizacion/{instrumento}")
    @PreAuthorize("@permisos.tiene('administrar_plataforma')")
    @Operation(summary = "Apagar una personalización de otra empresa: vuelve a leer el método "
            + "de la plataforma; su copia se archiva, nunca se borra")
    public void apagarPersonalizacion(@PathVariable Long id, @PathVariable String instrumento,
                                      @Valid @RequestBody MotivoPlataforma datos) {
        personalizacion.apagarPara(permisos.actual(), id, instrumento(instrumento), datos.motivo());
    }

    // La conversión a mano, como en PersonalizacionController y por lo mismo: un valor
    // desconocido debe ser un 400 que diga cuáles existen.
    private static Instrumento instrumento(String crudo) {
        try {
            return Instrumento.valueOf(crudo.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("No existe el instrumento «" + crudo
                    + "»; los válidos son " + java.util.Arrays.toString(Instrumento.values()));
        }
    }
}
