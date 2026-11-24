package com.renaser.ai.ai_engine.whatsapp.controller;

import com.renaser.ai.ai_engine.whatsapp.service.Celular;
import com.renaser.ai.ai_engine.whatsapp.service.ClienteConversacion;
import com.renaser.ai.ai_engine.whatsapp.service.impl.ClienteConversacionFalso;
import com.renaser.ai.ai_engine.whatsapp.service.impl.ClienteConversacionFalso.Llamada;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * El registro del bot falso (modo {@code falso}): lo que EX le habría mandado al bot de verdad.
 *
 * <p>Solo existe con {@code app.whatsapp.modo=falso}, que ningún entorno de verdad tiene, y aun
 * así va detrás de la firma del bot como el resto de la integración. Lo usan los E2E para
 * comprobar que llegó un solo aviso, el anuncio del número o la revocación del anterior, y desde
 * la V92 las vinculaciones sin mensaje.
 *
 * <p>Y tres mandos para el QA de «Notificaciones» (V92): que el bot conteste como caído —el error
 * del CRM al activar WhatsApp—, que no conozca un número —el {@code 404 SIN_CONVERSACION} que hace
 * caer al camino con mensaje— y que tenga un número dado de baja —el {@code 409 SUPRIMIDO} de
 * una postulación nueva—. Como el bot de verdad, el código del error viaja en {@code code}.
 */
@RestController
@RequestMapping("/api/v1/integraciones/whatsapp/falso")
@ConditionalOnProperty(name = "app.whatsapp.modo", havingValue = "falso")
@Tag(name = "Integración: bot de WhatsApp", description = "Lo que el bot le pide a EX. Firmado con HMAC, sin token")
public class BotFalsoWhatsappController {

    /** {@code {"caido": true}}: el bot falso contesta como caído hasta que se le diga lo contrario. */
    public record Caido(boolean caido) {}

    /** {@code {"telefono": "+51…", "sinConversacion": true}}: el bot no conoce ese número. */
    public record SinConversacion(String telefono, boolean sinConversacion) {}

    /** {@code {"telefono": "+51…", "suprimido": true}}: el bot tiene ese número dado de baja. */
    public record Suprimido(String telefono, boolean suprimido) {}

    private final ClienteConversacion cliente;

    public BotFalsoWhatsappController(ClienteConversacion cliente) {
        this.cliente = cliente;
    }

    @GetMapping("/registro")
    @Operation(summary = "Solo en modo falso: lo que EX le mandó al bot (anuncios, avisos, "
            + "revocaciones y vinculaciones), filtrado por número si se pide")
    public List<Llamada> registro(@RequestParam(required = false) String telefono) {
        if (!(cliente instanceof ClienteConversacionFalso falso)) {
            return List.of();
        }
        // Un «+» sin codificar en la dirección llega como espacio: se normaliza como lo manda el bot.
        return telefono == null || telefono.isBlank() ? falso.registro()
                : Celular.delBot(telefono).map(falso::registroDe).orElse(List.of());
    }

    @PostMapping("/caido")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Solo en modo falso: que el bot conteste como caído (true) o que vuelva (false)")
    public void caido(@RequestBody Caido peticion) {
        if (cliente instanceof ClienteConversacionFalso falso) {
            falso.fallar(peticion.caido());
        }
    }

    @PostMapping("/sin-conversacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Solo en modo falso: que el bot no conozca ese número (404 con code SIN_CONVERSACION "
            + "en sus vinculaciones) o que vuelva a conocerlo")
    public void sinConversacion(@RequestBody SinConversacion peticion) {
        if (cliente instanceof ClienteConversacionFalso falso) {
            Celular.delBot(peticion.telefono())
                    .ifPresent(numero -> falso.sinConversacion(numero, peticion.sinConversacion()));
        }
    }

    @PostMapping("/suprimido")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Solo en modo falso: que el bot tenga ese número dado de baja (409 SUPRIMIDO "
            + "en una vinculación NUEVA_POSTULACION; ACTIVACION la levanta) o que no")
    public void suprimido(@RequestBody Suprimido peticion) {
        if (cliente instanceof ClienteConversacionFalso falso) {
            Celular.delBot(peticion.telefono())
                    .ifPresent(numero -> falso.suprimido(numero, peticion.suprimido()));
        }
    }
}
