package com.renaser.ai.ai_engine.postulacion.controller;

import com.renaser.ai.ai_engine.postulacion.service.ServicioPostulacionesPanel;

import com.renaser.ai.ai_engine.postulacion.dto.DtosPostulacion.*;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.CalificacionEncoladaResponse;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.PerfilIntegralResponse;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.PasadaEncolada;
import com.renaser.ai.ai_engine.perfilintegral.dto.DtosPerfilIntegral.RankingVacante;
import com.renaser.ai.ai_engine.perfilintegral.service.ServicioPerfilIntegralPanel;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/panel")
@RequiredArgsConstructor
@Tag(name = "Panel · Postulaciones", description = "La bandeja, la ficha y los movimientos")
public class PostulacionesPanelController {

    private final ServicioPostulacionesPanel servicio;
    private final ServicioPerfilIntegralPanel perfilIntegral;
    private final Permisos permisos;

    @GetMapping("/bandeja")
    @PreAuthorize("@permisos.tiene('ver_candidatos')")
    @Operation(summary = "La bandeja de trabajo: todo lo que espera a alguien "
            + "(espera_a = CANDIDATO, SISTEMA, TALENTO o AREA)")
    public List<FilaBandeja> bandeja(@RequestParam("espera_a") String esperaA) {
        return servicio.bandeja(permisos.actual(), esperaA);
    }

    @GetMapping("/vacantes/{id}/embudo")
    @PreAuthorize("@permisos.tiene('ver_embudo')")
    @Operation(summary = "Cuántas postulaciones hay en cada estado de una vacante")
    public ConteoEmbudo embudo(@PathVariable Long id) {
        return servicio.embudo(permisos.actual(), id);
    }

    @GetMapping("/vacantes/{id}/ranking")
    @PreAuthorize("@permisos.tiene('ver_embudo')")
    @Operation(summary = "La tanda entera ordenada de más apto a menos: grupo de prioridad, "
            + "nota, y las ocho notas del currículum de cada uno. Incluye a quien todavía no "
            + "tiene nota, porque un candidato sin calificar no puede desaparecer de la lista")
    public RankingVacante ranking(@PathVariable Long id) {
        return perfilIntegral.ranking(permisos.actual(), id);
    }

    @PostMapping("/vacantes/{id}/criba-rapida")
    @PreAuthorize("@permisos.tiene('ajustar_nota')")
    @Operation(summary = "Primera pasada sobre la tanda entera: el modelo contesta sin razonar "
            + "y en paralelo. Diez currículums tardan alrededor de medio minuto. Sirve para "
            + "ordenar, no para decidir")
    public PasadaEncolada cribaRapida(@PathVariable Long id) {
        return perfilIntegral.cribaRapida(permisos.actual(), id);
    }

    @PostMapping("/vacantes/{id}/criba-fina")
    @PreAuthorize("@permisos.tiene('ajustar_nota')")
    @Operation(summary = "Segunda pasada, solo sobre la parte alta de la tanda: el modelo que "
            + "razona vuelve a calificarlos y pisa las notas provisionales. Cuánta parte, lo "
            + "dice el parámetro «porcentaje_criba_fina»")
    public PasadaEncolada cribaFina(@PathVariable Long id) {
        return perfilIntegral.cribaFina(permisos.actual(), id);
    }

    @GetMapping("/postulaciones/{id}")
    @PreAuthorize("@permisos.tiene('abrir_ficha_candidato')")
    @Operation(summary = "La ficha completa de una postulación")
    public FichaPostulacion ficha(@PathVariable Long id) {
        return servicio.ficha(permisos.actual(), id);
    }

    // Los dos verbos del Perfil Integral viven en este controlador y no en uno nuevo a
    // propósito: la ruta es de postulaciones y este ya está declarado en ManejadorErrores
    // y ConfiguracionSwagger. Un controlador nuevo obligaría a tocar las dos.
    @GetMapping("/postulaciones/{id}/perfil-integral")
    @PreAuthorize("@permisos.tiene('ver_perfil_integral')")
    @Operation(summary = "El retrato que armó la IA: notas del currículum, hallazgos y alertas. "
            + "Si todavía no ha corrido, «estadoCalificacion» dice en qué punto está")
    public PerfilIntegralResponse perfilIntegral(@PathVariable Long id) {
        return perfilIntegral.ver(permisos.actual(), id);
    }

    @PostMapping(value = "/postulaciones/{id}/cv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permisos.tiene('ajustar_nota')")
    @Operation(summary = "Reemplazar el currículum de una postulación. Borra el texto ya "
            + "extraído: se vuelve a leer en la próxima calificación")
    public void reemplazarCv(@PathVariable Long id, @RequestParam("cv") MultipartFile cv) {
        perfilIntegral.reemplazarCv(permisos.actual(), id, cv);
    }

    @PostMapping("/postulaciones/{id}/calificacion-perfil-integral")
    @PreAuthorize("@permisos.tiene('ajustar_nota')")
    @Operation(summary = "Pedir que la IA califique, o que vuelva a calificar. Encola y "
            + "responde al momento: la llamada al modelo tarda decenas de segundos")
    public CalificacionEncoladaResponse calificarPerfilIntegral(@PathVariable Long id) {
        return perfilIntegral.recalificar(permisos.actual(), id);
    }

    @PostMapping("/postulaciones/{id}/criba-cv")
    @PreAuthorize("@permisos.tiene('ajustar_nota')")
    @Operation(summary = "Criba: que la IA lea solo el currículum y arme el retrato con eso. "
            + "Es lo que se pide con una tanda recién llegada, antes de que nadie responda "
            + "la evaluación")
    public CalificacionEncoladaResponse cribarCv(@PathVariable Long id) {
        return perfilIntegral.cribarCv(permisos.actual(), id);
    }

    @GetMapping("/postulaciones/{id}/historial")
    @PreAuthorize("@permisos.tiene('abrir_ficha_candidato')")
    @Operation(summary = "El recorrido completo: cada transición, quién y por qué")
    public List<PasoHistorial> historial(@PathVariable Long id) {
        return servicio.historial(permisos.actual(), id);
    }

    @PostMapping("/postulaciones/{id}/transiciones")
    @PreAuthorize("@permisos.tiene('mover_postulacion')")
    @Operation(summary = "Mover a cualquier estado, con motivo OBLIGATORIO. "
            + "Si el destino es un cierre, motivoCierre dice de qué clase")
    public void transicionar(@PathVariable Long id, @Valid @RequestBody Transicionar datos) {
        servicio.transicionar(permisos.actual(), id, datos);
    }

    @PostMapping("/postulaciones/{id}/confirmacion-avance")
    @PreAuthorize("@permisos.tiene('confirmar_avance')")
    @Operation(summary = "Confirmar que avanza: aplica el estado siguiente que calcula la máquina")
    public void confirmarAvance(@PathVariable Long id, @Valid @RequestBody ConfirmarAvance datos) {
        servicio.confirmarAvance(permisos.actual(), id, datos.motivo());
    }

    @GetMapping("/archivos/{id}/enlace")
    @PreAuthorize("@permisos.tiene('descargar_entregables')")
    @Operation(summary = "Un enlace temporal para bajarse el archivo del almacen "
            + "directamente, sin que pase por aqui. Es lo que hay que usar cuando los "
            + "archivos viven en un bucket; con el almacen de disco no hay enlace y toca "
            + "/descarga")
    public EnlaceArchivo enlace(@PathVariable Long id) {
        return servicio.enlaceDeArchivo(permisos.actual(), id);
    }

    @GetMapping("/archivos/{id}/descarga")
    @PreAuthorize("@permisos.tiene('descargar_entregables')")
    @Operation(summary = "Descargar un archivo (el CV) por su id. El contenido pasa por el "
            + "backend; con los archivos en un bucket es mejor /enlace")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id) {
        StringBuilder nombre = new StringBuilder();
        byte[] contenido = servicio.descargarArchivo(permisos.actual(), id, nombre);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(contenido);
    }
}
