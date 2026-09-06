package com.renaser.ai.ai_engine.portal.controller;

import com.renaser.ai.ai_engine.portal.service.ServicioCuentaPortal;
import com.renaser.ai.ai_engine.portal.service.ServicioPostulacionPortal;
import com.renaser.ai.ai_engine.portal.service.ServicioTablonPortal;
import com.renaser.ai.ai_engine.postulacion.service.ServicioEnlaceAcceso;

import com.renaser.ai.ai_engine.portal.dto.DtosPortal.*;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// La puerta del candidato. Lo público es mirar vacantes, leer los consentimientos,
// crear cuenta y entrar; todo lo demás exige su token.
@RestController
@RequestMapping("/api/v1/portal")
@RequiredArgsConstructor
@Tag(name = "Portal del candidato", description = "Lo que ve y hace quien postula")
public class PortalController {

    // Un servicio por tema, la misma puerta: el tablón público, la cuenta del candidato
    // y su postulación. Las rutas no saben del corte.
    private final ServicioCuentaPortal cuentas;
    private final ServicioTablonPortal tablon;
    private final ServicioPostulacionPortal postulaciones;
    private final ServicioEnlaceAcceso enlaces;
    private final Permisos permisos;

    // ---------- público ----------

    @GetMapping("/vacantes")
    @Operation(summary = "Las vacantes publicadas")
    public List<VacantePublica> vacantes() {
        return tablon.vacantesPublicadas();
    }

    @GetMapping("/vacantes/{id}")
    @Operation(summary = "El detalle público de una vacante, con sus requisitos indispensables")
    public VacantePublica vacante(@PathVariable Long id) {
        return tablon.vacante(id);
    }

    @GetMapping("/consentimientos/textos")
    @Operation(summary = "Los textos vigentes de los dos consentimientos de la plataforma")
    public List<TextoConsentimientoPublico> textos() {
        return cuentas.textosDeConsentimiento();
    }

    // Público como el tablón (misma regla de ConfiguracionSeguridad: GET /vacantes/**):
    // el candidato tiene que poder leer qué va a aceptar ANTES de decidir postular.
    @GetMapping("/vacantes/{id}/consentimiento")
    @Operation(summary = "El texto de tratamiento de datos de la empresa de esta vacante, "
            + "el que se acepta al postular")
    public ConsentimientoDeVacante consentimientoDeVacante(@PathVariable Long id) {
        return tablon.consentimientoDeVacante(id);
    }

    @PostMapping("/cuentas")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear la cuenta: persona, acceso y consentimientos")
    public void crearCuenta(@Valid @RequestBody CrearCuenta datos, HttpServletRequest request) {
        cuentas.crearCuenta(datos, request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Entrar con correo y contraseña; devuelve el token")
    public Sesion login(@Valid @RequestBody Login datos) {
        return cuentas.entrar(datos);
    }

    /**
     * La otra puerta: la de quien nunca eligió una contraseña.
     *
     * <p>Un candidato que llegó por una carga masiva de currículums tiene cuenta, pero con un
     * correo inventado y una clave que nadie le dijo. Este endpoint canjea el token que le
     * llegó por correo y le abre la misma sesión que el login normal.
     *
     * <p>Es público a la fuerza: el token <b>es</b> la credencial. Por eso vale poco tiempo,
     * se guarda solo su hash, y un token inválido, vencido o revocado devuelven los tres el
     * mismo 401 sin decir cuál de los tres fue.
     */
    @PostMapping("/auth/acceso")
    @Operation(summary = "Entrar con el enlace que llegó por correo, sin contraseña")
    public Sesion accesoPorEnlace(@Valid @RequestBody AccesoPorEnlace datos) {
        var sesion = enlaces.canjear(datos.token());
        // El portal tiene su propio record: así el contrato público no queda atado a un DTO
        // del paquete de seguridad, que es interno. Y aquí se le añade el nombre, que es
        // justo lo que esta puerta no tenía: quien entra por el enlace del correo nunca se
        // registró, así que el portal no lo sabía por ningún otro sitio.
        return cuentas.sesionDe(sesion.token(), sesion.usuarioId());
    }

    // ---------- con token de candidato ----------

    @GetMapping("/auth/sesion")
    @Operation(summary = "Como se llama quien tiene este token")
    public com.renaser.ai.ai_engine.portal.dto.DtosPortal.QuienSoy quienSoy() {
        return cuentas.quienSoy(permisos.actual());
    }

    @PostMapping(value = "/postulaciones", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permisos.tiene('postular_vacante')")
    @Operation(summary = "Postular: CV (PDF o Word, máx. 10 MB), enlaces, el resultado del que "
            + "te sientes orgulloso, la confirmación de los requisitos indispensables y la "
            + "aceptación del tratamiento de datos de la empresa (obligatoria). Sin adjuntar "
            + "CV se usa el del perfil; adjuntando uno vale solo para esta vacante y el "
            + "perfil no cambia. Sin ninguno de los dos, 400")
    public ResponseEntity<Map<String, String>> postular(
            @RequestParam Long vacanteId,
            // Opcional desde que el currículum puede vivir en el perfil. Quien no adjunte
            // nada usa el suyo; quien adjunte uno lo usa SOLO para esta vacante. Lo decide
            // el servicio, que es quien puede contestar un 400 explicando qué falta.
            @RequestParam(value = "cv", required = false) MultipartFile cv,
            @RequestParam String resultadoOrgulloso,
            @RequestParam(required = false) String portafolio,
            @RequestParam(required = false) String linkedin,
            @RequestParam(required = false) String github,
            @RequestParam(required = false) List<Long> requisitosConfirmados,
            // No obligatorio para Spring a propósito: si faltara aquí, el error saldría
            // del manejador genérico como un 500 opaco. Lo exige el servicio, con un 400
            // que dice qué falta. El IP y el navegador van al registro firmado, como en
            // crearCuenta.
            @RequestParam(required = false) Boolean aceptaTratamiento,
            HttpServletRequest request) {
        UUID uuid = postulaciones.postular(permisos.actual(), vacanteId, cv, resultadoOrgulloso,
                portafolio, linkedin, github, requisitosConfirmados, aceptaTratamiento,
                request.getRemoteAddr(), request.getHeader("User-Agent"));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("codigo", uuid.toString()));
    }

    @GetMapping("/postulaciones")
    @Operation(summary = "Mis postulaciones, con su estado y días sin cambio")
    public List<MiPostulacion> misPostulaciones() {
        return postulaciones.misPostulaciones(permisos.actual());
    }

    @GetMapping("/postulaciones/{uuid}")
    @Operation(summary = "El detalle de una postulación mía, con su historial")
    public MiPostulacionDetalle miPostulacion(@PathVariable UUID uuid) {
        return postulaciones.miPostulacion(permisos.actual(), uuid);
    }

    @PostMapping("/postulaciones/{uuid}/retiro")
    @PreAuthorize("@permisos.tiene('retirar_postulacion')")
    @Operation(summary = "Retirar mi postulación. No borra mis datos: eso se pide aparte")
    public void retirar(@PathVariable UUID uuid) {
        postulaciones.retirar(permisos.actual(), uuid);
    }

    @PostMapping("/consentimientos/futuros/retiro")
    @PreAuthorize("@permisos.tiene('retirar_consentimiento_futuros')")
    @Operation(summary = "Retirar el consentimiento de futuros contactos")
    public void retirarFuturos() {
        cuentas.retirarConsentimientoFuturos(permisos.actual());
    }

    @PostMapping("/solicitudes-borrado")
    @PreAuthorize("@permisos.tiene('pedir_borrado_datos')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Pedir el borrado de mis datos. Lo ejecuta Dirección o Administración")
    public void pedirBorrado(@RequestBody(required = false) PedirBorrado datos) {
        cuentas.pedirBorrado(permisos.actual(), datos == null ? null : datos.motivo());
    }
}
