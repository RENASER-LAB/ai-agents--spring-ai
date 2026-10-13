package com.renaser.ai.ai_engine.whatsapp.controller;

import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.Baja;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.Novedades;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.PeticionBaja;
import com.renaser.ai.ai_engine.whatsapp.dto.DtosWhatsapp.PeticionVinculo;
import com.renaser.ai.ai_engine.whatsapp.service.ServicioIntegracionWhatsapp;
import com.renaser.ai.ai_engine.whatsapp.service.ServicioIntegracionWhatsapp.ResultadoVinculo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * La puerta del bot de WhatsApp (V73): lo que el servicio de conversación del CRM le pide a EX.
 *
 * <p>No lleva token: lleva la firma HMAC del bot ({@code X-Renaser-Marca} y
 * {@code X-Renaser-Firma}), que comprueba su propia cadena de seguridad antes de llegar aquí. Sin
 * firma, con firma mala o con una marca de hace más de cinco minutos, 401 y no se hace nada.
 * Ver docs/CONTRATO-NOVEDADES-POR-WHATSAPP.md.
 */
@RestController
@RequestMapping("/api/v1/integraciones/whatsapp")
@RequiredArgsConstructor
@Tag(name = "Integración: bot de WhatsApp",
        description = "Lo que el bot le pide a EX. Firmado con HMAC, sin token")
public class IntegracionWhatsappController {

    private final ServicioIntegracionWhatsapp integracion;

    @PostMapping("/vinculos")
    @Operation(summary = "Un número anunciado escribió: vincula sus postulaciones anunciadas y "
            + "devuelve sus novedades. 404 SIN_ANUNCIO, 409 PROCESO_TERMINADO o 409 BETA_APAGADA, "
            + "con el texto que el bot debe contestar")
    public ResponseEntity<Object> vincular(@Valid @RequestBody PeticionVinculo peticion) {
        ResultadoVinculo resultado = integracion.vincular(peticion);
        Object cuerpo = resultado.vinculo() != null ? resultado.vinculo() : resultado.rechazo();
        return ResponseEntity.status(resultado.estadoHttp()).body(cuerpo);
    }

    @GetMapping("/novedades")
    @Operation(summary = "Las novedades de cada proceso activo vinculado a ese número (la lista "
            + "puede ir vacía)")
    public Novedades novedades(@RequestParam String telefono) {
        return integracion.novedades(telefono);
    }

    @PostMapping("/bajas")
    @Operation(summary = "El número escribió BAJA: desvincula todas sus postulaciones y queda "
            + "suprimido")
    public Baja baja(@Valid @RequestBody PeticionBaja peticion) {
        return integracion.baja(peticion.telefono());
    }
}
