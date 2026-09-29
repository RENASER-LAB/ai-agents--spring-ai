package com.renaser.ai.ai_engine.colaborador.controller;

import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.AltaColaborador;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.Anulacion;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.ContratadoPendiente;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.Creado;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.DatosPersonales;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.EntradaHistorial;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.FichaColaborador;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.Filtros;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.NoDarDeAlta;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.OpcionesColaborador;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.PaginaColaboradores;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.Precarga;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.RegistrarCambio;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.RegistrarCese;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.Reingreso;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.ResultadoCarga;
import com.renaser.ai.ai_engine.colaborador.service.ServicioCargaColaboradores;
import com.renaser.ai.ai_engine.colaborador.service.ServicioColaboradores;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * La gestión de personas (V64): la lista, la ficha, el alta, la carga por Excel, los cambios,
 * el cese y el reingreso.
 *
 * <p>Sin permiso, 403. Con un alcance que no es TODO, o una ficha de otra empresa, 404. El
 * sueldo no viaja a quien no tiene {@code ver_sueldos}.
 */
@RestController
@RequestMapping("/api/v1/panel/colaboradores")
@RequiredArgsConstructor
@Tag(name = "Panel · Colaboradores", description = "La ficha de cada persona que trabaja en la empresa")
public class ColaboradoresController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ServicioColaboradores servicio;
    private final ServicioCargaColaboradores carga;
    private final Permisos permisos;

    // ---------- La lista ----------

    @GetMapping
    @PreAuthorize("@permisos.tiene('ver_colaboradores')")
    @Operation(summary = "La lista, ordenada por apellidos, en páginas de 50. Sin estado, "
            + "Activos y Por ingresar. porVencer: el contrato vence en 30 días o ya venció")
    public PaginaColaboradores listar(@RequestParam(required = false) List<String> estado,
                                      @RequestParam(required = false) Long sede,
                                      @RequestParam(required = false) Long area,
                                      @RequestParam(required = false) Long cargo,
                                      @RequestParam(defaultValue = "false") boolean porVencer,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(defaultValue = "0") int pagina) {
        return servicio.listar(permisos.actual(), new Filtros(estado, sede, area, cargo, porVencer, q), pagina);
    }

    @GetMapping("/opciones")
    @PreAuthorize("@permisos.tiene('ver_colaboradores')")
    @Operation(summary = "Lo que ofrecen los formularios hoy: catálogos, sedes, áreas y cargos activos, "
            + "y si quien pregunta puede editar y ver sueldos")
    public OpcionesColaborador opciones() {
        return servicio.opciones(permisos.actual());
    }

    // ---------- Contratados pendientes de alta ----------

    @GetMapping("/pendientes")
    @PreAuthorize("@permisos.tiene('ver_colaboradores')")
    @Operation(summary = "Las contrataciones de la empresa que todavía no tienen ficha")
    public List<ContratadoPendiente> pendientes() {
        return servicio.pendientes(permisos.actual());
    }

    @PostMapping("/pendientes/{postulacionId}/descarte")
    @PreAuthorize("@permisos.tiene('editar_colaboradores')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "No dar de alta a un contratado: lo saca del aviso, con motivo auditado")
    public void noDarDeAlta(@PathVariable Long postulacionId, @Valid @RequestBody NoDarDeAlta datos) {
        servicio.noDarDeAlta(permisos.actual(), postulacionId, datos.motivo());
    }

    @GetMapping("/precarga")
    @PreAuthorize("@permisos.tiene('editar_colaboradores')")
    @Operation(summary = "Lo que ya se sabe de un contratado, para precargar su alta")
    public Precarga precarga(@RequestParam("postulacion") Long postulacionId) {
        return servicio.precarga(permisos.actual(), postulacionId);
    }

    // ---------- El alta y la carga ----------

    @PostMapping
    @PreAuthorize("@permisos.tiene('editar_colaboradores')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dar de alta: la ficha, su primer periodo y su situación con motivo «Ingreso». "
            + "409 con colaboradorId si el documento ya tiene ficha en la empresa")
    public Creado darDeAlta(@Valid @RequestBody AltaColaborador datos) {
        return new Creado(servicio.darDeAlta(permisos.actual(), datos));
    }

    @GetMapping("/plantilla")
    @PreAuthorize("@permisos.tiene('editar_colaboradores')")
    @Operation(summary = "La plantilla .xlsx de la carga, con los valores válidos de hoy. La columna "
            + "de sueldo solo sale con ver_sueldos")
    public ResponseEntity<byte[]> plantilla() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plantilla-colaboradores.xlsx\"")
                .contentType(MediaType.parseMediaType(XLSX))
                .body(carga.plantilla(permisos.actual()));
    }

    @PostMapping(value = "/carga", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permisos.tiene('editar_colaboradores')")
    @Operation(summary = "Cargar el Excel: todo o nada. Si hay algún error, 400 con la lista entera "
            + "(fila, columna, valor y qué pasa) y no se guarda nada")
    public ResultadoCarga cargar(@RequestParam("archivo") MultipartFile archivo) throws IOException {
        return carga.cargar(permisos.actual(), archivo.getOriginalFilename(), archivo.getBytes());
    }

    // ---------- La ficha ----------

    @GetMapping("/{id}")
    @PreAuthorize("@permisos.tiene('ver_colaboradores')")
    @Operation(summary = "La ficha: perfil, situación vigente hoy, periodo actual y cambios programados")
    public FichaColaborador ficha(@PathVariable Long id) {
        return servicio.ficha(permisos.actual(), id);
    }

    @GetMapping("/{id}/historial")
    @PreAuthorize("@permisos.tiene('ver_colaboradores')")
    @Operation(summary = "La línea de tiempo, de lo más reciente a lo más antiguo")
    public List<EntradaHistorial> historial(@PathVariable Long id) {
        return servicio.historial(permisos.actual(), id);
    }

    @PutMapping("/{id}/perfil")
    @PreAuthorize("@permisos.tiene('editar_colaboradores')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Corregir los datos personales, sin historial: queda en la auditoría")
    public void editarPerfil(@PathVariable Long id, @Valid @RequestBody DatosPersonales datos) {
        servicio.editarPerfil(permisos.actual(), id, datos);
    }

    @PostMapping("/{id}/cambios")
    @PreAuthorize("@permisos.tiene('editar_colaboradores')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un cambio de la situación laboral. Con fecha futura queda programado")
    public Creado registrarCambio(@PathVariable Long id, @Valid @RequestBody RegistrarCambio datos) {
        return new Creado(servicio.registrarCambio(permisos.actual(), id, datos));
    }

    @PostMapping("/{id}/cambios/{situacionId}/anulacion")
    @PreAuthorize("@permisos.tiene('editar_colaboradores')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Anular un cambio programado, con motivo. Uno vigente no se anula")
    public void anularCambio(@PathVariable Long id, @PathVariable Long situacionId,
                             @Valid @RequestBody Anulacion datos) {
        servicio.anularCambio(permisos.actual(), id, situacionId, datos.motivo());
    }

    @PostMapping("/{id}/cese")
    @PreAuthorize("@permisos.tiene('editar_colaboradores')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Registrar el cese. Los cambios programados después de esa fecha se anulan")
    public void registrarCese(@PathVariable Long id, @Valid @RequestBody RegistrarCese datos) {
        servicio.registrarCese(permisos.actual(), id, datos);
    }

    @PostMapping("/{id}/cese/anulacion")
    @PreAuthorize("@permisos.tiene('editar_colaboradores')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Anular el cese del periodo actual, con motivo")
    public void anularCese(@PathVariable Long id, @Valid @RequestBody Anulacion datos) {
        servicio.anularCese(permisos.actual(), id, datos.motivo());
    }

    @PostMapping("/{id}/reingreso")
    @PreAuthorize("@permisos.tiene('editar_colaboradores')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reingresar a un cesado: abre un periodo nuevo con motivo «Reingreso»")
    public void reingresar(@PathVariable Long id, @Valid @RequestBody Reingreso datos) {
        servicio.reingresar(permisos.actual(), id, datos);
    }
}
