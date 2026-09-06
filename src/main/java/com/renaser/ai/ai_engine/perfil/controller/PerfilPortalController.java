package com.renaser.ai.ai_engine.perfil.controller;

import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EditarCabecera;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.ElegirPortada;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EditarCertificacion;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EditarEducacion;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EditarEnlace;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EditarExperiencia;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.EditarIdioma;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.PerfilCompleto;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.Reordenar;
import com.renaser.ai.ai_engine.perfil.service.ServicioArchivosDelPerfil;
import com.renaser.ai.ai_engine.perfil.service.ServicioPerfilPortal;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

/**
 * El perfil del candidato, desde el portal.
 *
 * <p>No lleva {@code @PreAuthorize}: lo que decide aquí no es un permiso sino de quién es el
 * perfil, y eso lo comprueba el servicio con {@code quien.personaId()}. Un elemento que no
 * es tuyo responde 404 — decir 403 ya confirmaría que existe.
 */
@RestController
@RequestMapping("/api/v1/portal/perfil")
@RequiredArgsConstructor
@Tag(name = "Portal · Perfil", description = "El perfil del candidato: suyo, editable y "
        + "el mismo en todas las convocatorias")
public class PerfilPortalController {

    private final ServicioPerfilPortal servicio;
    private final ServicioArchivosDelPerfil archivos;
    private final Permisos permisos;

    @GetMapping
    @Operation(summary = "Mi perfil completo. Vacío responde 200 con todo vacío, nunca 404")
    public PerfilCompleto ver() {
        return servicio.ver(permisos.actual());
    }

    @PutMapping
    @Operation(summary = "Editar la cabecera: titular, resumen, habilidades, experienciaMeses "
            + "(0-720), ubicación, disponibilidad y pretensión (un rango completo con moneda, "
            + "o nada). REEMPLAZA la cabecera entera: lo que no mandes se guarda vacío")
    public void editarCabecera(@Valid @RequestBody EditarCabecera datos) {
        servicio.editarCabecera(permisos.actual(), datos);
    }

    @GetMapping("/descarga")
    @Operation(summary = "Descargar mis datos en un archivo legible (derecho de acceso, "
            + "ley 29733)")
    public ResponseEntity<PerfilCompleto> descargar() {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"mi-perfil.json\"")
                .body(servicio.descargar(permisos.actual()));
    }

    // ---------- experiencia ----------

    @PostMapping("/experiencia")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Añadir un empleo")
    public Map<String, Long> crearExperiencia(@Valid @RequestBody EditarExperiencia datos) {
        return Map.of("id", servicio.crearExperiencia(permisos.actual(), datos));
    }

    @PutMapping("/experiencia/{id}")
    @Operation(summary = "Editar un empleo. Editarlo lo convierte en «escrito por mí»")
    public void editarExperiencia(@PathVariable Long id,
                                  @Valid @RequestBody EditarExperiencia datos) {
        servicio.editarExperiencia(permisos.actual(), id, datos);
    }

    @DeleteMapping("/experiencia/{id}")
    @Operation(summary = "Borrar un empleo")
    public void borrarExperiencia(@PathVariable Long id) {
        servicio.borrarExperiencia(permisos.actual(), id);
    }

    @PostMapping("/experiencia/{id}/confirmacion")
    @Operation(summary = "Confirmar un empleo que sacó el sistema, sin cambiarlo")
    public void confirmarExperiencia(@PathVariable Long id) {
        servicio.confirmarExperiencia(permisos.actual(), id);
    }

    @PutMapping("/experiencia/orden")
    @Operation(summary = "Reordenar los empleos: todos los ids, una vez cada uno")
    public void reordenarExperiencia(@Valid @RequestBody Reordenar datos) {
        servicio.reordenarExperiencia(permisos.actual(), datos.ids());
    }

    // ---------- educacion ----------

    @PostMapping("/educacion")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Añadir un título o estudio")
    public Map<String, Long> crearEducacion(@Valid @RequestBody EditarEducacion datos) {
        return Map.of("id", servicio.crearEducacion(permisos.actual(), datos));
    }

    @PutMapping("/educacion/{id}")
    @Operation(summary = "Editar un título o estudio")
    public void editarEducacion(@PathVariable Long id,
                                @Valid @RequestBody EditarEducacion datos) {
        servicio.editarEducacion(permisos.actual(), id, datos);
    }

    @DeleteMapping("/educacion/{id}")
    @Operation(summary = "Borrar un título o estudio")
    public void borrarEducacion(@PathVariable Long id) {
        servicio.borrarEducacion(permisos.actual(), id);
    }

    @PostMapping("/educacion/{id}/confirmacion")
    @Operation(summary = "Confirmar un estudio que sacó el sistema, sin cambiarlo")
    public void confirmarEducacion(@PathVariable Long id) {
        servicio.confirmarEducacion(permisos.actual(), id);
    }

    @PutMapping("/educacion/orden")
    @Operation(summary = "Reordenar los estudios")
    public void reordenarEducacion(@Valid @RequestBody Reordenar datos) {
        servicio.reordenarEducacion(permisos.actual(), datos.ids());
    }

    // ---------- idiomas ----------

    @PostMapping("/idiomas")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Añadir un idioma con su nivel (A1..C2 o NATIVO)")
    public Map<String, Long> crearIdioma(@Valid @RequestBody EditarIdioma datos) {
        return Map.of("id", servicio.crearIdioma(permisos.actual(), datos));
    }

    @PutMapping("/idiomas/{id}")
    @Operation(summary = "Editar un idioma")
    public void editarIdioma(@PathVariable Long id, @Valid @RequestBody EditarIdioma datos) {
        servicio.editarIdioma(permisos.actual(), id, datos);
    }

    @DeleteMapping("/idiomas/{id}")
    @Operation(summary = "Borrar un idioma")
    public void borrarIdioma(@PathVariable Long id) {
        servicio.borrarIdioma(permisos.actual(), id);
    }

    @PostMapping("/idiomas/{id}/confirmacion")
    @Operation(summary = "Confirmar un idioma que sacó el sistema")
    public void confirmarIdioma(@PathVariable Long id) {
        servicio.confirmarIdioma(permisos.actual(), id);
    }

    // ---------- certificaciones ----------

    @PostMapping("/certificaciones")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Añadir una certificación; el vencimiento es opcional")
    public Map<String, Long> crearCertificacion(
            @Valid @RequestBody EditarCertificacion datos) {
        return Map.of("id", servicio.crearCertificacion(permisos.actual(), datos));
    }

    @PutMapping("/certificaciones/{id}")
    @Operation(summary = "Editar una certificación")
    public void editarCertificacion(@PathVariable Long id,
                                    @Valid @RequestBody EditarCertificacion datos) {
        servicio.editarCertificacion(permisos.actual(), id, datos);
    }

    @DeleteMapping("/certificaciones/{id}")
    @Operation(summary = "Borrar una certificación")
    public void borrarCertificacion(@PathVariable Long id) {
        servicio.borrarCertificacion(permisos.actual(), id);
    }

    @PostMapping("/certificaciones/{id}/confirmacion")
    @Operation(summary = "Confirmar una certificación que sacó el sistema")
    public void confirmarCertificacion(@PathVariable Long id) {
        servicio.confirmarCertificacion(permisos.actual(), id);
    }

    // ---------- enlaces ----------

    @PostMapping("/enlaces")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Añadir un enlace. El de LinkedIn tiene que ser de linkedin.com y "
            + "el de GitHub de github.com; uno repetido responde 409")
    public Map<String, Long> crearEnlace(@Valid @RequestBody EditarEnlace datos) {
        return Map.of("id", servicio.crearEnlace(permisos.actual(), datos));
    }

    @DeleteMapping("/enlaces/{id}")
    @Operation(summary = "Borrar un enlace")
    public void borrarEnlace(@PathVariable Long id) {
        servicio.borrarEnlace(permisos.actual(), id);
    }
    // ---------- foto, portada, currículum y diplomas ----------

    /*
     * ⚠️ Los cuatro se SIRVEN, no se firman.
     *
     * Un `<img src>` no manda cabecera Authorization, así que un enlace firmado no vale para
     * pintar una foto; y en local el almacén es el de memoria, cuya url `memoria://` no la
     * abre ningún navegador. Sirviendo los bytes por una ruta con token, la pantalla se
     * comporta igual en local y en producción, y el navegador los pinta desde un blob.
     *
     * ⚠️ Y NINGUNO llega al panel del equipo. La foto no la ve quien decide (RF-41): ver
     * `PintorDePerfil.sinLoDelCandidato`.
     */

    @PostMapping(value = "/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Subir mi foto de perfil. JPG, PNG o WebP, hasta 2 MB. Sustituye a "
            + "la anterior. SOLO la veo yo: no viaja al panel ni a la IA")
    public void subirFoto(@RequestParam("archivo") MultipartFile archivo) {
        archivos.guardarFoto(permisos.actual(), archivo);
    }

    @DeleteMapping("/foto")
    @Operation(summary = "Quitar mi foto")
    public void quitarFoto() {
        archivos.quitarFoto(permisos.actual());
    }

    @GetMapping("/foto")
    @Operation(summary = "Mi foto, en bytes. 404 si no tengo")
    public ResponseEntity<byte[]> foto() {
        return servir(archivos.foto(permisos.actual()));
    }

    @PostMapping(value = "/portada", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Subir mi propia portada. Deja de valer la del catálogo si la tenía: "
            + "son excluyentes")
    public void subirPortada(@RequestParam("archivo") MultipartFile archivo) {
        archivos.guardarPortada(permisos.actual(), archivo);
    }

    @PutMapping("/portada/galeria")
    @Operation(summary = "Elegir una portada del catálogo del portal (CANTO_MENTA, "
            + "CANTO_AQUA, CANTO_ROSA, CANTO_VIOLETA o BRUMA). Suelta la propia si la había")
    public void elegirPortada(@Valid @RequestBody ElegirPortada datos) {
        archivos.elegirPortadaDeGaleria(permisos.actual(), datos.codigo());
    }

    @DeleteMapping("/portada")
    @Operation(summary = "Quedarme sin portada")
    public void quitarPortada() {
        archivos.quitarPortada(permisos.actual());
    }

    @GetMapping("/portada")
    @Operation(summary = "Mi portada propia, en bytes. 404 si tengo una del catálogo o ninguna")
    public ResponseEntity<byte[]> portada() {
        return servir(archivos.portada(permisos.actual()));
    }

    @PostMapping(value = "/cv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Subir mi currículum al perfil y arrancar su lectura. PDF o Word, "
            + "hasta 10 MB. Sustituye al anterior (RF-162) y se reutiliza al postular; si es "
            + "un archivo que ya se leyó, no se vuelve a pagar la lectura (RF-161)")
    public void subirCurriculum(@RequestParam("archivo") MultipartFile archivo) {
        archivos.guardarCurriculum(permisos.actual(), archivo);
    }

    @DeleteMapping("/cv")
    @Operation(summary = "Quitar mi currículum del perfil. Lo que ya se propuso al perfil se "
            + "queda: es mío y ya lo revisé")
    public void quitarCurriculum() {
        archivos.quitarCurriculum(permisos.actual());
    }

    @GetMapping("/cv")
    @Operation(summary = "Descargar mi currículum")
    public ResponseEntity<byte[]> curriculum() {
        return descargar(archivos.curriculum(permisos.actual()));
    }

    @PostMapping(value = "/certificaciones/{id}/archivo",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Adjuntar el diploma de una certificación. PDF o imagen")
    public void subirDiploma(@PathVariable Long id,
                             @RequestParam("archivo") MultipartFile archivo) {
        archivos.guardarDiploma(permisos.actual(), id, archivo);
    }

    @DeleteMapping("/certificaciones/{id}/archivo")
    @Operation(summary = "Quitar el diploma")
    public void quitarDiploma(@PathVariable Long id) {
        archivos.quitarDiploma(permisos.actual(), id);
    }

    @GetMapping("/certificaciones/{id}/archivo")
    @Operation(summary = "Ver el diploma de una certificación mía")
    public ResponseEntity<byte[]> diploma(@PathVariable Long id) {
        return servir(archivos.diploma(permisos.actual(), id));
    }

    /** Para pintar en la pantalla: se enseña, no se descarga. */
    private static ResponseEntity<byte[]> servir(ServicioArchivosDelPerfil.Contenido c) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        c.tipo() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : c.tipo()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + c.nombre() + "\"")
                .body(c.bytes());
    }

    /** Para guardarlo en el disco de quien lo pide. */
    private static ResponseEntity<byte[]> descargar(ServicioArchivosDelPerfil.Contenido c) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + c.nombre() + "\"")
                .body(c.bytes());
    }
}
